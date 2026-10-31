# Apex pack compression format, version 1

SkyBlock Apex's own compression format, tuned for the item repo bundle (one large JSON file). Decoder:
`dev.ashtonite365.apex.compress.ApexPack`; encoder: `ApexPackEncoder`; command line: `ApexPackTool` (the `apexPack`
Gradle task). The repo loader (`RepoBundle.unpackCompressed`, through `Decompress.open`) tells this format from zstd by
the first four bytes, never by the file name, so a bundle may be either (`repo.json.zst` keeps its name).

The design goal is decode speed first and size second: the whole content is the window, sequences are coded with
canonical prefix codes (no arithmetic or ANS state), and the commonest sequence costs one table lookup for both lengths
plus one for the offset.

## Status: adopted (Ashton 28 Oct 2026, F)

Apex uses this format for repo data and wherever it packs or caches data itself:

* **Repo bundle.** The manifest lists the pack under `alternates`
  (`{"bundle":"repo.json.apack","format":"apex-pack","format_version":1,"bytes":N,"sha256":...}`). `RepoBundle.install`
  downloads it, checks its size and sha256 and decodes it with `ApexPack`; when the entry is absent, or on any failure
  (download error, size or sha256 mismatch, not a pack, a broken frame), it uses `repo.json.zst`, which stays the main
  bundle. Repo 2026.10.28.1036 ships the pack with `--max --no-checksum` and 4 MiB blocks; frames without a checksum
  (flags 0) are supported, and the download's sha256 covers the bytes. `RepoBundleRealFixtureTest` decodes that real
  bundle and checks the pack and the zstd file give the same bytes and the same unpacked tree.
* **Auction price history** day files: `prices_<date>.json.apack` (fast encoder); legacy `.json.gz` files are still read
  and rewritten as packs on the next append.
* **Far-terrain surface chunks:** codec version 4 stores the body as a pack (fast encoder); versions 1-3 still decode.

The differential tests (`ZstdDecoderTest`, `ApexPackTest`) and the speed check (`scripts/bench-compress.sh`) stay. The measurements below
are from before the decision.

Measured on the live repo bundle (35,317,039 bytes of JSON; `scripts/bench-compress.sh`, 7 JVM forks, 20 warm-up and
30 timed decodes each, median of the fork medians, shared 8-core box, 28 Oct 2026, after the zstd decoder's
32-byte match copy, interleaved 4-stream literals and one-pass Huffman table build):

| decoder | input | size (bytes) | one-shot decode (ms) | streaming (ms) |
|---|---|---|---|---|
| reference zstd (native libzstd) | published `repo.json.zst` (zstd -19) | 2,847,064 | 11.9 | |
| old vendored aircompressor (Java, Unsafe) | same | 2,847,064 | 19.7 | 340.2 |
| our zstd decoder (`Zstd`, `ZstdInputStream`) | same | 2,847,064 | 18.5 | 27.7 |
| our pack decoder (`ApexPack`) | pack, `encode --max` | 2,715,052 (-4.6%) | 19.6 | 31.1 |

Fork ranges: our zstd 17.5-18.7 ms, aircompressor 19.0-20.8 ms, so our decoder is faster than the dependency it
replaced in every fork (it was 25.5 vs 23.1 ms before these changes, under a busier box). The pack is smaller than
zstd -19 but now decodes at about the speed of our zstd decoder, and reference zstd decodes its file about 1.6x
faster, so by the adoption rule (decode definitely faster than zstd, at most 5% larger) the published bundle stays
zstd, decoded by our own decoder. The pack's checksum costs about 1.5-2 ms (the published zstd has none; downloads are
sha256-checked anyway, so `--no-checksum` would be fine for a release). The loader already accepts a pack (by magic
bytes), so once mod versions with this loader are out, the published file could be switched if a later encoder or
decoder wins.

## Conventions

* `u8` is one byte. `varint` is unsigned LEB128: 7 bits per byte, least significant group first, high bit set on every
  byte but the last; at most 9 bytes in the header, 5 inside code tables.
* Bit streams are read LSB-first: the first bit of a stream is bit 0 of its first byte. A value of `n` raw bits is read
  with its least significant bit first. Unused bits of a stream's last byte are zero.
* `XXH64` is the 64-bit xxHash with seed 0.

## File

| field | size | meaning |
|---|---|---|
| magic | 4 | `AE 41 50 4B` (`0xAE` "APK") |
| version | u8 | 1 |
| flags | u8 | bit 0: checksum present; other bits must be 0 |
| content size | varint | number of decoded bytes |
| blocks | | until a block of type 0 |
| checksum | 8 | only with flag bit 0: XXH64 of the whole content, little-endian |

Nothing may follow. The blocks must produce exactly the content size.

## Blocks

Each block starts with a type byte:

* `0`: end of blocks.
* `1`: stored. `varint n`, then `n` bytes copied to the output.
* `2`: compressed (below).
* anything else is an error.

Matches may reach back to the first byte of the content, across blocks of any type. Three repeat offsets start at
1, 2, 3 for the content and live across blocks; stored blocks leave them unchanged.

### Compressed block

    varint raw          decoded size of the block (at most 2^24 and at most what is left of the content)
    varint litCount     literal bytes in the block (at most raw)
    varint seqCount     sequences (at most raw / 3)
    literal section
    sequence section

#### Literal section

    u8 contexts                  1 to 16
    if contexts > 1: 128 bytes   context map: byte i gives the context of previous-literal value 2i (low nibble) and
                                 2i+1 (high nibble); every nibble must be below contexts
    contexts code tables         alphabet 256, one per context (see "Code tables")
    varint s0, s1, s2            sizes in bytes of literal streams 0, 1 and 2
    varint total                 size of all four streams; stream 3 is total - s0 - s1 - s2 (must not be negative)
    total bytes                  the four streams, back to back

With `q = litCount / 4` (rounded down), streams 0, 1 and 2 each decode `q` literals and stream 3 decodes
`litCount - 3q`. Stream `k` fills literal positions `k*q` onwards. Each literal is decoded with the code table of the
context that the map assigns to the previous literal of the same stream; the first literal of each stream uses the
context of value 0. With one context the map is absent and all values use context 0. Each stream must end in its last
byte (it is an error to read past it or to leave a whole byte unread).

If a context's table has no symbols, decoding a literal with it is an error.

#### Sequence section

    token code table    alphabet 832
    length code table   alphabet 64
    offset code table   alphabet 67
    varint tokBytes     size of the token stream
    varint seqBytes     size of the token stream plus the offset stream (tokBytes <= seqBytes)
    seqBytes bytes      token stream, then offset stream

Each sequence reads from both streams, in this order:

1. A token symbol `t` from the token stream. `llc = t / 52` (literal-run code) and `mlc = t % 52` (match-length code).
2. The literal run `ll`:
   * `llc` 0 to 14: `ll = LL_BASE[llc] + bits(LL_EXTRA[llc])` from the token stream.
   * `llc` 15 (escape): a symbol `s` of the length table, from the token stream, then
     `ll = 32 + BUCKET_BASE[s] + bits(BUCKET_EXTRA[s])`.
3. The match length `ml = 3 + ML_BASE[mlc] + bits(ML_EXTRA[mlc])` from the token stream.
4. An offset symbol `o` from the offset stream, giving the distance `dist`, with repeat offsets `r0, r1, r2`:
   * `o = 0`: `dist = r0`.
   * `o = 1`: `dist = r1`; swap `r0` and `r1`.
   * `o = 2`: `dist = r2`; then `r2 = r1, r1 = r0, r0 = dist`.
   * `o >= 3`: `dist = BUCKET_BASE[o - 3] + bits(BUCKET_EXTRA[o - 3]) + 1` from the offset stream;
     then `r2 = r1, r1 = r0, r0 = dist`.
5. Copy the next `ll` literals to the output, then copy `ml` bytes starting `dist` bytes back, one byte at a time (so the
   copy may overlap its own output). `dist` must be at least 1 and reach no further back than the start of the content.

After the last sequence the remaining literals are copied. The block must decode to exactly `raw` bytes and use exactly
`litCount` literals; a sequence that would overrun either is an error. Both streams must end in their last byte.

#### Length tables

Literal-run codes:

| llc | 0-11 | 12 | 13 | 14 | 15 |
|---|---|---|---|---|---|
| LL_BASE | llc | 12 | 16 | 24 | escape (32 + bucket) |
| LL_EXTRA | 0 | 2 | 3 | 3 | - |

Match-length codes (of `ml - 3`): codes 0-15 are the value itself with no extra bits. For code `c >= 16`, let
`k = 4 + (c - 16) / 2` and `m = (c - 16) % 2`: `ML_BASE = (2 + m) << (k - 1)`, `ML_EXTRA = k - 1`. Code 51 is the
last, so the longest match is `3 + 2^22 - 1`.

Buckets (escape lengths and offsets), symbols 0-63: symbols 0-7 are the value itself with no extra bits. For symbol
`s >= 8`, let `k = 3 + (s - 8) / 2` and `m = (s - 8) % 2`: `BUCKET_BASE = 2^k + m * 2^(k-1)`, `BUCKET_EXTRA = k - 1`.

## Code tables

Every alphabet uses canonical prefix codes of at most 11 bits. A table description:

* `u8 0`: no symbols. Decoding a symbol with this table is an error.
* `u8 1`, `varint s`: the single symbol `s` (below the alphabet size), coded with zero bits.
* `u8 2`, `varint count - 1` (count at most the alphabet size), then the code lengths of symbols `0 .. count-1` as
  nibbles, low nibble of each byte first: `0` = unused, `1`-`11` = code length, `15` followed by a nibble `r` =
  `r + 3` unused symbols; `12`-`14` are errors. A final unused high nibble is zero. Symbols at or past `count` are unused.
  The lengths must form a complete code: the sum of `2^-len` over the used symbols is exactly 1.

Codes are assigned canonically: shorter codes first, and within one length in symbol order, each code being the previous
one plus one (moving to a longer length shifts left). In the bit stream a code is written most significant bit first,
so with LSB-first streams the decoder can index a 2^11 table with the next 11 bits of the stream.

## Decoder limits

`ApexPack.decompress(byte[])` refuses content above 1 GiB (`DEFAULT_MAX_OUTPUT`) before allocating; the loader passes
its own limits. Corrupt or truncated input throws `CompressFormatException`, never anything else.

## Encoder

`ApexPackEncoder` finds matches with hash chains over the whole input and chooses them with a price-driven shortest
path over 32 KiB segments, in two passes (the second priced by the first pass's code lengths). It then cuts ~1 MiB
blocks, builds the literal contexts by greedily merging previous-byte groups while that saves bits, and falls back to
a stored block when compression does not pay.

    ./gradlew apexPack --args="encode --max IN OUT"     # release bundles (slow: about 14 min for the 35 MB repo)
    ./gradlew apexPack --args="encode IN OUT"           # default (about 1 min)
    ./gradlew apexPack --args="decode IN OUT"           # IN may be zstd or a pack
    ./gradlew apexPack --args="info IN"

`IN` may be raw, zstd or a pack (compressed input is decoded first). Relative paths are resolved from the project directory
(where Gradle runs the tool). `encode` verifies the round trip before writing.
