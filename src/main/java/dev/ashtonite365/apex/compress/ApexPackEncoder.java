// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compress;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class ApexPackEncoder {
	private static final VarHandle LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
	private static final VarHandle INT = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

	public static final class Options {

		public int chainDepth = 96;

		public int niceLength = 256;

		public int passes = 2;

		public int blockSize = 1 << 20;

		public boolean checksum = true;
		public int hashBits = 22;

		public int window = Integer.MAX_VALUE;

		public int sequencePenalty = 0;

		public static Options max() {
			Options o = new Options();
			o.chainDepth = 1024; o.niceLength = 1024; o.passes = 2;
			return o;
		}

		public static Options fast() {
			Options o = new Options();
			o.chainDepth = 8; o.niceLength = 64; o.passes = 1;
			return o;
		}
	}

	private static final int SCALE = 16;
	private static final int SEGMENT = 1 << 15;
	private static final int MAX_MATCH = ApexPack.MAX_MATCH;

	private final byte[] in;
	private final int n;
	private final Options opt;
	private final int seqPenalty;

	private int[] sLL = new int[1024], sML = new int[1024], sDist = new int[1024];
	private int seqs;
	private int pending;

	private final int[] litPrice = new int[256];
	private final int[] tokPrice = new int[ApexPack.TOKENS];
	private final int[] lenPrice = new int[ApexPack.LEN_SYMBOLS];
	private final int[] offPrice = new int[ApexPack.OFF_SYMBOLS];

	private ApexPackEncoder(byte[] in, int off, int len, Options opt) {
		this.in = off == 0 && len == in.length ? in : Arrays.copyOfRange(in, off, off + len);
		this.n = len;
		this.opt = opt;
		this.seqPenalty = opt.sequencePenalty * SCALE;
	}

	public static byte[] compress(byte[] data) {
		return compress(data, 0, data.length, new Options());
	}

	public static byte[] compress(byte[] data, int off, int len, Options opt) {
		ApexPackEncoder e = new ApexPackEncoder(data, off, len, opt);
		e.staticPrices();
		for (int pass = 0; pass < opt.passes; pass++) {
			if (pass > 0) e.learnPrices();
			e.parse();
		}
		byte[] r = e.write();
		return r;
	}

	private static int bits(long count, long total) {
		if (count <= 0) return (PackHuffman.MAX_LEN + 1) * SCALE;
		return (int) Math.min((PackHuffman.MAX_LEN + 1) * SCALE, Math.max(SCALE, Math.round(-Math.log((double) count / total) / Math.log(2) * SCALE)));
	}

	private void staticPrices() {
		long[] h = new long[256];
		for (int i = 0; i < n; i++) h[in[i] & 0xFF]++;
		for (int b = 0; b < 256; b++) litPrice[b] = bits(h[b] + 1, n + 256);
		for (int t = 0; t < ApexPack.TOKENS; t++) {
			int ll = t / ApexPack.ML_CODES, ml = t % ApexPack.ML_CODES;
			tokPrice[t] = (5 + ll / 2 + ml / 4) * SCALE;
		}
		for (int s = 0; s < lenPrice.length; s++) lenPrice[s] = (3 + s / 4) * SCALE;
		offPrice[0] = SCALE; offPrice[1] = 3 * SCALE; offPrice[2] = 4 * SCALE;
		for (int s = ApexPack.REPS; s < offPrice.length; s++) offPrice[s] = 6 * SCALE;
	}

	private void learnPrices() {
		long[] lit = new long[256], tok = new long[ApexPack.TOKENS], len = new long[ApexPack.LEN_SYMBOLS], off = new long[ApexPack.OFF_SYMBOLS];
		countAll(lit, tok, len, off);
		price(lit, litPrice);
		price(tok, tokPrice);
		price(len, lenPrice);
		price(off, offPrice);
	}

	private static void price(long[] freq, int[] out) {
		long total = 0;
		for (long f : freq) total += f;
		int[] l = PackHuffman.lengths(freq, PackHuffman.MAX_LEN);
		for (int s = 0; s < out.length; s++) {
			if (freq[s] == 0) out[s] = (PackHuffman.MAX_LEN + 1) * SCALE;
			else if (l[s] == 0) out[s] = SCALE / 2;
			else {

				int ideal = bits(freq[s], total);
				out[s] = (l[s] * SCALE + ideal) / 2;
			}
		}
	}

	private int lenCost(int v) {
		int s = ApexPack.bucket(v);
		return lenPrice[s] + ApexPack.BUCKET_EXTRA[s] * SCALE;
	}

	private int matchCost(int litRun, int ml, int offSym, int offExtra) {
		int llc = ApexPack.llCode(litRun);
		int mv = ml - ApexPack.MIN_MATCH;
		int mlc = ApexPack.mlCode(mv);
		int c = seqPenalty + tokPrice[llc * ApexPack.ML_CODES + mlc] + offPrice[offSym]
			+ (offExtra + ApexPack.LL_EXTRA[llc] + ApexPack.ML_EXTRA[mlc]) * SCALE;
		if (llc == ApexPack.LL_ESCAPE) c += lenCost(litRun - ApexPack.LL_ESCAPE_BASE);
		return c;
	}

	private int[] head, chain;

	private int hash(int p) {
		return ((int) INT.get(in, p) * 0x9E3779B1) >>> (32 - opt.hashBits);
	}

	private int inserted;

	private void insertUpTo(int p) {
		int limit = Math.min(p, n - 4);
		for (int i = inserted; i < limit; i++) {
			int h = hash(i);
			chain[i] = head[h];
			head[h] = i;
		}
		if (limit > inserted) inserted = limit;
	}

	private int matchLen(int a, int b, int limit) {
		int len = 0;
		while (len + 8 <= limit) {
			long x = (long) LONG.get(in, a + len) ^ (long) LONG.get(in, b + len);
			if (x != 0) return len + (Long.numberOfTrailingZeros(x) >>> 3);
			len += 8;
		}
		while (len < limit && in[a + len] == in[b + len]) len++;
		return len;
	}

	private final int[] candLen = new int[64], candDist = new int[64];

	private int find(int p) {
		insertUpTo(p);
		int count = 0;
		if (p + 4 > n) return 0;
		int limit = Math.min(n - p, MAX_MATCH);
		int best = ApexPack.MIN_MATCH;
		int cur = head[hash(p)];
		int depth = opt.chainDepth;
		int lowest = p - opt.window;
		while (cur >= 0 && cur >= lowest && depth-- > 0) {

			if (in[cur + best] == in[p + best] || best >= limit) {
				int l = matchLen(p, cur, limit);
				if (l > best || (l >= 4 && count == 0 && l >= best)) {
					if (l > best || count == 0) {
						if (count == candLen.length) count--;
						candLen[count] = l; candDist[count] = p - cur; count++;
						best = l;
						if (l >= opt.niceLength || l >= limit) break;
					}
				}
			}
			cur = chain[cur];
		}
		return count;
	}

	private int[] price, fromLen, fromDist, run, rep0, rep1, rep2;

	private void parse() {
		seqs = 0;
		pending = 0;
		head = new int[1 << opt.hashBits];
		Arrays.fill(head, -1);
		chain = new int[Math.max(n, 1)];
		inserted = 0;
		int cap = SEGMENT + opt.niceLength + 2;
		price = new int[cap]; fromLen = new int[cap]; fromDist = new int[cap]; run = new int[cap];
		rep0 = new int[cap]; rep1 = new int[cap]; rep2 = new int[cap];
		int r0 = 1, r1 = 2, r2 = 3;
		int pos = 0;
		while (pos < n) {

			int segMax = Math.min(SEGMENT, n - pos);
			Arrays.fill(price, 0, Math.min(cap, segMax + opt.niceLength + 2), Integer.MAX_VALUE);
			price[0] = 0; run[0] = pending; rep0[0] = r0; rep1[0] = r1; rep2[0] = r2;
			int cut = segMax;
			int forcedLen = 0, forcedDist = 0;
			for (int j = 0; j < segMax; j++) {
				int pj = price[j];
				int p = pos + j;

				int lp = pj + litPrice[in[p] & 0xFF];
				if (lp < price[j + 1]) {
					price[j + 1] = lp; fromLen[j + 1] = 0; run[j + 1] = run[j] + 1;
					rep0[j + 1] = rep0[j]; rep1[j + 1] = rep1[j]; rep2[j + 1] = rep2[j];
				}
				int limit = Math.min(n - p, MAX_MATCH);
				if (limit < ApexPack.MIN_MATCH) continue;
				int lr = run[j];

				int bestRep = 0, bestRepDist = 0;
				for (int k = 0; k < ApexPack.REPS; k++) {
					int d = k == 0 ? rep0[j] : k == 1 ? rep1[j] : rep2[j];
					if (d > p || d > opt.window) continue;
					int l = matchLen(p, p - d, limit);
					if (l < ApexPack.MIN_MATCH) continue;
					if (l > bestRep) { bestRep = l; bestRepDist = d; }
					int top = Math.min(l, opt.niceLength);
					for (int m = ApexPack.MIN_MATCH; m <= top; m++) relax(j, m, d, k, 0, pj + matchCost(lr, m, k, 0));
				}
				if (bestRep >= opt.niceLength) { cut = j; forcedLen = bestRep; forcedDist = bestRepDist; break; }
				int c = find(p);
				if (c > 0) {
					int longest = candLen[c - 1];
					if (longest >= opt.niceLength) { cut = j; forcedLen = longest; forcedDist = candDist[c - 1]; break; }
					int prevLen = ApexPack.MIN_MATCH - 1;
					for (int i = 0; i < c; i++) {
						int d = candDist[i];
						int v = d - 1;
						int bs = ApexPack.bucket(v);
						int sym = ApexPack.REPS + bs;
						int extra = ApexPack.BUCKET_EXTRA[bs];
						for (int m = prevLen + 1; m <= candLen[i]; m++) relax(j, m, d, -1, 0, pj + matchCost(lr, m, sym, extra));
						prevLen = candLen[i];
					}
				}
			}

			int[] reps = emitPath(pos, cut);
			r0 = reps[0]; r1 = reps[1]; r2 = reps[2];
			pos += cut;
			if (forcedLen > 0) {
				int d = forcedDist;
				addSeq(pending, forcedLen, d);
				pending = 0;
				if (d == r0) { }
				else if (d == r1) { r1 = r0; r0 = d; }
				else if (d == r2) { r2 = r1; r1 = r0; r0 = d; }
				else { r2 = r1; r1 = r0; r0 = d; }
				pos += forcedLen;
			}
		}
		head = null; chain = null;
	}

	private void relax(int j, int m, int d, int repIndex, int unused, int cost) {
		int t = j + m;
		if (cost >= price[t]) return;
		price[t] = cost; fromLen[t] = m; fromDist[t] = d; run[t] = 0;
		int a = rep0[j], b = rep1[j], c = rep2[j];
		if (d == a) { rep0[t] = a; rep1[t] = b; rep2[t] = c; }
		else if (d == b) { rep0[t] = b; rep1[t] = a; rep2[t] = c; }
		else if (d == c) { rep0[t] = c; rep1[t] = a; rep2[t] = b; }
		else { rep0[t] = d; rep1[t] = a; rep2[t] = b; }
	}

	private int[] stackLen = new int[SEGMENT + 1], stackDist = new int[SEGMENT + 1];

	private int[] emitPath(int pos, int cut) {
		int k = 0;
		int j = cut;
		while (j > 0) {
			int m = fromLen[j];
			if (m == 0) { stackLen[k] = 0; j -= 1; }
			else { stackLen[k] = m; stackDist[k] = fromDist[j]; j -= m; }
			k++;
		}
		for (int i = k - 1; i >= 0; i--) {
			if (stackLen[i] == 0) pending++;
			else { addSeq(pending, stackLen[i], stackDist[i]); pending = 0; }
		}
		return new int[]{rep0[cut], rep1[cut], rep2[cut]};
	}

	private void addSeq(int ll, int ml, int d) {
		if (seqs == sLL.length) {
			int c = seqs * 2;
			sLL = Arrays.copyOf(sLL, c); sML = Arrays.copyOf(sML, c); sDist = Arrays.copyOf(sDist, c);
		}
		sLL[seqs] = ll; sML[seqs] = ml; sDist[seqs] = d;
		seqs++;
	}

	private int[] offSyms;

	private void resolveOffsets() {
		offSyms = new int[seqs];
		int a = 1, b = 2, c = 3;
		for (int i = 0; i < seqs; i++) {
			int d = sDist[i];
			if (d == a) offSyms[i] = 0;
			else if (d == b) { offSyms[i] = 1; b = a; a = d; }
			else if (d == c) { offSyms[i] = 2; c = b; b = a; a = d; }
			else { offSyms[i] = ApexPack.REPS + ApexPack.bucket(d - 1); c = b; b = a; a = d; }
		}
	}

	private static int token(int ll, int ml) {
		return ApexPack.llCode(ll) * ApexPack.ML_CODES + ApexPack.mlCode(ml - ApexPack.MIN_MATCH);
	}

	private void countAll(long[] lit, long[] tok, long[] len, long[] off) {
		resolveOffsets();
		for (int i = 0; i < seqs; i++) {
			int ll = sLL[i];
			tok[token(ll, sML[i])]++;
			if (ll >= ApexPack.LL_ESCAPE_BASE) len[ApexPack.bucket(ll - ApexPack.LL_ESCAPE_BASE)]++;
			off[offSyms[i]]++;
		}
		long q = 0;
		for (int i = 0; i < seqs; i++) {
			for (int k = 0; k < sLL[i]; k++) lit[in[(int) (q + k)] & 0xFF]++;
			q += sLL[i] + sML[i];
		}
		for (long k = q; k < n; k++) lit[in[(int) k] & 0xFF]++;
	}

	private int repA = 1, repB = 2, repC = 3;

	private byte[] write() {
		repA = 1; repB = 2; repC = 3;
		PackHuffman.ByteSink out = new PackHuffman.ByteSink();
		out.put(ApexPack.MAGIC, 0, 4);
		out.put(ApexPack.VERSION);
		out.put(opt.checksum ? ApexPack.FLAG_CHECKSUM : 0);
		out.varint(n);

		int i = 0;
		int q = 0;
		int carry = 0;
		while (q < n || i < seqs) {
			int start = q;
			int first = i;
			long raw = 0;
			int lits = 0;
			int firstCarry = carry;

			while (i < seqs) {
				int ll = sLL[i] - (i == first ? carry : 0);
				long add = ll + sML[i];
				if (raw > 0 && raw + add > opt.blockSize) break;
				if (add > ApexPack.MAX_BLOCK / 2) break;
				raw += add; lits += ll; i++;
			}
			int trailing = 0;
			boolean split = false;
			if (i < seqs && i == first) {

				int ll = sLL[i] - carry;
				trailing = Math.min(ll, opt.blockSize);
				split = true;
			} else if (i == seqs) {
				trailing = n - (start + (int) raw);
			}
			long blockRaw = raw + trailing;
			int blockLits = lits + trailing;
			writeBlock(out, start, first, i, firstCarry, blockLits, (int) blockRaw);
			q = start + (int) blockRaw;
			if (split) carry += trailing; else carry = 0;
			if (i == seqs && q >= n) break;
		}
		out.put(0);
		if (opt.checksum) {
			long h = XxHash64.hash(in, 0, n);
			for (int k = 0; k < 8; k++) out.put((int) (h >>> (8 * k)));
		}
		return out.toArray();
	}

	private void writeBlock(PackHuffman.ByteSink out, int start, int first, int end, int carry, int litCount, int raw) {
		if (raw == 0) return;

		byte[] lit = new byte[litCount];
		int lp = 0, q = start;
		for (int i = first; i < end; i++) {
			int ll = sLL[i] - (i == first ? carry : 0);
			System.arraycopy(in, q, lit, lp, ll);
			lp += ll; q += ll + sML[i];
		}
		System.arraycopy(in, q, lit, lp, litCount - lp);

		int ra = repA, rb = repB, rc = repC;
		int[] offSyms = new int[end - first];
		for (int i = first; i < end; i++) {
			int d = sDist[i], k = i - first;
			if (d == ra) offSyms[k] = 0;
			else if (d == rb) { offSyms[k] = 1; rb = ra; ra = d; }
			else if (d == rc) { offSyms[k] = 2; rc = rb; rb = ra; ra = d; }
			else { offSyms[k] = ApexPack.REPS + ApexPack.bucket(d - 1); rc = rb; rb = ra; ra = d; }
		}
		long[] tok = new long[ApexPack.TOKENS], len = new long[ApexPack.LEN_SYMBOLS], off = new long[ApexPack.OFF_SYMBOLS];
		for (int i = first; i < end; i++) {
			int ll = sLL[i] - (i == first ? carry : 0);
			tok[token(ll, sML[i])]++;
			if (ll >= ApexPack.LL_ESCAPE_BASE) len[ApexPack.bucket(ll - ApexPack.LL_ESCAPE_BASE)]++;
			off[offSyms[i - first]]++;
		}
		int[] tokL = PackHuffman.lengths(tok, PackHuffman.MAX_LEN);
		int[] lenL = PackHuffman.lengths(len, PackHuffman.MAX_LEN);
		int[] offL = PackHuffman.lengths(off, PackHuffman.MAX_LEN);

		PackHuffman.ByteSink b = new PackHuffman.ByteSink();
		b.put(2);
		b.varint(raw);
		b.varint(litCount);
		b.varint(end - first);
		int q4 = litCount >>> 2;
		int[] ctx = new int[256];
		int contexts = literalContexts(lit, q4, ctx);
		b.put(contexts);
		if (contexts > 1) for (int i = 0; i < 256; i += 2) b.put(ctx[i] | ctx[i + 1] << 4);
		long[][] cf = new long[contexts][256];
		for (int k = 0; k < 4; k++) {
			int from = k * q4, to = k == 3 ? litCount : (k + 1) * q4;
			int prev = 0;
			for (int j = from; j < to; j++) { cf[ctx[prev]][lit[j] & 0xFF]++; prev = lit[j] & 0xFF; }
		}
		int[][] litL = new int[contexts][], litC = new int[contexts][];
		for (int k = 0; k < contexts; k++) {
			litL[k] = PackHuffman.lengths(cf[k], PackHuffman.MAX_LEN);
			litC[k] = PackHuffman.codes(litL[k]);
			PackHuffman.writeTable(b, litL[k], cf[k]);
		}
		byte[][] parts = new byte[4][];
		for (int k = 0; k < 4; k++) {
			int from = k * q4, to = k == 3 ? litCount : (k + 1) * q4;
			PackHuffman.BitSink ls = new PackHuffman.BitSink();
			int prev = 0;
			for (int j = from; j < to; j++) {
				int x = lit[j] & 0xFF, c = ctx[prev];
				ls.put(litC[c][x], litL[c][x]);
				prev = x;
			}
			parts[k] = ls.finish();
		}
		b.varint(parts[0].length);
		b.varint(parts[1].length);
		b.varint(parts[2].length);
		b.varint(parts[0].length + parts[1].length + parts[2].length + parts[3].length);
		for (byte[] part : parts) b.put(part, 0, part.length);
		PackHuffman.writeTable(b, tokL, tok);
		PackHuffman.writeTable(b, lenL, len);
		PackHuffman.writeTable(b, offL, off);
		int[] tokC = PackHuffman.codes(tokL), lenC = PackHuffman.codes(lenL), offC = PackHuffman.codes(offL);
		PackHuffman.BitSink ss = new PackHuffman.BitSink(), os_ = new PackHuffman.BitSink();
		for (int i = first; i < end; i++) {
			int ll = sLL[i] - (i == first ? carry : 0), mv = sML[i] - ApexPack.MIN_MATCH;
			int llc = ApexPack.llCode(ll), mlc = ApexPack.mlCode(mv);
			int t = llc * ApexPack.ML_CODES + mlc;
			ss.put(tokC[t], tokL[t]);
			if (llc == ApexPack.LL_ESCAPE) putLen(ss, ll - ApexPack.LL_ESCAPE_BASE, lenC, lenL);
			else ss.put(ll - ApexPack.LL_BASE[llc], ApexPack.LL_EXTRA[llc]);
			ss.put(mv - ApexPack.ML_BASE[mlc], ApexPack.ML_EXTRA[mlc]);
			int os = offSyms[i - first];
			os_.put(offC[os], offL[os]);
			if (os >= ApexPack.REPS) {
				int bs = os - ApexPack.REPS;
				int v = sDist[i] - 1;
				os_.put(v - ApexPack.BUCKET_BASE[bs], ApexPack.BUCKET_EXTRA[bs]);
			}
		}
		byte[] ssb = ss.finish(), osb = os_.finish();
		b.varint(ssb.length);
		b.varint(ssb.length + osb.length);
		b.put(ssb, 0, ssb.length);
		b.put(osb, 0, osb.length);
		if (b.size >= raw + 8) {

			out.put(1);
			out.varint(raw);
			out.put(in, start, raw);
		} else {
			out.put(b.buf, 0, b.size);
			repA = ra; repB = rb; repC = rc;
		}
	}

	private static int literalContexts(byte[] lit, int q4, int[] map) {
		int n = lit.length;
		Arrays.fill(map, 0);
		if (n < 4096) return 1;
		long[][] f = new long[256][256];
		long[] tot = new long[256];
		for (int k = 0; k < 4; k++) {
			int from = k * q4, to = k == 3 ? n : (k + 1) * q4;
			int prev = 0;
			for (int j = from; j < to; j++) { int x = lit[j] & 0xFF; f[prev][x]++; tot[prev]++; prev = x; }
		}
		Integer[] order = new Integer[256];
		for (int i = 0; i < 256; i++) order[i] = i;
		Arrays.sort(order, (x, y) -> Long.compare(tot[y], tot[x]));
		int start = 0;
		while (start < 256 && tot[order[start]] > 0) start++;
		int seeds = Math.min(start, 32);

		int[] owner = new int[256];
		Arrays.fill(owner, -1);
		long[][] g = new long[seeds][];
		for (int i = 0; i < seeds; i++) { g[i] = f[order[i]].clone(); owner[order[i]] = i; }
		for (int i = seeds; i < start; i++) {
			int v = order[i];
			int last = seeds - 1;
			for (int x = 0; x < 256; x++) g[last][x] += f[v][x];
			owner[v] = last;
		}
		int live = seeds;
		boolean[] dead = new boolean[seeds];
		double[] cost = new double[seeds];
		for (int i = 0; i < seeds; i++) cost[i] = bitsOf(g[i]);
		final double tableBits = 8 * 90;
		while (live > 1) {
			double best = Double.MAX_VALUE;
			int ba = -1, bb = -1;
			long[] merged = new long[256];
			for (int a = 0; a < seeds; a++) {
				if (dead[a]) continue;
				for (int bI = a + 1; bI < seeds; bI++) {
					if (dead[bI]) continue;
					for (int x = 0; x < 256; x++) merged[x] = g[a][x] + g[bI][x];
					double d = bitsOf(merged) - cost[a] - cost[bI];
					if (d < best) { best = d; ba = a; bb = bI; }
				}
			}
			double saving = tableBits + (live == 2 ? 128 * 8 : 0);
			if (live <= ApexPack.LIT_CONTEXTS && best > saving) break;
			for (int x = 0; x < 256; x++) g[ba][x] += g[bb][x];
			cost[ba] = bitsOf(g[ba]);
			dead[bb] = true;
			for (int v = 0; v < 256; v++) if (owner[v] == bb) owner[v] = ba;
			live--;
		}
		int[] index = new int[seeds];
		int k = 0;
		for (int i = 0; i < seeds; i++) if (!dead[i]) index[i] = k++;
		for (int v = 0; v < 256; v++) map[v] = owner[v] < 0 ? 0 : index[owner[v]];
		if (k == 1) Arrays.fill(map, 0);
		return k;
	}

	private static double bitsOf(long[] h) {
		long t = 0;
		double s = 0;
		for (long v : h) if (v > 0) { t += v; s -= v * Math.log(v); }
		if (t == 0) return 0;
		return (s + t * Math.log(t)) / Math.log(2);
	}

	private static void putLen(PackHuffman.BitSink s, int v, int[] codes, int[] lens) {
		int bs = ApexPack.bucket(v);
		s.put(codes[bs], lens[bs]);
		s.put(v - ApexPack.BUCKET_BASE[bs], ApexPack.BUCKET_EXTRA[bs]);
	}
}
