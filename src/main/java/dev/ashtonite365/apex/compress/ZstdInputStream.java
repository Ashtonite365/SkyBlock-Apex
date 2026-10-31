// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compress;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public final class ZstdInputStream extends InputStream {
	private static final int DIRECT_LIMIT = 64 << 20;

	private final InputStream in;
	private final long maxWindow;
	private final ZstdBlocks blocks = new ZstdBlocks();
	private final byte[] block = new byte[ZstdBlocks.BLOCK_MAX + ZstdBlocks.SLACK];
	private final byte[] head = new byte[ZstdFrame.MAX_HEADER + 4];
	private final XxHash64 hash = new XxHash64();

	private byte[] buf = new byte[0];
	private int bufEnd;
	private int histStart, readPos, writePos;
	private ZstdFrame frame;
	private long frameProduced;
	private boolean lastBlockDone, eof, sawFrame, closed;

	public ZstdInputStream(InputStream in) {
		this(in, Zstd.DEFAULT_MAX_WINDOW);
	}

	public ZstdInputStream(InputStream in, long maxWindow) {
		this.in = in;
		this.maxWindow = maxWindow;
	}

	@Override
	public int read() throws IOException {
		byte[] one = new byte[1];
		int n = read(one, 0, 1);
		return n <= 0 ? -1 : one[0] & 0xFF;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		java.util.Objects.checkFromIndexSize(off, len, b.length);
		if (closed) throw new IOException("stream closed");
		if (len == 0) return 0;
		while (readPos == writePos) {
			if (!advance()) return -1;
		}
		int n = Math.min(len, writePos - readPos);
		System.arraycopy(buf, readPos, b, off, n);
		readPos += n;
		return n;
	}

	@Override
	public int available() {
		return writePos - readPos;
	}

	byte[] readNBytesLimited(int max) throws IOException {
		byte[] out = new byte[1 << 16];
		int n = 0;
		while (true) {
			if (n == out.length) {
				if (out.length >= max) throw new CompressFormatException("zstd: output over the limit");
				out = Arrays.copyOf(out, (int) Math.min((long) out.length * 2, max));
			}
			int r = read(out, n, out.length - n);
			if (r < 0) return Arrays.copyOf(out, n);
			n += r;
		}
	}

	@Override
	public void close() throws IOException {
		if (!closed) {
			closed = true;
			buf = null;
			in.close();
		}
	}

	private boolean advance() throws IOException {
		if (eof) return false;
		if (frame == null) {
			if (!startFrame()) { eof = true; return false; }
			return true;
		}
		if (lastBlockDone) {
			finishFrame();
			return true;
		}
		nextBlock();
		return true;
	}

	private int readFully(byte[] b, int off, int len) throws IOException {
		int n = 0;
		while (n < len) {
			int r = in.read(b, off + n, len - n);
			if (r < 0) break;
			n += r;
		}
		return n;
	}

	private void exactly(byte[] b, int off, int len) throws IOException {
		if (readFully(b, off, len) != len) throw new CompressFormatException("zstd: truncated input");
	}

	private boolean startFrame() throws IOException {
		while (true) {
			int got = readFully(head, 0, 4);
			if (got == 0) {
				if (!sawFrame) throw new CompressFormatException("zstd: no frame");
				return false;
			}
			if (got < 4) throw new CompressFormatException("zstd: truncated frame");
			int m = Zstd.intLE(head, 0);
			if ((m & ZstdFrame.SKIPPABLE_MASK) == ZstdFrame.SKIPPABLE) {
				exactly(head, 0, 4);
				long skip = Zstd.intLE(head, 0) & 0xFFFFFFFFL;
				while (skip > 0) {
					int n = readFully(block, 0, (int) Math.min(skip, block.length));
					if (n == 0) throw new CompressFormatException("zstd: truncated skippable frame");
					skip -= n;
				}
				sawFrame = true;
				continue;
			}
			if (m != ZstdFrame.MAGIC) throw new CompressFormatException("zstd: not a zstd frame");
			exactly(head, 0, 1);
			int hs = ZstdFrame.headerSize(head[0] & 0xFF);
			exactly(head, 1, hs - 1);
			frame = ZstdFrame.parse(head, 0, maxWindow);
			sawFrame = true;
			frameProduced = 0;
			lastBlockDone = false;
			blocks.startFrame();
			hash.reset();
			long w = frame.windowSize;
			long want = frame.contentSize >= 0 && frame.contentSize <= DIRECT_LIMIT
				? frame.contentSize + ZstdBlocks.BLOCK_MAX
				: w + Math.max(w / 2, 1 << 20);
			want = Math.max(want, ZstdBlocks.BLOCK_MAX);
			if (buf.length < want + ZstdBlocks.SLACK || buf.length > 2 * (want + ZstdBlocks.SLACK) + (4 << 20)) {
				buf = new byte[(int) want + ZstdBlocks.SLACK];
			}
			bufEnd = buf.length - ZstdBlocks.SLACK;
			histStart = readPos = writePos = 0;
			return true;
		}
	}

	private void nextBlock() throws IOException {
		exactly(head, 0, 3);
		int h = (head[0] & 0xFF) | (head[1] & 0xFF) << 8 | (head[2] & 0xFF) << 16;
		int type = (h >>> 1) & 3;
		int size = h >>> 3;
		int blockMax = frame.blockMax();
		if (size > blockMax) throw new CompressFormatException("zstd: block larger than the maximum");
		if (type == 3) throw new CompressFormatException("zstd: reserved block type");

		if (writePos + blockMax > bufEnd) {
			int keep = (int) Math.min(frame.windowSize, writePos - histStart);
			int from = writePos - keep;
			System.arraycopy(buf, from, buf, 0, keep);
			histStart = 0;
			writePos = readPos = keep;
			if (writePos + blockMax > bufEnd) throw new CompressFormatException("zstd: output past the frame size");
		}
		long room = frame.contentSize >= 0 ? frame.contentSize - frameProduced : Long.MAX_VALUE;
		int limit = (int) Math.min(writePos + blockMax, writePos + Math.min(room, Integer.MAX_VALUE));
		int start = writePos;
		switch (type) {
			case 0 -> {
				if (size > limit - start) throw new CompressFormatException("zstd: output past the frame size");
				exactly(buf, start, size);
				writePos += size;
			}
			case 1 -> {
				if (size > limit - start) throw new CompressFormatException("zstd: output past the frame size");
				exactly(head, 0, 1);
				Arrays.fill(buf, start, start + size, head[0]);
				writePos += size;
			}
			default -> {
				exactly(block, 0, size);
				writePos = blocks.block(block, 0, size, buf, start, limit, histStart, buf.length);
			}
		}
		frameProduced += writePos - start;
		if (frame.checksum) hash.update(buf, start, writePos - start);
		if ((h & 1) != 0) {
			lastBlockDone = true;
			if (writePos == start) finishFrame();
		}
	}

	private void finishFrame() throws IOException {
		if (frame.contentSize >= 0 && frameProduced != frame.contentSize) throw new CompressFormatException("zstd: content size mismatch");
		if (frame.checksum) {
			exactly(head, 0, 4);
			if ((int) hash.digest() != Zstd.intLE(head, 0)) throw new CompressFormatException("zstd: content checksum mismatch");
		}
		frame = null;
	}
}
