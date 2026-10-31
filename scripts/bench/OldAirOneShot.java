// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.thirdparty.zstd;

import static sun.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;

public final class OldAirOneShot {
	public static int decode(byte[] src, byte[] dst) {
		return new ZstdFrameDecompressor().decompress(src, ARRAY_BYTE_BASE_OFFSET, ARRAY_BYTE_BASE_OFFSET + src.length,
			dst, ARRAY_BYTE_BASE_OFFSET, ARRAY_BYTE_BASE_OFFSET + dst.length);
	}
}
