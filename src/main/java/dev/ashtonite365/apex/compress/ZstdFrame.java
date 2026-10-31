// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compress;

final class ZstdFrame {
	static final int MAGIC = 0xFD2FB528;
	static final int SKIPPABLE_MASK = 0xFFFFFFF0;
	static final int SKIPPABLE = 0x184D2A50;

	static final int MAX_HEADER = 14;

	int headerSize;
	long windowSize;
	long contentSize = -1;
	boolean checksum;
	long dictionaryId;

	int blockMax() {
		return (int) Math.min(windowSize, ZstdBlocks.BLOCK_MAX);
	}

	static int headerSize(int descriptor) {
		int fcsFlag = descriptor >>> 6;
		boolean single = (descriptor & 0x20) != 0;
		int did = descriptor & 3;
		return 1 + (single ? 0 : 1) + (did == 3 ? 4 : did) + (fcsFlag == 0 ? (single ? 1 : 0) : 1 << fcsFlag);
	}

	static ZstdFrame parse(byte[] src, int off, long maxWindow) throws CompressFormatException {
		ZstdFrame f = new ZstdFrame();
		int d = src[off] & 0xFF;
		if ((d & 0x08) != 0) throw new CompressFormatException("zstd: reserved frame header bit set");
		f.headerSize = headerSize(d);
		f.checksum = (d & 0x04) != 0;
		boolean single = (d & 0x20) != 0;
		int p = off + 1;
		if (!single) {
			int w = src[p++] & 0xFF;
			int log = 10 + (w >>> 3);
			long base = 1L << log;
			f.windowSize = base + (base >>> 3) * (w & 7);
		}
		int didFlag = d & 3;
		int didLen = didFlag == 3 ? 4 : didFlag;
		long did = 0;
		for (int i = didLen - 1; i >= 0; i--) did = (did << 8) | (src[p + i] & 0xFF);
		p += didLen;
		f.dictionaryId = did;
		int fcsFlag = d >>> 6;
		int fcsLen = fcsFlag == 0 ? (single ? 1 : 0) : 1 << fcsFlag;
		if (fcsLen > 0) {
			long v = 0;
			for (int i = fcsLen - 1; i >= 0; i--) v = (v << 8) | (src[p + i] & 0xFF);
			if (fcsLen == 2) v += 256;
			if (v < 0) throw new CompressFormatException("zstd: content size too large");
			f.contentSize = v;
		}
		if (single) f.windowSize = f.contentSize;
		if (did != 0) throw new CompressFormatException("zstd: frames that need a dictionary are not supported");
		if (f.windowSize > maxWindow) throw new CompressFormatException("zstd: window of " + f.windowSize + " bytes is over the limit of " + maxWindow);
		return f;
	}
}
