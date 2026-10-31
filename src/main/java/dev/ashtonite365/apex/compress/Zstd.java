// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compress;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public final class Zstd {

	public static final long DEFAULT_MAX_WINDOW = 1L << 27;

	private Zstd() {}

	public static boolean isZstdFrame(byte[] head, int off, int len) {
		if (len < 4) return false;
		int m = intLE(head, off);
		return m == ZstdFrame.MAGIC || (m & ZstdFrame.SKIPPABLE_MASK) == ZstdFrame.SKIPPABLE;
	}

	static int intLE(byte[] b, int o) {
		return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8 | (b[o + 2] & 0xFF) << 16 | (b[o + 3] & 0xFF) << 24;
	}

	public static long contentSize(byte[] src, int off, int len) throws CompressFormatException {
		int end = off + len, p = off;
		long total = 0;
		while (p < end) {
			if (end - p < 4) throw new CompressFormatException("zstd: truncated frame");
			int m = intLE(src, p);
			if ((m & ZstdFrame.SKIPPABLE_MASK) == ZstdFrame.SKIPPABLE) {
				if (end - p < 8) throw new CompressFormatException("zstd: truncated skippable frame");
				long size = intLE(src, p + 4) & 0xFFFFFFFFL;
				if (size > end - p - 8) throw new CompressFormatException("zstd: truncated skippable frame");
				p += 8 + (int) size;
				continue;
			}
			if (m != ZstdFrame.MAGIC) throw new CompressFormatException("zstd: not a zstd frame");
			if (end - p < 5 || end - p - 4 < ZstdFrame.headerSize(src[p + 4] & 0xFF)) throw new CompressFormatException("zstd: truncated frame header");
			ZstdFrame f = ZstdFrame.parse(src, p + 4, Long.MAX_VALUE);
			if (f.contentSize < 0) return -1;
			total += f.contentSize;
			p += 4 + f.headerSize;

			int blockMax = f.blockMax();
			long bound = 0;
			while (true) {
				if (end - p < 3) throw new CompressFormatException("zstd: truncated block header");
				int h = (src[p] & 0xFF) | (src[p + 1] & 0xFF) << 8 | (src[p + 2] & 0xFF) << 16;
				int type = (h >>> 1) & 3;
				int size = h >>> 3;
				int body = type == 1 ? 1 : size;
				if (type == 3) throw new CompressFormatException("zstd: reserved block type");
				if (size > blockMax) throw new CompressFormatException("zstd: block larger than the maximum");
				if (body > end - p - 3) throw new CompressFormatException("zstd: truncated block");
				bound += type == 2 ? blockMax : size;
				p += 3 + body;
				if ((h & 1) != 0) break;
			}
			if (f.contentSize > bound) throw new CompressFormatException("zstd: declared content size larger than the blocks can make");
			if (f.checksum) p += 4;
			if (p > end) throw new CompressFormatException("zstd: truncated checksum");
		}
		return total;
	}

	public static byte[] decompress(byte[] src) throws IOException {
		return decompress(src, 0, src.length, Integer.MAX_VALUE - 16);
	}

	public static byte[] decompress(byte[] src, int off, int len, int maxOutput) throws IOException {
		long size = contentSize(src, off, len);
		if (size > maxOutput) throw new CompressFormatException("zstd: output over the limit");
		if (size >= 0) {
			byte[] out = new byte[(int) size];
			int n = decompress(src, off, len, out, 0, out.length);
			if (n != size) throw new CompressFormatException("zstd: content size mismatch");
			return out;
		}
		try (ZstdInputStream in = new ZstdInputStream(new ByteArrayInputStream(src, off, len))) {
			return in.readNBytesLimited(maxOutput);
		}
	}

	public static int decompress(byte[] src, int off, int len, byte[] dst, int dOff, int dLen) throws CompressFormatException {
		return decompress(src, off, len, dst, dOff, dLen, DEFAULT_MAX_WINDOW);
	}

	public static int decompress(byte[] src, int off, int len, byte[] dst, int dOff, int dLen, long maxWindow)
		throws CompressFormatException {
		java.util.Objects.checkFromIndexSize(off, len, src.length);
		java.util.Objects.checkFromIndexSize(dOff, dLen, dst.length);
		int end = off + len, p = off;
		int dEnd = dOff + dLen, pos = dOff;
		ZstdBlocks blocks = new ZstdBlocks();
		boolean any = false;
		while (p < end) {
			if (end - p < 4) throw new CompressFormatException("zstd: truncated frame");
			int m = intLE(src, p);
			if ((m & ZstdFrame.SKIPPABLE_MASK) == ZstdFrame.SKIPPABLE) {
				if (end - p < 8) throw new CompressFormatException("zstd: truncated skippable frame");
				long size = intLE(src, p + 4) & 0xFFFFFFFFL;
				if (size > end - p - 8) throw new CompressFormatException("zstd: truncated skippable frame");
				p += 8 + (int) size;
				any = true;
				continue;
			}
			if (m != ZstdFrame.MAGIC) throw new CompressFormatException("zstd: not a zstd frame");
			if (end - p < 5 || end - p - 4 < ZstdFrame.headerSize(src[p + 4] & 0xFF)) throw new CompressFormatException("zstd: truncated frame header");
			ZstdFrame f = ZstdFrame.parse(src, p + 4, maxWindow);
			p += 4 + f.headerSize;
			int frameStart = pos;
			int frameEnd = f.contentSize >= 0 ? (int) Math.min(dEnd, frameStart + f.contentSize) : dEnd;
			if (f.contentSize >= 0 && f.contentSize > dEnd - frameStart) throw new CompressFormatException("zstd: output buffer too small");
			int blockMax = f.blockMax();
			blocks.startFrame();
			while (true) {
				if (end - p < 3) throw new CompressFormatException("zstd: truncated block header");
				int h = (src[p] & 0xFF) | (src[p + 1] & 0xFF) << 8 | (src[p + 2] & 0xFF) << 16;
				p += 3;
				int type = (h >>> 1) & 3;
				int size = h >>> 3;
				if (size > blockMax) throw new CompressFormatException("zstd: block larger than the maximum");
				int limit = Math.min(frameEnd, pos + blockMax);
				switch (type) {
					case 0 -> {
						if (size > end - p) throw new CompressFormatException("zstd: truncated block");
						if (size > limit - pos) throw new CompressFormatException("zstd: output past the frame size");
						System.arraycopy(src, p, dst, pos, size);
						p += size; pos += size;
					}
					case 1 -> {
						if (p >= end) throw new CompressFormatException("zstd: truncated block");
						if (size > limit - pos) throw new CompressFormatException("zstd: output past the frame size");
						java.util.Arrays.fill(dst, pos, pos + size, src[p]);
						p += 1; pos += size;
					}
					case 2 -> {
						if (size > end - p) throw new CompressFormatException("zstd: truncated block");
						pos = blocks.block(src, p, p + size, dst, pos, limit, frameStart, dEnd);
						p += size;
					}
					default -> throw new CompressFormatException("zstd: reserved block type");
				}
				if ((h & 1) != 0) break;
			}
			if (f.contentSize >= 0 && pos - frameStart != f.contentSize) throw new CompressFormatException("zstd: content size mismatch");
			if (f.checksum) {
				if (end - p < 4) throw new CompressFormatException("zstd: truncated checksum");
				int want = intLE(src, p);
				p += 4;
				if ((int) XxHash64.hash(dst, frameStart, pos - frameStart) != want) throw new CompressFormatException("zstd: content checksum mismatch");
			}
			any = true;
		}
		if (!any) throw new CompressFormatException("zstd: no frame");
		return pos - dOff;
	}
}
