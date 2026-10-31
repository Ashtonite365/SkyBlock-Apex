// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compress;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class ApexPack {
	static final byte[] MAGIC = {(byte) 0xAE, 'A', 'P', 'K'};
	static final int VERSION = 1;
	static final int FLAG_CHECKSUM = 1;
	static final int MIN_MATCH = 3;
	static final int REPS = 3;
	static final int LEN_SYMBOLS = 64;

	static final int LIT_CONTEXTS = 16;

	static final int LL_CODES = 16;
	static final int LL_ESCAPE = 15;
	static final int LL_ESCAPE_BASE = 32;
	static final int[] LL_BASE = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 16, 24, LL_ESCAPE_BASE};
	static final int[] LL_EXTRA = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 3, 3, 0};

	static final int ML_CODES = 52;
	static final int TOKENS = LL_CODES * ML_CODES;
	static final int MAX_MATCH = MIN_MATCH + (1 << 22) - 1;
	static final int[] ML_BASE = new int[ML_CODES];
	static final int[] ML_EXTRA = new int[ML_CODES];
	static final int OFF_SYMBOLS = REPS + 64;

	static final int MAX_BLOCK = 1 << 24;

	public static final int DEFAULT_MAX_OUTPUT = 1 << 30;

	static final int[] BUCKET_BASE = new int[64];
	static final int[] BUCKET_EXTRA = new int[64];

	static {
		for (int c = 0; c < ML_CODES; c++) {
			if (c < 16) { ML_BASE[c] = c; continue; }
			int k = 4 + ((c - 16) >> 1);
			int m = (c - 16) & 1;
			ML_BASE[c] = (2 | m) << (k - 1);
			ML_EXTRA[c] = k - 1;
		}
		for (int s = 0; s < 64; s++) {
			if (s < 8) { BUCKET_BASE[s] = s; continue; }
			int k = 3 + ((s - 8) >> 1);
			int m = (s - 8) & 1;
			BUCKET_BASE[s] = k >= 31 ? Integer.MAX_VALUE : (1 << k) | (m << (k - 1));
			BUCKET_EXTRA[s] = k - 1;
		}
	}

	static int bucket(int v) {
		if (v < 8) return v;
		int k = 31 - Integer.numberOfLeadingZeros(v);
		return 8 + 2 * (k - 3) + ((v >>> (k - 1)) & 1);
	}

	static int llCode(int v) {
		if (v < 12) return v;
		if (v < 16) return 12;
		if (v < 24) return 13;
		if (v < 32) return 14;
		return LL_ESCAPE;
	}

	static int mlCode(int v) {
		if (v < 16) return v;
		int k = 31 - Integer.numberOfLeadingZeros(v);
		return 16 + 2 * (k - 4) + ((v >>> (k - 1)) & 1);
	}

	private static final VarHandle LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

	private ApexPack() {}

	public static boolean isApexPack(byte[] head, int off, int len) {
		if (len < 4) return false;
		for (int i = 0; i < 4; i++) if (head[off + i] != MAGIC[i]) return false;
		return true;
	}

	public static byte[] decompress(byte[] src) throws CompressFormatException {
		return decompress(src, 0, src.length, DEFAULT_MAX_OUTPUT);
	}

	public static long contentSize(byte[] src, int off, int len) throws CompressFormatException {
		Cursor c = new Cursor(src, off, off + len);
		header(c);
		return c.varint();
	}

	private static int header(Cursor c) throws CompressFormatException {
		if (c.end - c.p < 6 || !isApexPack(c.src, c.p, 4)) throw new CompressFormatException("apex pack: bad magic");
		c.p += 4;
		int version = c.u8();
		if (version != VERSION) throw new CompressFormatException("apex pack: unsupported version " + version);
		int flags = c.u8();
		if ((flags & ~FLAG_CHECKSUM) != 0) throw new CompressFormatException("apex pack: unknown flags");
		return flags;
	}

	public static byte[] decompress(byte[] src, int off, int len, int maxOutput) throws CompressFormatException {
		Cursor c = new Cursor(src, off, off + len);
		int flags = header(c);
		long size = c.varint();
		if (size > maxOutput) throw new CompressFormatException("apex pack: content of " + size + " bytes is over the limit");

		int first = (int) Math.min(size, Math.max(1 << 16, Math.min((long) len * 32, 1 << 26)));
		byte[] out = body(c, flags, new byte[first], 0, (int) size, true);
		return out.length == size ? out : Arrays.copyOf(out, (int) size);
	}

	public static int decompress(byte[] src, int off, int len, byte[] dst, int dOff, int dLen) throws CompressFormatException {
		java.util.Objects.checkFromIndexSize(dOff, dLen, dst.length);
		Cursor c = new Cursor(src, off, off + len);
		int flags = header(c);
		long size = c.varint();
		if (size > dLen) throw new CompressFormatException("apex pack: content of " + size + " bytes does not fit the output");
		body(c, flags, dst, dOff, dOff + (int) size, false);
		return (int) size;
	}

	private static byte[] body(Cursor c, int flags, byte[] dst, int start, int end, boolean grow) throws CompressFormatException {
		Decoder d = new Decoder(start, end, dst, grow);
		d.run(c);
		byte[] out = d.out;
		if ((flags & FLAG_CHECKSUM) != 0) {
			if (c.end - c.p < 8) throw new CompressFormatException("apex pack: truncated checksum");
			long want = (long) LONG.get(c.src, c.p);
			c.p += 8;
			if (XxHash64.hash(out, start, end - start) != want) throw new CompressFormatException("apex pack: checksum mismatch");
		}
		if (c.p != c.end) throw new CompressFormatException("apex pack: trailing bytes");
		return out;
	}

	static final class Cursor {
		final byte[] src;
		int p;
		final int end;

		Cursor(byte[] src, int p, int end) {
			java.util.Objects.checkFromToIndex(p, end, src.length);
			this.src = src; this.p = p; this.end = end;
		}

		int u8() throws CompressFormatException {
			if (p >= end) throw new CompressFormatException("apex pack: truncated");
			return src[p++] & 0xFF;
		}

		long varint() throws CompressFormatException {
			long v = 0;
			for (int shift = 0; shift < 63; shift += 7) {
				int b = u8();
				v |= (long) (b & 0x7F) << shift;
				if ((b & 0x80) == 0) return v;
			}
			throw new CompressFormatException("apex pack: bad varint");
		}

		int size(long max) throws CompressFormatException {
			long v = varint();
			if (v > max) throw new CompressFormatException("apex pack: size out of range");
			return (int) v;
		}
	}

	private static final class Decoder {

		final int start, end;
		final int[] litT = new int[LIT_CONTEXTS * PackHuffman.TABLE], lenT = new int[PackHuffman.TABLE];

		final int[] litCtx = new int[256];

		final long[] tokT = new long[PackHuffman.TABLE];

		final long[] offT = new long[PackHuffman.TABLE];
		final int[] scratch = new int[TOKENS];
		byte[] lits = new byte[0];
		byte[] stream = new byte[0];
		int r0 = 1, r1 = 2, r2 = 3;

		byte[] out;
		final boolean grow;

		Decoder(int start, int end, byte[] out, boolean grow) { this.start = start; this.end = end; this.out = out; this.grow = grow; }

		private void ensure(int need) {
			if (need <= out.length) return;
			out = Arrays.copyOf(out, (int) Math.min(end, Math.max(need, (long) out.length * 2)));
		}

		void run(Cursor c) throws CompressFormatException {
			int pos = start;
			while (true) {
				int type = c.u8();
				if (type == 0) break;
				if (type == 1) {
					int n = c.size(end - pos);
					if (n > c.end - c.p) throw new CompressFormatException("apex pack: truncated stored block");
					byte[] out = this.out;
					if (grow) { ensure(pos + n); out = this.out; }
					System.arraycopy(c.src, c.p, out, pos, n);
					c.p += n; pos += n;
				} else if (type == 2) {
					pos = lzBlock(c, pos);
				} else throw new CompressFormatException("apex pack: bad block type");
			}
			if (pos != end) throw new CompressFormatException("apex pack: content shorter than declared");
		}

		private byte[] streamCopy(Cursor c, int n) throws CompressFormatException {
			if (n > c.end - c.p) throw new CompressFormatException("apex pack: truncated stream");
			if (stream.length < n + 16) stream = new byte[Math.max(n + 16, stream.length * 3 / 2)];
			System.arraycopy(c.src, c.p, stream, 0, n);
			Arrays.fill(stream, n, n + 16, (byte) 0);
			c.p += n;
			return stream;
		}

		private int lzBlock(Cursor c, int pos) throws CompressFormatException {
			int raw = c.size(Math.min(MAX_BLOCK, end - pos));
			if (grow) ensure(pos + raw);
			byte[] out = this.out;
			int litCount = c.size(raw);
			int seqCount = c.size(raw / MIN_MATCH);

			readLiteralTables(c, litCount);
			int s0 = c.size(Integer.MAX_VALUE), s1 = c.size(Integer.MAX_VALUE), s2 = c.size(Integer.MAX_VALUE);
			int litBytes = c.size(Integer.MAX_VALUE);
			if ((long) s0 + s1 + s2 > litBytes) throw new CompressFormatException("apex pack: bad literal stream sizes");
			byte[] s = streamCopy(c, litBytes);
			if (lits.length < litCount + 16) lits = new byte[Math.max(litCount + 16, lits.length * 3 / 2)];
			literals(s, s0, s1, s2, litBytes, litCount);

			long r = PackHuffman.readLengths(c.src, c.p, c.end, TOKENS, scratch);
			c.p = (int) r;
			PackHuffman.fill(r, scratch, tokT, Decoder::tokenCell);
			r = PackHuffman.readLengths(c.src, c.p, c.end, LEN_SYMBOLS, scratch);
			c.p = (int) r;
			PackHuffman.fill(r, scratch, lenT, 0, (sym, l) -> sym << 4 | l);
			r = PackHuffman.readLengths(c.src, c.p, c.end, OFF_SYMBOLS, scratch);
			c.p = (int) r;
			PackHuffman.fill(r, scratch, offT, Decoder::offsetCell);
			int tokBytes = c.size(Integer.MAX_VALUE);
			int seqBytes = c.size(Integer.MAX_VALUE);
			if (tokBytes > seqBytes) throw new CompressFormatException("apex pack: bad sequence stream sizes");
			s = streamCopy(c, seqBytes);
			return sequences(s, tokBytes, seqBytes, seqCount, out, pos, litCount, raw);
		}

		private void readLiteralTables(Cursor c, int litCount) throws CompressFormatException {
			int contexts = c.u8();
			if (contexts < 1 || contexts > LIT_CONTEXTS) throw new CompressFormatException("apex pack: bad literal context count");
			if (contexts == 1) Arrays.fill(litCtx, 0);
			else {
				for (int i = 0; i < 256; i += 2) {
					int m = c.u8();
					int lo = m & 15, hi = m >>> 4;
					if (lo >= contexts || hi >= contexts) throw new CompressFormatException("apex pack: bad literal context map");
					litCtx[i] = lo << PackHuffman.MAX_LEN;
					litCtx[i + 1] = hi << PackHuffman.MAX_LEN;
				}
			}
			final int[] ctx = litCtx;

			PackHuffman.IntCell cell = (sym, l) -> sym << 4 | l | (ctx[sym] >>> PackHuffman.MAX_LEN) << 12;
			for (int k = 0; k < contexts; k++) {
				int at = k << PackHuffman.MAX_LEN;
				long r = PackHuffman.readLengths(c.src, c.p, c.end, 256, scratch);
				c.p = (int) r;
				PackHuffman.fill(r, scratch, litT, at, cell);
				if (litCount > 0 && litT[at] < 0) throw new CompressFormatException("apex pack: literal context without a code");
			}
		}

		private static long tokenCell(int sym, int l) {
			int llc = sym / ML_CODES, mlc = sym % ML_CODES;
			long llBase = llc == LL_ESCAPE ? 63 : LL_BASE[llc];
			long len = l;
			long all = len + LL_EXTRA[llc] + ML_EXTRA[mlc];
			return all | len << 6 | (long) LL_EXTRA[llc] << 10 | (long) ML_EXTRA[mlc] << 13 | llBase << 18
				| (long) (ML_BASE[mlc] + MIN_MATCH) << 24;
		}

		private static long offsetCell(int sym, int l) {
			long base = 0, extra = 0;
			if (sym >= REPS) { base = BUCKET_BASE[sym - REPS] + 1L; extra = BUCKET_EXTRA[sym - REPS]; }
			long len = l;
			return (len + extra) | len << 6 | (long) sym << 10 | base << 24;
		}

		private static void streamEnd(int p, int used, int bytes, String what) throws CompressFormatException {
			p += used >>> 3; used &= 7;
			if (((long) p << 3) + used > (long) bytes << 3 || (bytes > 0 && ((long) p << 3) + used <= ((long) bytes - 1) << 3))
				throw new CompressFormatException("apex pack: " + what + " stream size mismatch");
		}

		private void literals(byte[] s, int b0, int b1, int b2, int bytes, int n) throws CompressFormatException {
			final int[] t = litT;
			final byte[] dst = lits;
			final int mask = PackHuffman.TABLE - 1;
			final int q = n >>> 2;
			final int o1 = b0, o2 = b0 + b1, o3 = b0 + b1 + b2;
			final int e0 = o1, e1 = o2, e2 = o3, e3 = bytes;
			int p0 = 0, p1 = o1, p2 = o2, p3 = o3;
			int u0 = 0, u1 = 0, u2 = 0, u3 = 0;

			int c0 = litCtx[0], c1 = c0, c2 = c0, c3 = c0;
			int i = 0;
			while (i + 5 <= q) {
				long w0 = (long) LONG.get(s, p0), w1 = (long) LONG.get(s, p1), w2 = (long) LONG.get(s, p2), w3 = (long) LONG.get(s, p3);
				for (int k = 0; k < 5; k++) {
					int x0 = t[c0 | (int) (w0 >>> u0) & mask], x1 = t[c1 | (int) (w1 >>> u1) & mask];
					int x2 = t[c2 | (int) (w2 >>> u2) & mask], x3 = t[c3 | (int) (w3 >>> u3) & mask];
					dst[i + k] = (byte) (x0 >>> 4); u0 += x0 & 15; c0 = (x0 >>> 12) << 11;
					dst[q + i + k] = (byte) (x1 >>> 4); u1 += x1 & 15; c1 = (x1 >>> 12) << 11;
					dst[2 * q + i + k] = (byte) (x2 >>> 4); u2 += x2 & 15; c2 = (x2 >>> 12) << 11;
					dst[3 * q + i + k] = (byte) (x3 >>> 4); u3 += x3 & 15; c3 = (x3 >>> 12) << 11;
				}
				i += 5;
				p0 += u0 >>> 3; u0 &= 7; p1 += u1 >>> 3; u1 &= 7; p2 += u2 >>> 3; u2 &= 7; p3 += u3 >>> 3; u3 &= 7;
				if (p0 > e0 || p1 > e1 || p2 > e2 || p3 > e3) throw new CompressFormatException("apex pack: literal stream overrun");
			}

			int[] st = {p0, u0, c0, p1, u1, c1, p2, u2, c2, p3, u3, c3};
			int[] ends = {e0, e1, e2, e3};
			for (int k = 0; k < 4; k++) {
				int p = st[3 * k], u = st[3 * k + 1], cx = st[3 * k + 2];
				int from = k * q + i, to = k == 3 ? n : (k + 1) * q;
				for (int j = from; j < to; j++) {
					long w = (long) LONG.get(s, p);
					int x = t[cx | (int) (w >>> u) & mask];
					dst[j] = (byte) (x >>> 4); u += x & 15; cx = (x >>> 12) << 11;
					p += u >>> 3; u &= 7;
					if (p > ends[k]) throw new CompressFormatException("apex pack: literal stream overrun");
				}
				int startByte = k == 0 ? 0 : ends[k - 1];
				streamEnd(p - startByte, u, ends[k] - startByte, "literal");
			}
		}

		private int sequences(byte[] s, int bytes, int allBytes, int count, byte[] out, int pos, int litCount, int raw)
			throws CompressFormatException {
			final long[] tok = tokT, off = offT;
			final int[] len = lenT;
			final int[] base = BUCKET_BASE, extra = BUCKET_EXTRA;
			final byte[] lit = lits;
			final int mask = PackHuffman.TABLE - 1;
			final int blockEnd = pos + raw;
			final int hard = Math.min(end, out.length), floor = start;
			int p = 0, used = 0;
			int q = bytes, qused = 0;
			int lp = 0;
			int a = r0, b = r1, cc = r2;
			if (count > 0 && (tok[0] < 0 || off[0] < 0)) throw new CompressFormatException("apex pack: sequences without a code");
			for (int i = 0; i < count; i++) {
				p += used >>> 3; used &= 7;
				if (p > bytes) throw new CompressFormatException("apex pack: sequence stream overrun");
				long word = (long) LONG.get(s, p);
				long e = tok[(int) (word >>> used) & mask];
				int llb = (int) (e >>> 18) & 63;
				int ll, ml;
				int mn = (int) (e >>> 13) & 31;
				if (llb != 63) {

					int at = used + ((int) (e >>> 6) & 15);
					int ln = (int) (e >>> 10) & 7;
					used += (int) e & 63;
					ll = llb + (int) ((word >>> at) & ((1L << ln) - 1));
					ml = (int) (e >>> 24) + (int) ((word >>> (at + ln)) & ((1L << mn) - 1));
				} else {
					used += (int) (e >>> 6) & 15;
					int le = len[(int) (word >>> used) & mask];
					if (le < 0) throw new CompressFormatException("apex pack: length without a code");
					used += le & 15;
					int ls = le >>> 4;
					p += used >>> 3; used &= 7; word = (long) LONG.get(s, p);
					int n = extra[ls];
					ll = LL_ESCAPE_BASE + base[ls] + (int) ((word >>> used) & ((1L << n) - 1));
					used += n;
					p += used >>> 3; used &= 7;
					if (p > bytes) throw new CompressFormatException("apex pack: sequence stream overrun");
					word = (long) LONG.get(s, p);
					ml = (int) (e >>> 24) + (int) ((word >>> used) & ((1L << mn) - 1));
					used += mn;
				}

				q += qused >>> 3; qused &= 7;
				if (q > allBytes) throw new CompressFormatException("apex pack: offset stream overrun");
				long ow = (long) LONG.get(s, q);
				long oe = off[(int) (ow >>> qused) & mask];
				int os = (int) (oe >>> 10) & 127;
				int dist;
				if (os >= REPS) {
					int at = qused + ((int) (oe >>> 6) & 15);
					int n = ((int) oe & 63) - ((int) (oe >>> 6) & 15);
					qused += (int) oe & 63;
					dist = (int) (oe >>> 24) + (int) ((ow >>> at) & ((1L << n) - 1));
					cc = b; b = a; a = dist;
				} else {
					qused += (int) oe & 63;
					if (os == 0) {
						dist = a;
					} else if (os == 1) {
						dist = b; b = a; a = dist;
					} else {
						dist = cc; cc = b; b = a; a = dist;
					}
				}

				if (ll < 0 || ll > litCount - lp || ml > blockEnd - pos - ll - (litCount - lp - ll))
					throw new CompressFormatException("apex pack: bad sequence");
				if (ll <= 16 && pos + 16 <= hard) {
					LONG.set(out, pos, (long) LONG.get(lit, lp));
					LONG.set(out, pos + 8, (long) LONG.get(lit, lp + 8));
				} else {
					System.arraycopy(lit, lp, out, pos, ll);
				}
				pos += ll; lp += ll;
				int from = pos - dist;
				if (dist <= 0 || from < floor) throw new CompressFormatException("apex pack: offset before the start");
				int stop = pos + ml;
				if (dist >= 16 && stop + 32 <= hard) {

					long x0 = (long) LONG.get(out, from), x1 = (long) LONG.get(out, from + 8);
					LONG.set(out, pos, x0);
					LONG.set(out, pos + 8, x1);
					long x2 = (long) LONG.get(out, from + 16), x3 = (long) LONG.get(out, from + 24);
					LONG.set(out, pos + 16, x2);
					LONG.set(out, pos + 24, x3);
					if (ml > 96 && dist >= ml) {
						System.arraycopy(out, from + 32, out, pos + 32, ml - 32);
					} else if (ml > 32) {
						pos += 32; from += 32;
						do {
							long x = (long) LONG.get(out, from), y = (long) LONG.get(out, from + 8);
							LONG.set(out, pos, x);
							LONG.set(out, pos + 8, y);
							pos += 16; from += 16;
						} while (pos < stop);
					}
				} else if (dist >= 16 && stop + 16 <= hard) {
					do {
						long x = (long) LONG.get(out, from), y = (long) LONG.get(out, from + 8);
						LONG.set(out, pos, x);
						LONG.set(out, pos + 8, y);
						pos += 16; from += 16;
					} while (pos < stop);
				} else if (dist >= 8 && stop + 8 <= hard) {
					do {
						LONG.set(out, pos, (long) LONG.get(out, from));
						pos += 8; from += 8;
					} while (pos < stop);
				} else if (dist == 1) {
					Arrays.fill(out, pos, stop, out[from]);
				} else if (ml < 16) {
					for (int k = 0; k < ml; k++) out[pos + k] = out[from + k];
				} else {
					int done = 0, period = dist;
					while (done < ml) {
						int k = Math.min(period, ml - done);
						System.arraycopy(out, from, out, pos + done, k);
						done += k; period += k;
					}
				}
				pos = stop;
			}
			streamEnd(p, used, bytes, "token");
			streamEnd(q - bytes, qused, allBytes - bytes, "offset");
			int rest = litCount - lp;
			if (pos + rest != blockEnd) throw new CompressFormatException("apex pack: block sizes do not add up");
			System.arraycopy(lit, lp, out, pos, rest);
			r0 = a; r1 = b; r2 = cc;
			return blockEnd;
		}
	}
}
