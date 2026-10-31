// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compress;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class PackHuffman {
	static final int MAX_LEN = 11;
	static final int TABLE = 1 << MAX_LEN;

	private PackHuffman() {}

	static int[] lengths(long[] freq, int maxLen) {
		int n = freq.length;
		int[] len = new int[n];
		int used = 0;
		for (long f : freq) if (f > 0) used++;
		if (used <= 1) return len;
		Integer[] order = new Integer[used];
		int k = 0;
		for (int s = 0; s < n; s++) if (freq[s] > 0) order[k++] = s;
		Arrays.sort(order, (a, b) -> freq[a] != freq[b] ? Long.compare(freq[a], freq[b]) : Integer.compare(a, b));

		long[] leafW = new long[used];
		for (int i = 0; i < used; i++) leafW[i] = freq[order[i]];
		List<long[]> prevW = null;
		List<int[]> prevKids = null;
		long[] curW = null;
		int[][] cur = null;
		List<int[][]> levels = new ArrayList<>();
		for (int level = 0; level < maxLen; level++) {

			int pk = level == 0 ? 0 : cur.length / 2;
			long[] pw = new long[pk];
			for (int i = 0; i < pk; i++) pw[i] = curW[2 * i] + curW[2 * i + 1];
			int total = used + pk;
			long[] w = new long[total];
			int[][] items = new int[total][];
			int a = 0, b = 0;
			for (int i = 0; i < total; i++) {
				if (b >= pk || (a < used && leafW[a] <= pw[b])) {
					w[i] = leafW[a];
					items[i] = new int[]{order[a], -1, -1};
					a++;
				} else {
					w[i] = pw[b];
					items[i] = new int[]{-1, 2 * b, 2 * b + 1};
					b++;
				}
			}
			levels.add(items);
			curW = w;
			cur = items;
		}

		int take = 2 * used - 2;
		int[] counts = new int[n];
		int[] stackLevel = new int[4096];
		int[] stackIdx = new int[4096];
		for (int i = 0; i < take; i++) {
			int sp = 0;
			stackLevel[sp] = maxLen - 1; stackIdx[sp] = i; sp++;
			while (sp > 0) {
				sp--;
				int lv = stackLevel[sp], ix = stackIdx[sp];
				int[] it = levels.get(lv)[ix];
				if (it[0] >= 0) counts[it[0]]++;
				else {
					stackLevel[sp] = lv - 1; stackIdx[sp] = it[1]; sp++;
					stackLevel[sp] = lv - 1; stackIdx[sp] = it[2]; sp++;
				}
			}
		}
		for (int s = 0; s < n; s++) len[s] = counts[s];
		return len;
	}

	static int[] codes(int[] len) {
		int n = len.length;
		int[] codes = new int[n];
		int[] perLen = new int[MAX_LEN + 2];
		for (int l : len) if (l > 0) perLen[l]++;
		int[] next = new int[MAX_LEN + 2];
		int code = 0;
		for (int l = 1; l <= MAX_LEN; l++) {
			next[l] = code;
			code = (code + perLen[l]) << 1;
		}
		for (int s = 0; s < n; s++) {
			int l = len[s];
			if (l == 0) continue;
			codes[s] = Integer.reverse(next[l]++) >>> (32 - l);
		}
		return codes;
	}

	static void writeTable(ByteSink out, int[] len, long[] freq) {
		int n = len.length;
		int used = 0, lone = -1, last = -1;
		for (int s = 0; s < n; s++) if (freq[s] > 0) { used++; lone = s; last = s; }
		if (used == 0) { out.put(0); return; }
		if (used == 1) { out.put(1); out.varint(lone); return; }
		out.put(2);
		int count = last + 1;
		out.varint(count - 1);
		int[] nib = new int[count * 2];
		int k = 0;
		for (int s = 0; s < count; ) {
			if (len[s] == 0) {
				int run = 0;
				while (s + run < count && len[s + run] == 0 && run < 18) run++;
				if (run >= 3) {
					nib[k++] = 15; nib[k++] = run - 3; s += run;
					continue;
				}
			}
			nib[k++] = len[s++];
		}
		for (int i = 0; i < k; i += 2) out.put(nib[i] | (i + 1 < k ? nib[i + 1] << 4 : 0));
	}

	static final int NONE = 0, LONE = 1, CODE = 2;

	interface IntCell { int cell(int sym, int len); }

	interface LongCell { long cell(int sym, int len); }

	static long readLengths(byte[] src, int p, int end, int alphabet, int[] lens) throws CompressFormatException {
		if (p >= end) throw new CompressFormatException("apex pack: truncated code table");
		int kind = src[p++] & 0xFF;
		if (kind == 0) return p | (long) NONE << 32;
		if (kind == 1) {
			long[] v = new long[1];
			p = varint(src, p, end, v);
			if (v[0] >= alphabet) throw new CompressFormatException("apex pack: symbol out of range");
			return p | (long) LONE << 32 | v[0] << 40;
		}
		if (kind != 2) throw new CompressFormatException("apex pack: bad code table");
		long[] cv = new long[1];
		p = varint(src, p, end, cv);
		if (cv[0] >= alphabet) throw new CompressFormatException("apex pack: code table too large");
		int count = (int) cv[0] + 1;
		int s = 0;
		boolean high = false;
		int cur = 0;
		long kraft = 0;
		while (s < count) {
			int v;
			if (!high) {
				if (p >= end) throw new CompressFormatException("apex pack: truncated code table");
				cur = src[p++] & 0xFF;
				v = cur & 15;
			} else v = cur >>> 4;
			high = !high;
			if (v == 15) {
				int r;
				if (!high) {
					if (p >= end) throw new CompressFormatException("apex pack: truncated code table");
					cur = src[p++] & 0xFF;
					r = cur & 15;
				} else r = cur >>> 4;
				high = !high;
				r += 3;
				if (s + r > count) throw new CompressFormatException("apex pack: bad zero run");
				for (int i = 0; i < r; i++) lens[s++] = 0;
			} else if (v > MAX_LEN) {
				throw new CompressFormatException("apex pack: bad code length");
			} else {
				lens[s++] = v;
				if (v > 0) kraft += 1L << (MAX_LEN - v);
			}
		}
		if (kraft != TABLE) throw new CompressFormatException("apex pack: incomplete code");
		return p | (long) CODE << 32 | (long) count << 40;
	}

	static void fill(long r, int[] lens, int[] cells, int at, IntCell f) {
		int kind = (int) (r >>> 32) & 3;
		if (kind == NONE) { Arrays.fill(cells, at, at + TABLE, -1); return; }
		if (kind == LONE) { Arrays.fill(cells, at, at + TABLE, f.cell((int) (r >>> 40), 0)); return; }
		int count = (int) (r >>> 40);
		int[] next = firstCodes(lens, count);
		for (int sym = 0; sym < count; sym++) {
			int l = lens[sym];
			if (l == 0) continue;
			int cell = f.cell(sym, l);
			int step = 1 << l;
			for (int i = at + (Integer.reverse(next[l]++) >>> (32 - l)), e = at + TABLE; i < e; i += step) cells[i] = cell;
		}
	}

	static void fill(long r, int[] lens, long[] cells, LongCell f) {
		int kind = (int) (r >>> 32) & 3;
		if (kind == NONE) { Arrays.fill(cells, 0, TABLE, -1); return; }
		if (kind == LONE) { Arrays.fill(cells, 0, TABLE, f.cell((int) (r >>> 40), 0)); return; }
		int count = (int) (r >>> 40);
		int[] next = firstCodes(lens, count);
		for (int sym = 0; sym < count; sym++) {
			int l = lens[sym];
			if (l == 0) continue;
			long cell = f.cell(sym, l);
			int step = 1 << l;
			for (int i = Integer.reverse(next[l]++) >>> (32 - l); i < TABLE; i += step) cells[i] = cell;
		}
	}

	private static int[] firstCodes(int[] lens, int count) {
		int[] perLen = new int[MAX_LEN + 1];
		for (int i = 0; i < count; i++) perLen[lens[i]]++;
		int[] next = new int[MAX_LEN + 1];
		int code = 0;
		for (int l = 1; l <= MAX_LEN; l++) {
			next[l] = code;
			code = (code + perLen[l]) << 1;
		}
		return next;
	}

	private static int varint(byte[] src, int p, int end, long[] out) throws CompressFormatException {
		long v = 0;
		for (int shift = 0; shift < 35; shift += 7) {
			if (p >= end) throw new CompressFormatException("apex pack: truncated code table");
			int b = src[p++] & 0xFF;
			v |= (long) (b & 0x7F) << shift;
			if ((b & 0x80) == 0) { out[0] = v; return p; }
		}
		throw new CompressFormatException("apex pack: bad varint");
	}

	static final class ByteSink {
		byte[] buf = new byte[1 << 12];
		int size;

		void put(int b) {
			if (size == buf.length) buf = Arrays.copyOf(buf, buf.length * 2);
			buf[size++] = (byte) b;
		}

		void put(byte[] b, int off, int len) {
			if (size + len > buf.length) buf = Arrays.copyOf(buf, Math.max(buf.length * 2, size + len));
			System.arraycopy(b, off, buf, size, len);
			size += len;
		}

		void varint(long v) {
			while ((v & ~0x7FL) != 0) { put((int) (v & 0x7F) | 0x80); v >>>= 7; }
			put((int) v);
		}

		byte[] toArray() { return Arrays.copyOf(buf, size); }
	}

	static final class BitSink {
		final ByteSink out = new ByteSink();
		long acc;
		int bits;

		void put(long value, int n) {
			if (n == 0) return;
			acc |= (value & ((1L << n) - 1)) << bits;
			bits += n;
			if (bits >= 32) {
				for (int i = 0; i < 4; i++) { out.put((int) acc); acc >>>= 8; }
				bits -= 32;
			}
		}

		byte[] finish() {
			while (bits > 0) { out.put((int) acc); acc >>>= 8; bits -= 8; }
			bits = 0; acc = 0;
			return out.toArray();
		}
	}
}
