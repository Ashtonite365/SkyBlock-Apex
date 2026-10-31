// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compress;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

final class BackBits {
	static final VarHandle LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

	private final byte[] src;
	private final int start;
	long word;
	int used;
	int at;

	BackBits(byte[] src, int start, int end) throws CompressFormatException {
		this.src = src;
		this.start = start;
		int len = end - start;
		if (len <= 0 || src[end - 1] == 0) throw new CompressFormatException("zstd: bad bit stream end");
		if (len >= 8) {
			at = end - 8;
			word = (long) LONG.get(src, at);
		} else {
			at = start - (8 - len);
			long w = 0;
			for (int i = end - 1; i >= start; i--) w = (w << 8) | (src[i] & 0xFFL);
			word = w << (8 * (8 - len));
		}
		used = Long.numberOfLeadingZeros(word) + 1;
	}

	static long peek(long word, int used, int n) {
		return (word << used) >>> 1 >>> (63 - n);
	}

	long read(int n) {
		if (used + n > 64) refill();
		long v = (word << used) >>> 1 >>> (63 - n);
		used += n;
		return v;
	}

	void refill() {
		if (at >= start + 8) {
			at -= used >>> 3;
			used &= 7;
			word = (long) LONG.get(src, at);
		} else if (at > start) {
			int back = Math.min(used >>> 3, at - start);
			at -= back;
			used -= back << 3;
			word = (long) LONG.get(src, at);
		}
	}

	boolean overrun() {
		if (used > 56) refill();
		return at <= start && used > ZstdBlocks.floorBits(at, start);
	}

	boolean exhausted() {
		refill();
		return at <= start && used == ZstdBlocks.floorBits(at, start);
	}
}
