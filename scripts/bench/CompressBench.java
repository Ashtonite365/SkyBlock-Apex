// SPDX-License-Identifier: GPL-3.0-or-later

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class CompressBench {
	public static void main(String[] a) throws Exception {
		String codec = a[0];
		byte[] src = Files.readAllBytes(Path.of(a[1]));
		byte[] raw = Files.readAllBytes(Path.of(a[2]));
		int warm = Integer.parseInt(a[3]), iters = Integer.parseInt(a[4]);
		byte[] dst = new byte[raw.length + 64];
		byte[] buf = new byte[1 << 16];
		Runner r = switch (codec) {
			case "ours-zstd" -> () -> dev.ashtonite365.apex.compress.Zstd.decompress(src, 0, src.length, dst, 0, dst.length);
			case "ours-pack" -> () -> dev.ashtonite365.apex.compress.ApexPack.decompress(src, 0, src.length, dst, 0, dst.length);
			case "air-zstd" -> () -> dev.ashtonite365.apex.thirdparty.zstd.OldAirOneShot.decode(src, dst);
			case "libzstd" -> {
				var d = new io.airlift.compress.v3.zstd.ZstdNativeDecompressor();
				yield () -> d.decompress(src, 0, src.length, dst, 0, dst.length);
			}
			case "ours-zstd-stream" -> () -> drain(new dev.ashtonite365.apex.compress.ZstdInputStream(new ByteArrayInputStream(src)), buf);
			case "ours-pack-stream" -> () -> drain(dev.ashtonite365.apex.compress.Decompress.open(new ByteArrayInputStream(src)), buf);
			case "air-zstd-stream" -> () -> drain(new dev.ashtonite365.apex.thirdparty.zstd.ZstdInputStream(new ByteArrayInputStream(src)), buf);
			default -> throw new IllegalArgumentException(codec);
		};

		int n = r.run();
		if (n != raw.length) throw new IllegalStateException(codec + ": " + n + " bytes, want " + raw.length);
		if (!codec.endsWith("-stream") && !Arrays.equals(dst, 0, n, raw, 0, n)) throw new IllegalStateException(codec + ": wrong output");
		for (int i = 0; i < warm; i++) r.run();
		long[] t = new long[iters];
		for (int i = 0; i < iters; i++) {
			long s = System.nanoTime();
			r.run();
			t[i] = System.nanoTime() - s;
		}
		Arrays.sort(t);
		System.out.printf("%s median %.2f ms min %.2f ms max %.2f ms (%d iters)%n", codec, t[iters / 2] / 1e6, t[0] / 1e6, t[iters - 1] / 1e6, iters);
	}

	interface Runner { int run() throws Exception; }

	static int drain(InputStream in, byte[] buf) throws Exception {
		int n = 0, r;
		try (in) { while ((r = in.read(buf)) > 0) n += r; }
		return n;
	}
}
