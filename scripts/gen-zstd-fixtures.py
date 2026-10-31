#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Writes the libzstd-made test frames in src/test/resources/compress/zstd (needs the python 'zstandard' package).

Each NAME.zst decodes to the source named before its first dot. Only the frames are checked in; sources.sha256 holds
the SHA-256 of every source, which ZstdDecoderTest checks besides comparing with aircompressor. Run from the repository
root; the output is deterministic for a given zstandard version."""
import hashlib
import os, random, zstandard as zs

OUT = "src/test/resources/compress/zstd"

def sources():
    r = random.Random(26)
    rnd = bytes(r.getrandbits(8) for _ in range(6000))
    words = [b"item", b"name", b"lore", b"\"id\":", b"ENCHANTED_", b"{", b"}", b"[", b"],", b"\\u00a7", b"7", b"0.5", b"null"]
    text = b" ".join(words[r.randrange(len(words))] for _ in range(30000))
    rle = b"\x00" * 300000
    small = b"hello zstd"
    mixed = bytes(r.getrandbits(3) * 37 & 255 for _ in range(20000)) + text[:30000] + rnd[:3000]
    a = bytes(r.getrandbits(8) for _ in range(65536)); block = bytes(r.getrandbits(8) for _ in range(1024))
    far = a + block * 9216 + a
    return {"empty": b"", "small": small, "random": rnd, "text": text, "rle": rle, "mixed": mixed, "far": far}

def main():
    os.makedirs(OUT, exist_ok=True)
    src = sources()
    with open(f"{OUT}/sources.sha256", "w") as f:
        for n in sorted(src): f.write(f"{n} {hashlib.sha256(src[n]).hexdigest()} {len(src[n])}\n")
    def put(name, data):
        open(f"{OUT}/{name}.zst", "wb").write(data)
    for n in ("empty", "small", "random", "text", "rle", "mixed"):
        for lvl in (-5, 1, 3, 9, 19, 22):
            for ck in (0, 1):
                for cs in (0, 1):
                    if lvl not in (3, 19) and (ck, cs) != (1, 1): continue
                    c = zs.ZstdCompressor(level=lvl, write_checksum=bool(ck), write_content_size=bool(cs))
                    put(f"{n}.L{lvl}.ck{ck}.cs{cs}", c.compress(src[n]) if cs else b"".join(_stream(c, src[n])))
    p = zs.ZstdCompressionParameters.from_level(19, window_log=24, enable_ldm=True, write_checksum=True)
    put("far.W24", zs.ZstdCompressor(compression_params=p).compress(src["far"]))
    c = zs.ZstdCompressor(level=5, write_checksum=True)
    skip = (0x184D2A53).to_bytes(4, "little") + (7).to_bytes(4, "little") + b"skipped"
    put("text.multi", c.compress(src["text"][:80000]) + skip + c.compress(src["text"][80000:]))
    p = zs.ZstdCompressionParameters.from_level(9, window_log=10, write_checksum=True)
    put("mixed.W10", zs.ZstdCompressor(compression_params=p).compress(src["mixed"]))

def _stream(c, data):
    o = c.compressobj(); yield o.compress(data); yield o.flush()

if __name__ == "__main__":
    main()
