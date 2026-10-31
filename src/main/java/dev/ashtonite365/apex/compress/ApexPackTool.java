// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compress;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ApexPackTool {
	private ApexPackTool() {}

	public static void main(String[] args) throws IOException {
		try {
			System.exit(run(args));
		} catch (CompressFormatException e) {
			System.err.println("error: " + e.getMessage());
			System.exit(1);
		}
	}

	public static int run(String[] args) throws IOException {
		if (args.length == 0) return usage();
		List<String> rest = new ArrayList<>(List.of(args).subList(1, args.length));
		switch (args[0]) {
			case "encode" -> {
				ApexPackEncoder.Options o = new ApexPackEncoder.Options();
				if (rest.remove("--fast")) o = ApexPackEncoder.Options.fast();
				if (rest.remove("--max")) o = ApexPackEncoder.Options.max();
				if (rest.remove("--no-checksum")) o.checksum = false;
				if (rest.size() != 2) return usage();
				byte[] in = Files.readAllBytes(Path.of(rest.get(0)));
				if (Decompress.detect(in, 0, in.length) != Decompress.Format.UNKNOWN) in = Decompress.decode(in);
				long t = System.nanoTime();
				byte[] out = ApexPackEncoder.compress(in, 0, in.length, o);
				double s = (System.nanoTime() - t) / 1e9;
				if (!java.util.Arrays.equals(ApexPack.decompress(out), in)) throw new IllegalStateException("round trip failed");
				Files.write(Path.of(rest.get(1)), out);
				System.out.printf("%s: %d -> %d bytes (%.3f%%) in %.1f s, round trip verified%n", rest.get(1), in.length, out.length,
					in.length == 0 ? 0.0 : out.length * 100.0 / in.length, s);
				return 0;
			}
			case "decode" -> {
				if (rest.size() != 2) return usage();
				byte[] out = Decompress.decode(Files.readAllBytes(Path.of(rest.get(0))));
				Files.write(Path.of(rest.get(1)), out);
				System.out.printf("%s: %d bytes%n", rest.get(1), out.length);
				return 0;
			}
			case "info" -> {
				if (rest.size() != 1) return usage();
				byte[] in = Files.readAllBytes(Path.of(rest.get(0)));
				Decompress.Format f = Decompress.detect(in, 0, in.length);
				long size = Decompress.decode(in).length;
				System.out.printf("%s: %s, %d bytes, content %d bytes%n", rest.get(0), f, in.length, size);
				return 0;
			}
			default -> { return usage(); }
		}
	}

	private static int usage() {
		System.err.println("""
			usage: apexPack encode [--fast | --max] [--no-checksum] INPUT OUTPUT
			       apexPack decode INPUT OUTPUT
			       apexPack info INPUT
			INPUT may be raw data, zstd or an Apex pack; encode decodes compressed input first.""");
		return 2;
	}
}
