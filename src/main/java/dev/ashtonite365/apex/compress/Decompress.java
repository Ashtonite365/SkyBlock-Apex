// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compress;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public final class Decompress {
	public enum Format { APEX_PACK, ZSTD, UNKNOWN }

	private Decompress() {}

	public static Format detect(byte[] head, int off, int len) {
		if (ApexPack.isApexPack(head, off, len)) return Format.APEX_PACK;
		if (len >= 4) {
			int magic = (head[off] & 0xFF) | (head[off + 1] & 0xFF) << 8 | (head[off + 2] & 0xFF) << 16 | (head[off + 3] & 0xFF) << 24;
			if (magic == 0xFD2FB528 || (magic & 0xFFFFFFF0) == 0x184D2A50) return Format.ZSTD;
		}
		return Format.UNKNOWN;
	}

	public static InputStream open(InputStream in, int maxCompressed, int maxContent) throws IOException {
		InputStream b = in.markSupported() ? in : new BufferedInputStream(in);
		b.mark(4);
		byte[] head = b.readNBytes(4);
		b.reset();
		return switch (detect(head, 0, head.length)) {
			case APEX_PACK -> {
				byte[] packed = b.readNBytes(maxCompressed);
				if (b.read() >= 0) throw new CompressFormatException("apex pack: larger than " + maxCompressed + " bytes");
				yield new ByteArrayInputStream(ApexPack.decompress(packed, 0, packed.length, maxContent));
			}
			case ZSTD -> new ZstdInputStream(b);
			case UNKNOWN -> throw new CompressFormatException("unknown compressed format (first bytes " + Arrays.toString(head) + ")");
		};
	}

	public static InputStream open(InputStream in) throws IOException {
		return open(in, 1 << 28, ApexPack.DEFAULT_MAX_OUTPUT);
	}

	public static byte[] decode(byte[] data) throws IOException {
		return switch (detect(data, 0, data.length)) {
			case APEX_PACK -> ApexPack.decompress(data);
			case ZSTD -> Zstd.decompress(data);
			case UNKNOWN -> throw new CompressFormatException("unknown compressed format");
		};
	}
}
