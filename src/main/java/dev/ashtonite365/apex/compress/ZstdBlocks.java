// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compress;

import java.lang.invoke.VarHandle;

final class ZstdBlocks {
	static final int BLOCK_MAX = 128 * 1024;

	static final int SLACK = 32;
	private static final VarHandle LONG = BackBits.LONG;

	private static final int[] LL_BASE = new int[36];
	private static final int[] LL_EXTRA = new int[36];
	private static final int[] ML_BASE = new int[53];
	private static final int[] ML_EXTRA = new int[53];

	static {
		for (int c = 0; c < 16; c++) LL_BASE[c] = c;
		int[] llBits = {1, 1, 1, 1, 2, 2, 3, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
		int v = 16;
		for (int i = 0; i < llBits.length; i++) {
			LL_BASE[16 + i] = v;
			LL_EXTRA[16 + i] = llBits[i];
			v += 1 << llBits[i];
		}
		for (int c = 0; c < 32; c++) ML_BASE[c] = c + 3;
		int[] mlBits = {1, 1, 1, 1, 2, 2, 3, 3, 4, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
		v = 35;
		for (int i = 0; i < mlBits.length; i++) {
			ML_BASE[32 + i] = v;
			ML_EXTRA[32 + i] = mlBits[i];
			v += 1 << mlBits[i];
		}
	}

	private static final ZstdFse LL_DEFAULT = defaults(6, new short[]{4, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 2, 1, 1, 1, 1, 1, -1, -1, -1, -1});
	private static final ZstdFse ML_DEFAULT = defaults(6, new short[]{1, 4, 3, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, -1, -1, -1, -1, -1, -1, -1});
	private static final ZstdFse OF_DEFAULT = defaults(5, new short[]{1, 1, 1, 1, 1, 1, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1, 1, 1, -1, -1, -1, -1, -1});

	private static ZstdFse defaults(int log, short[] dist) {
		ZstdFse t = new ZstdFse(log);
		try {
			t.build(dist, dist.length, log);
		} catch (CompressFormatException e) {
			throw new IllegalStateException(e);
		}
		return t;
	}

	private final ZstdHuffman huffman = new ZstdHuffman();
	private final ZstdFse llOwn = new ZstdFse(9), ofOwn = new ZstdFse(8), mlOwn = new ZstdFse(9);
	private ZstdFse llCur, ofCur, mlCur;
	private final short[] dist = new short[64];
	private final byte[] literals = new byte[BLOCK_MAX + SLACK];
	private int rep1, rep2, rep3;

	private byte[] litArr;
	private int litPos, litEnd;

	void startFrame() {
		rep1 = 1; rep2 = 4; rep3 = 8;
		huffman.present = false;
		llCur = ofCur = mlCur = null;
	}

	int block(byte[] src, int off, int end, byte[] out, int pos, int limit, int histStart, int hardEnd)
		throws CompressFormatException {
		int p = literalsSection(src, off, end);
		return sequences(src, p, end, out, pos, limit, histStart, hardEnd);
	}

	private int literalsSection(byte[] src, int off, int end) throws CompressFormatException {
		if (off >= end) throw new CompressFormatException("zstd: empty block");
		int b0 = src[off] & 0xFF;
		int type = b0 & 3;
		int format = (b0 >>> 2) & 3;
		if (type < 2) {
			int size, head;
			if ((format & 1) == 0) { size = b0 >>> 3; head = 1; }
			else if (format == 1) { need(off, 2, end); size = (b0 >>> 4) | ((src[off + 1] & 0xFF) << 4); head = 2; }
			else { need(off, 3, end); size = (b0 >>> 4) | ((src[off + 1] & 0xFF) << 4) | ((src[off + 2] & 0xFF) << 12); head = 3; }
			if (size > BLOCK_MAX) throw new CompressFormatException("zstd: literals too large");
			int p = off + head;
			if (type == 0) {
				need(p, size, end);
				if (p + size + SLACK <= src.length) {
					litArr = src; litPos = p; litEnd = p + size;
				} else {
					System.arraycopy(src, p, literals, 0, size);
					litArr = literals; litPos = 0; litEnd = size;
				}
				return p + size;
			}
			need(p, 1, end);
			java.util.Arrays.fill(literals, 0, size, src[p]);
			litArr = literals; litPos = 0; litEnd = size;
			return p + 1;
		}
		int head = format < 2 ? 3 : format + 2;
		int bits = format < 2 ? 10 : format == 2 ? 14 : 18;
		need(off, head, end);
		long v = 0;
		for (int i = head - 1; i >= 0; i--) v = (v << 8) | (src[off + i] & 0xFF);
		int mask = (1 << bits) - 1;
		int size = (int) (v >>> 4) & mask;
		int packed = (int) (v >>> (4 + bits)) & mask;
		if (size > BLOCK_MAX) throw new CompressFormatException("zstd: literals too large");
		int p = off + head;
		need(p, packed, end);
		int stop = p + packed;
		if (type == 2) p += huffman.read(src, p, stop);
		else if (!huffman.present) throw new CompressFormatException("zstd: treeless literals without a previous table");
		if (format == 0) {
			huffStream(src, p, stop, literals, 0, size);
		} else {
			if (stop - p < 10) throw new CompressFormatException("zstd: truncated literal streams");
			int s1 = (src[p] & 0xFF) | (src[p + 1] & 0xFF) << 8;
			int s2 = (src[p + 2] & 0xFF) | (src[p + 3] & 0xFF) << 8;
			int s3 = (src[p + 4] & 0xFF) | (src[p + 5] & 0xFF) << 8;
			int a = p + 6, b = a + s1, c = b + s2, d = c + s3;
			if (d >= stop) throw new CompressFormatException("zstd: bad literal jump table");
			int quarter = (size + 3) >>> 2;
			if (3 * quarter > size) throw new CompressFormatException("zstd: too few literals for four streams");
			huffStreams4(src, a, b, c, d, stop, literals, quarter, size);
		}
		litArr = literals; litPos = 0; litEnd = size;
		return stop;
	}

	static int floorBits(int at, int start) {
		return at >= start ? 64 : 64 - ((start - at) << 3);
	}

	private static void need(int at, int n, int end) throws CompressFormatException {
		if (n < 0 || at + n > end) throw new CompressFormatException("zstd: truncated block");
	}

	private void huffStreams4(byte[] src, int a, int b, int c, int d, int stop, byte[] dst, int q, int size)
		throws CompressFormatException {
		if (b - a < 16 || c - b < 16 || d - c < 16 || stop - d < 16 || q < 8) {
			huffStream(src, a, b, dst, 0, q);
			huffStream(src, b, c, dst, q, q);
			huffStream(src, c, d, dst, 2 * q, q);
			huffStream(src, d, stop, dst, 3 * q, size - 3 * q);
			return;
		}
		if (src[b - 1] == 0 || src[c - 1] == 0 || src[d - 1] == 0 || src[stop - 1] == 0) throw new CompressFormatException("zstd: bad literal stream");
		final short[] cells = huffman.cells;
		final int down = 64 - huffman.maxBits;
		int at0 = b - 8, at1 = c - 8, at2 = d - 8, at3 = stop - 8;
		long w0 = (long) LONG.get(src, at0), w1 = (long) LONG.get(src, at1), w2 = (long) LONG.get(src, at2), w3 = (long) LONG.get(src, at3);
		int u0 = Long.numberOfLeadingZeros(w0) + 1, u1 = Long.numberOfLeadingZeros(w1) + 1;
		int u2 = Long.numberOfLeadingZeros(w2) + 1, u3 = Long.numberOfLeadingZeros(w3) + 1;
		final int s0 = a + 8, s1 = b + 8, s2 = c + 8, s3 = d + 8;
		final int q2 = 2 * q, q3 = 3 * q;
		int i = 0;
		final int n = Math.min(q, size - q3);
		while (i + 5 <= n && at0 >= s0 && at1 >= s1 && at2 >= s2 && at3 >= s3) {
			at0 -= u0 >>> 3; u0 &= 7; w0 = (long) LONG.get(src, at0);
			at1 -= u1 >>> 3; u1 &= 7; w1 = (long) LONG.get(src, at1);
			at2 -= u2 >>> 3; u2 &= 7; w2 = (long) LONG.get(src, at2);
			at3 -= u3 >>> 3; u3 &= 7; w3 = (long) LONG.get(src, at3);
			for (int k = 0; k < 5; k++) {
				short x0 = cells[(int) ((w0 << u0) >>> down)], x1 = cells[(int) ((w1 << u1) >>> down)];
				short x2 = cells[(int) ((w2 << u2) >>> down)], x3 = cells[(int) ((w3 << u3) >>> down)];
				dst[i + k] = (byte) (x0 >> 4); u0 += x0 & 15;
				dst[q + i + k] = (byte) (x1 >> 4); u1 += x1 & 15;
				dst[q2 + i + k] = (byte) (x2 >> 4); u2 += x2 & 15;
				dst[q3 + i + k] = (byte) (x3 >> 4); u3 += x3 & 15;
			}
			i += 5;
		}
		huffTail(src, a, at0, u0, dst, i, q);
		huffTail(src, b, at1, u1, dst, q + i, q2);
		huffTail(src, c, at2, u2, dst, q2 + i, q3);
		huffTail(src, d, at3, u3, dst, q3 + i, size);
	}

	private void huffStream(byte[] src, int from, int to, byte[] dst, int d, int n) throws CompressFormatException {
		if (to <= from || src[to - 1] == 0) throw new CompressFormatException("zstd: bad literal stream");
		short[] cells = huffman.cells;
		int bits = huffman.maxBits;
		long word;
		int used, at;
		if (to - from >= 8) {
			at = to - 8;
			word = (long) LONG.get(src, at);
		} else {
			at = from - (8 - (to - from));
			long w = 0;
			for (int i = to - 1; i >= from; i--) w = (w << 8) | (src[i] & 0xFFL);
			word = w << (8 * (8 - (to - from)));
		}
		used = Long.numberOfLeadingZeros(word) + 1;
		int end = d + n;
		int safe = from + 8;
		final int down = 64 - bits;

		while (end - d >= 4 && at >= safe) {
			at -= used >>> 3;
			used &= 7;
			word = (long) LONG.get(src, at);
			short c = cells[(int) ((word << used) >>> down)];
			dst[d] = (byte) (c >> 4); used += c & 15;
			c = cells[(int) ((word << used) >>> down)];
			dst[d + 1] = (byte) (c >> 4); used += c & 15;
			c = cells[(int) ((word << used) >>> down)];
			dst[d + 2] = (byte) (c >> 4); used += c & 15;
			c = cells[(int) ((word << used) >>> down)];
			dst[d + 3] = (byte) (c >> 4); used += c & 15;
			d += 4;
		}
		huffTail(src, from, at, used, dst, d, end);
	}

	private void huffTail(byte[] src, int from, int at, int used, byte[] dst, int d, int end) throws CompressFormatException {
		final short[] cells = huffman.cells;
		final int down = 64 - huffman.maxBits;
		long word = at >= from ? (long) LONG.get(src, at) : paddedWord(src, from, at);
		while (d < end) {
			if (at > from && used >= 8) {
				int back = Math.min(used >>> 3, at - from);
				at -= back; used -= back << 3;
				word = (long) LONG.get(src, at);
			}
			if (at <= from && used >= floorBits(at, from)) throw new CompressFormatException("zstd: literal stream overrun");
			short c = cells[(int) ((word << used) >>> down)];
			dst[d++] = (byte) (c >> 4); used += c & 15;
		}
		if (at > from) {
			int back = Math.min(used >>> 3, at - from);
			at -= back; used -= back << 3;
		}
		if (at > from || used != floorBits(at, from)) throw new CompressFormatException("zstd: literal stream not fully used");
	}

	private static long paddedWord(byte[] src, int from, int at) {
		int len = 8 - (from - at);
		long w = 0;
		for (int i = from + len - 1; i >= from; i--) w = (w << 8) | (src[i] & 0xFFL);
		return w << (8 * (from - at));
	}

	private ZstdFse table(int mode, byte[] src, int[] p, int end, ZstdFse own, ZstdFse fallback, ZstdFse last,
		int maxLog, int symbols) throws CompressFormatException {
		switch (mode) {
			case 0:
				return fallback;
			case 1: {
				need(p[0], 1, end);
				int s = src[p[0]++] & 0xFF;
				if (s >= symbols) throw new CompressFormatException("zstd: RLE symbol out of range");
				own.single(s);
				return own;
			}
			case 2:
				p[0] += own.read(src, p[0], end, maxLog, symbols, dist);
				return own;
			default:
				if (last == null) throw new CompressFormatException("zstd: repeat table without a previous one");
				return last;
		}
	}

	private final int[] cursor = new int[1];

	private int sequences(byte[] src, int p, int end, byte[] out, int pos, int limit, int histStart, int hardEnd)
		throws CompressFormatException {
		need(p, 1, end);
		int b0 = src[p] & 0xFF;
		int count;
		if (b0 == 0) {
			if (p + 1 != end) throw new CompressFormatException("zstd: bytes after an empty sequences section");
			return copyLiterals(out, pos, litEnd - litPos, limit);
		} else if (b0 < 128) {
			count = b0; p += 1;
		} else if (b0 < 255) {
			need(p, 2, end);
			count = ((b0 - 128) << 8) + (src[p + 1] & 0xFF); p += 2;
		} else {
			need(p, 3, end);
			count = (src[p + 1] & 0xFF) + ((src[p + 2] & 0xFF) << 8) + 0x7F00; p += 3;
		}
		need(p, 1, end);
		int modes = src[p++] & 0xFF;
		if ((modes & 3) != 0) throw new CompressFormatException("zstd: reserved sequence mode bits set");
		cursor[0] = p;
		llCur = table(modes >>> 6, src, cursor, end, llOwn, LL_DEFAULT, llCur, 9, 36);
		ofCur = table((modes >>> 4) & 3, src, cursor, end, ofOwn, OF_DEFAULT, ofCur, 8, 32);
		mlCur = table((modes >>> 2) & 3, src, cursor, end, mlOwn, ML_DEFAULT, mlCur, 9, 53);
		p = cursor[0];
		if (p >= end || src[end - 1] == 0) throw new CompressFormatException("zstd: bad sequence stream");

		final int[] llT = llCur.cells, ofT = ofCur.cells, mlT = mlCur.cells;
		final int llLog = llCur.log, ofLog = ofCur.log, mlLog = mlCur.log;
		final byte[] lit = litArr;
		int lp = litPos;
		final int le = litEnd;
		int r1 = rep1, r2 = rep2, r3 = rep3;

		final int start = p, safe = p + 8;
		long word;
		int used, at;
		if (end - p >= 8) {
			at = end - 8;
			word = (long) LONG.get(src, at);
		} else {
			at = p - (8 - (end - p));
			long w = 0;
			for (int i = end - 1; i >= p; i--) w = (w << 8) | (src[i] & 0xFFL);
			word = w << (8 * (8 - (end - p)));
		}
		used = Long.numberOfLeadingZeros(word) + 1;
		int llS = (int) ((word << used) >>> 1 >>> (63 - llLog)); used += llLog;
		int ofS = (int) ((word << used) >>> 1 >>> (63 - ofLog)); used += ofLog;
		int mlS = (int) ((word << used) >>> 1 >>> (63 - mlLog)); used += mlLog;

		for (int left = count; left > 0; left--) {
			if (at >= safe) { at -= used >>> 3; used &= 7; word = (long) LONG.get(src, at); }
			else if (at > start) { int back = Math.min(used >>> 3, at - start); at -= back; used -= back << 3; word = (long) LONG.get(src, at); }
			int llC = llT[llS], ofC = ofT[ofS], mlC = mlT[mlS];
			int ofCode = ofC >>> 24;
			int mlCode = mlC >>> 24;
			int llCode = llC >>> 24;
			long ofVal = (1L << ofCode) + ((word << used) >>> 1 >>> (63 - ofCode));
			used += ofCode;
			if (used > 32) {
				if (at >= safe) { at -= used >>> 3; used &= 7; word = (long) LONG.get(src, at); }
				else if (at > start) { int back = Math.min(used >>> 3, at - start); at -= back; used -= back << 3; word = (long) LONG.get(src, at); }
			}
			int mlBits = ML_EXTRA[mlCode];
			int ml = ML_BASE[mlCode] + (int) ((word << used) >>> 1 >>> (63 - mlBits));
			used += mlBits;
			int llBits = LL_EXTRA[llCode];
			int ll = LL_BASE[llCode] + (int) ((word << used) >>> 1 >>> (63 - llBits));
			used += llBits;

			int offset;
			if (ofVal > 3) {
				offset = (int) Math.min(ofVal - 3, Integer.MAX_VALUE);
				r3 = r2; r2 = r1; r1 = offset;
			} else {
				int idx = (int) ofVal + (ll == 0 ? 1 : 0);
				if (idx == 1) {
					offset = r1;
				} else if (idx == 2) {
					offset = r2; r2 = r1; r1 = offset;
				} else {
					offset = idx == 3 ? r3 : r1 - 1;
					r3 = r2; r2 = r1; r1 = offset;
				}
			}

			if (left > 1) {
				if (used > 38) {
					if (at >= safe) { at -= used >>> 3; used &= 7; word = (long) LONG.get(src, at); }
					else if (at > start) { int back = Math.min(used >>> 3, at - start); at -= back; used -= back << 3; word = (long) LONG.get(src, at); }
				}
				int n = (llC >>> 16) & 0xFF;
				llS = (llC & 0xFFFF) + (int) ((word << used) >>> 1 >>> (63 - n)); used += n;
				n = (mlC >>> 16) & 0xFF;
				mlS = (mlC & 0xFFFF) + (int) ((word << used) >>> 1 >>> (63 - n)); used += n;
				n = (ofC >>> 16) & 0xFF;
				ofS = (ofC & 0xFFFF) + (int) ((word << used) >>> 1 >>> (63 - n)); used += n;
			}

			int litStop = lp + ll;
			int outStop = pos + ll + ml;
			if (litStop > le || outStop > limit || outStop < 0) throw new CompressFormatException("zstd: sequence past the block end");
			if (ll <= 16 && lp + 16 <= lit.length && pos + 16 <= hardEnd) {
				LONG.set(out, pos, (long) LONG.get(lit, lp));
				LONG.set(out, pos + 8, (long) LONG.get(lit, lp + 8));
			} else {
				System.arraycopy(lit, lp, out, pos, ll);
			}
			pos += ll;
			lp = litStop;
			if (offset <= 0 || offset > pos - histStart) throw new CompressFormatException("zstd: match offset out of range");
			int from = pos - offset;
			if (offset >= 16 && outStop + 32 <= hardEnd) {

				long x0 = (long) LONG.get(out, from), x1 = (long) LONG.get(out, from + 8);
				LONG.set(out, pos, x0);
				LONG.set(out, pos + 8, x1);
				long x2 = (long) LONG.get(out, from + 16), x3 = (long) LONG.get(out, from + 24);
				LONG.set(out, pos + 16, x2);
				LONG.set(out, pos + 24, x3);
				if (ml > 32) {
					if (ml > 96 && offset >= ml) {
						System.arraycopy(out, from + 32, out, pos + 32, ml - 32);
					} else {
						pos += 32; from += 32;
						do {
							long x = (long) LONG.get(out, from), y = (long) LONG.get(out, from + 8);
							LONG.set(out, pos, x);
							LONG.set(out, pos + 8, y);
							pos += 16; from += 16;
						} while (pos < outStop);
					}
				}
			} else if (offset >= 8 && outStop + 8 <= hardEnd) {
				do {
					LONG.set(out, pos, (long) LONG.get(out, from));
					pos += 8; from += 8;
				} while (pos < outStop);
			} else if (offset == 1) {
				java.util.Arrays.fill(out, pos, outStop, out[from]);
			} else if (ml < 16) {
				for (int i = 0; i < ml; i++) out[pos + i] = out[from + i];
			} else {
				int done = 0, period = offset;
				while (done < ml) {
					int k = Math.min(period, ml - done);
					System.arraycopy(out, from, out, pos + done, k);
					done += k; period += k;
				}
			}
			pos = outStop;
		}
		if (at > start) { int back = Math.min(used >>> 3, at - start); at -= back; used -= back << 3; }
		if (at > start || used != floorBits(at, start)) throw new CompressFormatException("zstd: sequence stream not fully used");
		rep1 = r1; rep2 = r2; rep3 = r3;
		litPos = lp;
		return copyLiterals(out, pos, le - lp, limit);
	}

	private int copyLiterals(byte[] out, int pos, int n, int limit) throws CompressFormatException {
		if (pos + n > limit) throw new CompressFormatException("zstd: literals past the block end");
		System.arraycopy(litArr, litPos, out, pos, n);
		litPos += n;
		return pos + n;
	}
}
