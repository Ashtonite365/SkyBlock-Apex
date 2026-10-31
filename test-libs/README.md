# Test-only libraries

Jars here are used by tests only and are never put into the mod jar (JarContentsTest checks this).

- `aircompressor-v3-3.13.jar` (Apache-2.0, https://github.com/airlift/aircompressor): the zstd oracle for
  `src/test/kotlin/compress` (differential tests of our own decoder, libzstd frames at every level, benchmarks).
  Checked in because builds run offline.
