// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compress;

final class ZstdHuffman {
	static final int MAX_BITS = 11;
	int maxBits;
	final short[] cells = new short[1 << MAX_BITS];
	boolean present;

	private final byte[] weights = new byte[256];
	private final ZstdFse weightFse = new ZstdFse(6);
	private final short[] dist = new short[16];
	private final int[] runStart = new int[MAX_BITS + 2];

	int read(byte[] src, int off, int end) throws CompressFormatException {
		if (off >= end) throw new CompressFormatException("zstd: truncated Huffman description");
		int head = src[off] & 0xFF;
		int count;
		int used;
		if (head >= 128) {
			count = head - 127;
			used = 1 + (count + 1) / 2;
			if (used > end - off) throw new CompressFormatException("zstd: truncated Huffman weights");
			for (int i = 0; i < count; i++) {
				int b = src[off + 1 + (i >> 1)] & 0xFF;
				weights[i] = (byte) ((i & 1) == 0 ? b >>> 4 : b & 15);
			}
		} else {
			used = 1 + head;
			if (head == 0 || used > end - off) throw new CompressFormatException("zstd: truncated Huffman weights");
			int from = off + 1, to = off + 1 + head;
			int desc = weightFse.read(src, from, to, 6, 12, dist);
			count = decodeWeights(src, from + desc, to);
		}
		build(count);
		present = true;
		return used;
	}

	private int decodeWeights(byte[] src, int start, int end) throws CompressFormatException {
		BackBits in = new BackBits(src, start, end);
		int[] t = weightFse.cells;
		int lg = weightFse.log;
		int s1 = (int) in.read(lg);
		int s2 = (int) in.read(lg);
		int n = 0;
		while (true) {
			if (n > 253) throw new CompressFormatException("zstd: too many Huffman weights");
			int c = t[s1];
			weights[n++] = (byte) (c >>> 24);
			s1 = (c & 0xFFFF) + (int) in.read((c >>> 16) & 0xFF);
			if (in.overrun()) { weights[n++] = (byte) (t[s2] >>> 24); break; }
			c = t[s2];
			weights[n++] = (byte) (c >>> 24);
			s2 = (c & 0xFFFF) + (int) in.read((c >>> 16) & 0xFF);
			if (in.overrun()) { weights[n++] = (byte) (t[s1] >>> 24); break; }
		}
		return n;
	}

	private void build(int count) throws CompressFormatException {
		long sum = 0;
		for (int i = 0; i < count; i++) {
			int w = weights[i];
			if (w > MAX_BITS) throw new CompressFormatException("zstd: Huffman weight too large");
			if (w > 0) sum += 1L << (w - 1);
		}
		if (sum == 0) throw new CompressFormatException("zstd: empty Huffman tree");
		int bits = 64 - Long.numberOfLeadingZeros(sum);
		long rest = (1L << bits) - sum;
		if (Long.bitCount(rest) != 1 || bits > MAX_BITS) throw new CompressFormatException("zstd: bad Huffman weights");
		int last = Long.numberOfTrailingZeros(rest) + 1;
		weights[count] = (byte) last;
		int symbols = count + 1;
		maxBits = bits;

		final int[] next = runStart;
		java.util.Arrays.fill(next, 0);
		for (int s = 0; s < symbols; s++) next[weights[s]]++;
		int at = 0;
		for (int w = 1; w <= bits; w++) {
			int n = next[w];
			next[w] = at;
			at += n << (w - 1);
		}
		for (int s = 0; s < symbols; s++) {
			int w = weights[s];
			if (w == 0) continue;
			int from = next[w], to = from + (1 << (w - 1));
			next[w] = to;
			short cell = (short) ((s << 4) | (bits + 1 - w));
			for (int k = from; k < to; k++) cells[k] = cell;
		}
		if (at != 1 << bits) throw new CompressFormatException("zstd: bad Huffman weights");
	}
}
