// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compress;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

public final class XxHash64 {
	private static final long K1 = 0x9E3779B185EBCA87L;
	private static final long K2 = 0xC2B2AE3D27D4EB4FL;
	private static final long K3 = 0x165667B19E3779F9L;
	private static final long K4 = 0x85EBCA77C2B2AE63L;
	private static final long K5 = 0x27D4EB2F165667C5L;
	private static final VarHandle LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
	private static final VarHandle INT = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

	private final long seed;
	private long a, b, c, d;
	private final byte[] pending = new byte[32];
	private int pendingLen;
	private long total;

	public XxHash64() { this(0); }

	public XxHash64(long seed) {
		this.seed = seed;
		reset();
	}

	public void reset() {
		a = seed + K1 + K2;
		b = seed + K2;
		c = seed;
		d = seed - K1;
		pendingLen = 0;
		total = 0;
	}

	private static long mix(long lane, long input) {
		return Long.rotateLeft(lane + input * K2, 31) * K1;
	}

	private static long fold(long h, long lane) {
		return (h ^ mix(0, lane)) * K1 + K4;
	}

	public void update(byte[] data, int off, int len) {
		total += len;
		if (pendingLen > 0) {
			int take = Math.min(32 - pendingLen, len);
			System.arraycopy(data, off, pending, pendingLen, take);
			pendingLen += take; off += take; len -= take;
			if (pendingLen < 32) return;
			stripe(pending, 0);
			pendingLen = 0;
		}
		int end = off + len;
		long la = a, lb = b, lc = c, ld = d;
		while (end - off >= 32) {
			la = mix(la, (long) LONG.get(data, off));
			lb = mix(lb, (long) LONG.get(data, off + 8));
			lc = mix(lc, (long) LONG.get(data, off + 16));
			ld = mix(ld, (long) LONG.get(data, off + 24));
			off += 32;
		}
		a = la; b = lb; c = lc; d = ld;
		if (off < end) {
			System.arraycopy(data, off, pending, 0, end - off);
			pendingLen = end - off;
		}
	}

	private void stripe(byte[] s, int o) {
		a = mix(a, (long) LONG.get(s, o));
		b = mix(b, (long) LONG.get(s, o + 8));
		c = mix(c, (long) LONG.get(s, o + 16));
		d = mix(d, (long) LONG.get(s, o + 24));
	}

	public long digest() {
		long h;
		if (total >= 32) {
			h = Long.rotateLeft(a, 1) + Long.rotateLeft(b, 7) + Long.rotateLeft(c, 12) + Long.rotateLeft(d, 18);
			h = fold(h, a);
			h = fold(h, b);
			h = fold(h, c);
			h = fold(h, d);
		} else {
			h = seed + K5;
		}
		h += total;
		int i = 0;
		for (; i + 8 <= pendingLen; i += 8)
			h = Long.rotateLeft(h ^ mix(0, (long) LONG.get(pending, i)), 27) * K1 + K4;
		if (i + 4 <= pendingLen) {
			h = Long.rotateLeft(h ^ (((int) INT.get(pending, i)) & 0xFFFFFFFFL) * K1, 23) * K2 + K3;
			i += 4;
		}
		for (; i < pendingLen; i++)
			h = Long.rotateLeft(h ^ (pending[i] & 0xFFL) * K5, 11) * K1;
		h ^= h >>> 33;
		h *= K2;
		h ^= h >>> 29;
		h *= K3;
		h ^= h >>> 32;
		return h;
	}

	public static long hash(byte[] data, int off, int len) {
		XxHash64 x = new XxHash64();
		x.update(data, off, len);
		return x.digest();
	}
}
