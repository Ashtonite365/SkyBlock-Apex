// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compress;

final class ZstdFse {
	int log;
	int[] cells;

	ZstdFse(int maxLog) {
		cells = new int[1 << maxLog];
	}

	static int symbol(int cell) { return cell >>> 24; }

	void single(int symbol) {
		log = 0;
		cells[0] = symbol << 24;
	}

	void build(short[] dist, int symbols, int tableLog) throws CompressFormatException {
		int size = 1 << tableLog;
		if (cells.length < size) cells = new int[size];
		log = tableLog;
		int[] next = new int[symbols];

		int top = size - 1;
		for (int s = 0; s < symbols; s++) {
			if (dist[s] == -1) {
				cells[top--] = s << 24;
				next[s] = 1;
			} else {
				next[s] = dist[s];
			}
		}
		int filled = size - 1 - top;

		int step = (size >>> 1) + (size >>> 3) + 3;
		int mask = size - 1;
		int at = 0;
		for (int s = 0; s < symbols; s++) {
			for (int k = dist[s]; k > 0; k--) {
				cells[at] = s << 24;
				filled++;
				do { at = (at + step) & mask; } while (at > top);
			}
		}
		if (at != 0 || filled != size) throw new CompressFormatException("zstd: bad FSE distribution");

		for (int st = 0; st < size; st++) {
			int s = cells[st] >>> 24;
			int x = next[s]++;
			int bits = tableLog - (31 - Integer.numberOfLeadingZeros(x));
			int base = (x << bits) - size;
			cells[st] = (s << 24) | (bits << 16) | base;
		}
	}

	int read(byte[] src, int off, int end, int maxLog, int maxSymbols, short[] dist) throws CompressFormatException {

		long acc = 0;
		int accBits = 0;
		int pos = off;
		int usedBits = 0;

		while (accBits <= 56 && pos < end) { acc |= (src[pos++] & 0xFFL) << accBits; accBits += 8; }
		int tableLog = (int) (acc & 15) + 5;
		acc >>>= 4; accBits -= 4; usedBits += 4;
		if (tableLog > maxLog) throw new CompressFormatException("zstd: FSE accuracy log too large");
		int size = 1 << tableLog;
		int left = size + 1;
		int width = tableLog + 1;
		int s = 0;
		while (left > 1) {
			if (s >= maxSymbols) throw new CompressFormatException("zstd: FSE description has too many symbols");
			if (accBits < 32) { while (accBits <= 56 && pos < end) { acc |= (src[pos++] & 0xFFL) << accBits; accBits += 8; } if (accBits < 32) accBits = 32; }

			int span = 1 << (width - 1);
			int shortCodes = (2 * span - 1) - left;
			int low = (int) (acc & (span - 1));
			int v;
			if (low < shortCodes) {
				v = low;
				acc >>>= width - 1; accBits -= width - 1; usedBits += width - 1;
			} else {
				v = (int) (acc & (2 * span - 1));
				if (v >= span) v -= shortCodes;
				acc >>>= width; accBits -= width; usedBits += width;
			}
			int count = v - 1;
			dist[s++] = (short) count;
			left -= count == -1 ? 1 : count;
			if (count == 0) {

				while (true) {
					if (accBits < 32) { while (accBits <= 56 && pos < end) { acc |= (src[pos++] & 0xFFL) << accBits; accBits += 8; } if (accBits < 32) accBits = 32; }
					int run = (int) (acc & 3);
					acc >>>= 2; accBits -= 2; usedBits += 2;
					if (s + run > maxSymbols) throw new CompressFormatException("zstd: FSE zero run past the alphabet");
					for (int i = 0; i < run; i++) dist[s++] = 0;
					if (run != 3) break;
				}
			}
			if (left < 1) throw new CompressFormatException("zstd: FSE counts overflow the table");
			while (left < span && width > 1) { width--; span >>= 1; }
		}
		int bytes = (usedBits + 7) >>> 3;
		if (bytes > end - off) throw new CompressFormatException("zstd: truncated FSE description");
		build(dist, s, tableLog);
		return bytes;
	}
}
