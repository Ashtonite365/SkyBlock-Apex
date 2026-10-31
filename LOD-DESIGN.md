# Apex Far Terrain (surface LOD): design

Branch `feat-lod-1025`, based on merge-e `8557cbbb`. Code: `src/main/kotlin/features/render/farterrain/surface/`,
mixins in `src/main/java/dev/ashtonite365/apex/mixins/surfacelod/`, shaders in `assets/apex/shaders/surfacelod/`.

A standalone, lightweight far-terrain renderer written from scratch inside Apex. It records only the visible surface
of chunks the client has loaded, meshes it off-thread with the real block models and atlas sprites, and draws it wherever
the game is not drawing terrain, through Minecraft's own render API. It never reads Minecraft's render distance
(section 6.1).

## 0. Requirements checklist (Ashton's spec)

| # | Requirement | How the design meets it |
|---|---|---|
| 1 | Every block as close to full texture and detail as possible | Meshing feeds the stored block states to vanilla's own `ModelBlockRenderer` / `FluidRenderer` through a read-only block view, so each face uses the block's real baked model quads and block-atlas sprites (top, side, bottom per face), vanilla biome tint (grass, leaves, water via the stored column biome) and ambient occlusion. No flat colours. |
| 2 | Fully custom, shipped inside the Apex jar, no outside library or mod dependency | All code is new Apex code (Kotlin + 2 small Java mixins + 2 GLSL files). Only Minecraft, Fabric API and Fabric Loader are used, which Apex already depends on. |
| 3 | No NVIDIA lock-in, no vendor GL extensions | Only Minecraft's `GpuDevice` / `RenderPass` / `RenderPipeline` API with vanilla's `BLOCK` vertex format, vanilla's sequential quad index buffer and plain GLSL 330 shaders using vanilla includes. No `NV_*`, no mesh shaders, no bindless, no compute. Indirect multi-draw (core GL 4.3 / Vulkan, through the same `RenderPass.drawIndexedIndirect` API vanilla uses) only where vanilla itself draws its terrain that way (`LevelRenderer.isChunkRenderingUsingMultiDrawIndirect()`: its device checks, known driver issues and the player's option), with the per-region path everywhere else. Runs wherever vanilla terrain runs. (Changed 2026-10-27 from "no indirect": see section 4.) |
| 4 | Intentionally lightweight: only the visible surface | Per column only the top block and the blocks whose side faces can be seen (down to the lowest neighbouring surface) are stored, plus the single underside block of an overhanging or floating mass over open air. No caves, no underground. A face is culled only when its neighbour's face completely covers it (section 1.2). |
| 5 | Full-texture surface blocks | Same as 1: the surface blocks are meshed with their real models; transparent blocks (water, glass, leaves) are kept and drawn with the translucent / cutout pipelines. |
| 6 | Works with or without Sodium, no hard dependency on it | Common far-terrain code references no Sodium class (a unit test scans its bytecode). The hand-off reads what the game actually draws: vanilla's own visible-section list, or, only when Sodium is installed, an optional reader in the separate `sodium` compat source set (service-loaded by name, its mixins gated in `MixinPlugin.shouldApplyMixin` on `isModLoaded("sodium")`) that reads Sodium's per-frame render lists through its public API. Without Sodium that code is never class-loaded; if it fails or the Sodium version is untested, the prediction path is used and the reason is logged once (section 6). Hooks are Fabric `LevelRenderEvents` plus optional (`require = 0`) mixins into vanilla `LevelRenderer` / `Camera`. If the in-pass hook does not fire (another renderer replaced vanilla terrain), the renderer draws in its own render pass at `END_MAIN` on the main render target, sharing its depth buffer (section 6). |
| 7 | No third-party LOD mod code at all | No dependency, API, code or copied ideas from any other LOD mod. The old island-saves compat code for a third-party LOD mod was removed entirely in merge-f-1025. If any known far-terrain mod id is loaded (`surface/OtherLodMods.MOD_IDS`, a mod-id check only), Apex Far Terrain **disables itself** rather than drawing on top of it (choice: disable, not coexist; both would draw the same distance band and fight over depth and fog). |
| 8 | Debug mode (off by default): overlay + log of load times, recording, meshing, drawing, memory, cache | Setting *Debug Mode*. A HUD overlay shows: island and per-region disk load times; chunks recorded this visit, pending captures, re-recorded (changed) chunks, plus a mini map of recorded / pending / stored-unverified chunks around the player; mesh build time per region (last / avg / max), mesh queue length and worker usage %; draw calls, regions, faces and vertices drawn per frame; VRAM and RAM used; cache hits / misses / changes, disk saves. In debug mode the same lines are logged every 10 s, and a *Dump Debug Log* button writes a full report plus the recent event log to `.minecraft/apex/far-terrain-surface/debug/dump-<time>.log`. |
| 9 | Per-island saves, keyed by the island the mod detects, PI / Garden also by profile; load on arrival, unload on leave; never mix islands or instances | `SurfaceIslandKeys` turns (server, Hypixel locraw mode = `SkyBlockIsland`, sidebar ownership, profile id, dimension) into a key. Each key has its own folder and its own in-memory store and GPU meshes. Leaving an island flushes and drops all of its data. Guests' islands and instances (Dungeons, Kuudra, Mineshafts) are memory-only. Every live capture is compared with the stored chunk by content hash; a different server instance with changed terrain causes those chunks to be re-recorded and re-meshed (counted as "changed" in debug). |
| 10 | Decoupled from the vanilla render distance | Nothing in far terrain reads `renderDistance` / `getEffectiveRenderDistance`, the server view distance or a chunk storage radius derived from them. Ownership comes from the drawn list + loaded + 8 neighbours; the hand-off box, pre-draw band and prediction use the measured `SurfaceGameArea`; draw range, far clip and fade use far terrain's own Distance settings. Tested by behaviour and by a bytecode + source scan (section 6.1). |

## 1. Data model

`SurfaceChunk` (pure Kotlin): per chunk, per column (256):

* `top`: world y of the highest non-air block (or none);
* `depth`: number of positions stored from `top` downward (exposed blocks, open air, hidden spans; see 1.1);
* the full block states of that run (palette indices into the chunk palette of block-state strings, index 0 = air);
* `columnBiome`: biome at the top block (for tints);
* `edges`: which neighbouring chunks were known at capture;
* optional undersides (`underY` / `underState`, only allocated when a chunk has one): per column the y and state of the
  lowest block of a solid mass whose bottom lies below the stored run with only void beneath it (the interior bottom
  of a floating island).

Positions above `top` are air; positions below the stored run are "hidden ground" (treated as opaque, never drawn),
except: a run may end in one air marker (palette index 0) when its lowest block has open air directly beneath, and
below a column's underside block everything is air (positions between the run and the underside stay hidden).
Format "ASC1", deflate-compressed, strings rather than registry ids (version independent). Change detection uses a
64-bit content hash independent of palette order and capture time; undersides are mixed in only when present, so a
chunk without any hashes exactly as before.

Format versions: v1 = header, palette, biomes, 256 column records. v2 (written now) appends an underside list: varint
count, and when it is not 0 a 32-byte column bitmap, the y of each listed column (short) and its palette index
(varint), as separate streams so deflate packs flat island bottoms tightly. v1 files still decode (no undersides).
Overhead: a chunk without undersides costs 1 raw byte (0 bytes after compression in tests); an air marker is 1 raw
byte in its column; measured after compression, a 16x16 flat-bottomed floating island chunk with 196 undersides
grew 153 -> 170 bytes and a rough-bottomed one with 256 undersides 80 -> 114 bytes.

### 1.1 Column extraction (`SurfaceExtractor`, pure, unit-tested)

Rule: store every block that has a face toward OPEN air (or toward an open see-through / non-full block), at any depth
in the column, regardless of overhangs; never store the walls of sealed pockets and caves.

* Grid: the chunk plus its 1-block border (18x18 columns) from `minOpaqueTop - FLOOD_BELOW (64)` (clamped to the world
  bottom) up to one block above the highest top. Unloaded border columns are treated as closed (solid).
* Open air: a flood fill (6-neighbour, through air and see-through / non-full blocks) seeded from the open sky of
  every loaded column (everything above its highest opaque block; a column with no opaque block at all is open to the
  void all the way down). Air reachable from there is open: under bridges, under overhangs, roofed areas open on a
  side, tunnels with an opening inside the grid, the void under floating islands. Air enclosed within the grid (a
  sealed cave, a closed room, a pocket inside an island, a tunnel whose only opening is an unloaded chunk) is not.
  The check is conservative: an opening further than one block outside the chunk is not seen from this chunk.
* Per column, top-down from `top`: a block with a face toward open air is stored; open air is stored as air (palette
  index 0) where a side neighbour holds a block (so that block's face toward it is not culled) and as one air marker
  under the lowest stored block; everything else is a hidden span. Trailing hidden positions are dropped.
* Fallback for masses thicker than the flood range over the void: the old single `underY` underside block.
* The chunk is re-captured when a neighbour that was unknown loads; a deeper re-capture is a refinement, not a change.

Data: each column is a list of entries; an entry >= 0 is one block (palette index), an entry < 0 is a span of hidden
positions, so a column holds several runs (bridge, open air, ground). `depth` counts positions (spans included).
`stateIndex` still answers air / state / hidden (`BELOW`) for any y, so the mesher, the face probe and the light use
it unchanged. Format v3 (written now): each column record adds an entry count and zigzag varint entries; v1 and v2
files still decode (they never hold spans).

### 1.2 Face culling (`SurfaceFaces`, pure, unit-tested)

Ashton's rule: a face is hidden only when the neighbouring block's face on that side completely covers it; otherwise
it is drawn, whether the neighbour is air, see-through or only partly covering. All six directions are treated
alike (undersides included). The decision is `SurfaceFaces.quadVisible(world, classifier, shapes, self, x, y, z,
dir, onFace)`, used by the mesher for every quad:

* Neighbour = hidden ground (below a stored run) or a chunk never recorded: hidden.
* Neighbour = air: drawn (overhang ceilings, stair and top-slab undersides, floating-island bottoms over the void).
* Neighbour = a block: hidden only when `FaceShapes.covers(self, neighbour, dir)`. In game this is vanilla's own
  face-occlusion shape comparison (`Block.shouldRenderFace`: the neighbour's occlusion face toward us must cover our
  occlusion face completely; non-occluding blocks such as glass, leaves and water have an empty face and never
  cover), plus "two equal see-through states cover each other" (water next to water, glass next to glass).
  Examples: a stair's full back against a wall hides both the back and the wall face; a stair's open notch facing a
  block leaves that block's face drawn; a floor slab on the ground hides the slab's bottom and the ground's top.
* Quads inside the block (not on the boundary in their direction, e.g. a stair's step top or a bottom slab's top)
  are always drawn, with one explicit exception (Ashton's rule 2): a bottom slab's UP quad is dropped when a full
  opaque cube (or hidden ground) sits directly on it. The block above still draws its own bottom over the slab, since
  the slab's empty top face does not cover it.
* `mask` / `forEachVisible` apply the same test to the six boundary faces (DOWN included) so blocks whose only
  visible face is an underside are not skipped; underside blocks are visited too.
* Fluids are tessellated by vanilla's fluid renderer over the same stored view, so their bottom face follows the same
  rule (drawn over air, hidden on the seabed or on hidden ground).

## 2. Capture and storage

* Capture (`SurfaceCapture`, main thread): on `ClientChunkEvents.CHUNK_LOAD` the chunk is queued; after a 2 s settle
  delay (lets the server finish sending updates) it is captured, and loaded neighbours that had that side unknown are
  re-captured too. A slow sweep re-captures loaded chunks every 2 minutes to catch block changes. Budget: 4 chunks
  per tick. Capture reads only block states and biomes; the result is immutable and handed to the worker.
* Storage (`SurfaceStore`, pure + java.io): one folder per island key under
  `.minecraft/apex/far-terrain-surface/<key path>/`, region files `r.<rx>.<rz>.asr` of 32x32 chunks, written whole to a
  temp file and atomically moved. Dirty regions flush every 60 s, on leave and at shutdown, on the worker thread.
* Island keys (`SurfaceIslandKeys`, pure, unit-tested):
  * Hypixel SkyBlock public islands: `hypixel/<island>` (hub, dwarven-mines, crimson-isle, ...), from the locraw mode
    Apex already detects (`SkyBlockIsland`).
  * Own Private Island / Garden: `hypixel/<island>/<profile uuid>`, ownership from the sidebar title
    ("SKYBLOCK" / "SKYBLOCK CO-OP" = own, "GUEST" = visiting).
  * Visited islands, Dungeons, Kuudra, Mineshafts, other Hypixel games: memory-only, new per connection.
  * Pending (no fresh locraw, no sidebar, no profile id yet): no session, nothing recorded.
  * Other servers: `server/<host>/<dimension>`; single player: `singleplayer/<world>/<dimension>`.
  * Non-overworld dimensions get a suffix. Every path segment is slugged to `[a-z0-9-]`.

## 3. Off-thread meshing

One low-priority worker thread. Regions are 8x8 chunks (128x128 blocks). When a chunk is stored as new or changed,
its region (and a neighbouring region if the chunk is on the border) is queued once (deduplicated). The game mesher
(`SurfaceMesher`) walks the region chunk by chunk, skips blocks whose pure face mask is 0, and tessellates the rest
with vanilla's `ModelBlockRenderer` / `FluidRenderer` over a `BlockAndTintGetter` view of the stored surface
(air above, hidden ground below, column biome, full sky light). Every quad goes through `SurfaceFaces.quadVisible`
(section 1.2) with vanilla's face shapes; a quad counts as on the boundary when all four vertices lie on that face
plane. There is no blanket drop of downward quads. Output: one vanilla
`BLOCK`-format buffer per layer (solid, cutout, translucent) with per-chunk vertex ranges, so the renderer can skip the
chunks vanilla is drawing itself.

## 4. GPU upload and draw hook

* Upload on the render thread within a per-frame budget (`SurfaceUploadQueue`), into ONE shared vertex buffer
  (`SurfaceVertexArena` over `SurfaceArenaAllocator`, a best-fit free list in whole vertices with merging of free
  neighbours): one range per region, its layers back to back, written with `CommandEncoder.writeToBuffer`. When full
  the buffer grows (half again, at least 32 MiB, cap 1 GiB or the device's single-allocation limit): a new buffer, the
  used part copied on the GPU, the old one closed. Offsets never move. A region that does not fit even the cap gets a
  buffer of its own and is drawn per region.
* Draw paths: (1) where vanilla draws its terrain with indirect multi-draw, one `drawIndexedIndirect` per layer for all
  drawn regions (commands in a ring of three GPU buffers; each region's offset from the camera is a per-instance
  attribute `RegionOffset`, picked by the command's first instance; the transform's model offset is zero), and the
  per-frame offsets / commands are only written when they changed; (2) otherwise per region layer one
  `multiDrawIndexed` submission where the device has it, (3) else one draw per range. Paths 2 and 3 use the
  per-region transform as before.
* Vertex format: vanilla `BLOCK` (28 B: position 3 x float, color 4 x byte, UV0 2 x float, UV2 2 x short). The shader
  reads every field, so nothing unused can be trimmed; the only lossless shrink (light as 2 x byte) gives 26 B, which
  the 4-byte attribute alignment pads back to 28. Kept at 28 B on purpose (no precision loss, no change in the look).
* `START_MAIN` (Fabric): pick regions within the far distance and the camera frustum, compute per-region transforms
  (region origin minus camera, in double precision, so no jitter far out), write them to the dynamic uniform buffer.
* Vanilla path: a mixin draws solid + cutout right after vanilla's opaque terrain inside vanilla's main pass, and
  translucent right before vanilla's translucent terrain (same depth buffer, same lightmap).
* Chunks the game is drawing (the hand-off of section 6: loaded, all 8 neighbours loaded, in the game's drawn list) are
  cut out of each region draw by splitting
  it into runs of contiguous per-chunk vertex ranges (`SurfaceDrawRuns`, pure, unit-tested), so far terrain never
  overlaps real chunks.

## 5. Fog and depth

* Own shaders (`surfacelod/terrain.vsh|fsh`): environmental fog (water, lava, blindness, weather) is kept, the
  render-distance fog is replaced by a fade into the fog colour over the last 20 % of far terrain's OWN distance
  (`SurfaceFarDistance.fadeStart/fadeEnd`, from the Distance settings). The game's render-distance fog fields are only
  pushed further out to that fade (`SurfaceFog.pushOut`, a max), never read for anything else.
* Depth: the camera far plane is pushed out to cover the far distance (`Camera.depthFar`, mixin, `require = 0`;
  `SurfaceFarDistance.farClip`, far terrain's own distance only; the game's value is kept when larger). The
  depth buffer is reverse-Z float, so near precision does not suffer. Far terrain uses the normal depth test and writes
  depth (solid / cutout); translucent uses the vanilla translucent depth state.

## 6. Sodium-present path

Drawn list (feat-drawnlist-1025): the hand-off decides which columns the game owns from what the game actually draws
each frame (`SurfaceDrawnList`). Vanilla: `LevelRenderer.visibleSections()` (the occlusion graph's frustum-culled list
that `extractSectionDrawGroups` walks), read at the head of that step before any hold-back. Sodium installed:
`compat/sodium/SodiumDrawnSections` (only loaded through `SurfaceDrawnSources` when `isModLoaded("sodium")`) walks
Sodium's render lists for the frame (one list per 8x4x8 render region; sections with geometry and with block
entities) through Apex's accessor on the world renderer. Neither available, or the reader failed / untested version:
the prediction path (built state + measured radius + geometry), logged once. A column becomes the game's once it is
loaded with all 8 neighbours, its surface section (or the one below) was in the drawn list within 100 ms, its top
section is built, and the 100 ms hold passed. It then stays the game's while it is loaded, has all 8 neighbours, and
the game still draws it: in this frame's list (vanilla), in the list within 100 ms (renderer list), or outside the
camera's view (behind the camera nothing is visible, so frustum culling never hands it back). A column in view that the
game stops drawing (it left the game's range while still loaded, the game's occlusion graph dropped it, its sections
were recycled) is far terrain's that same frame, and its next hand-off starts over. Pre-draw band (renderer list and
prediction only) and the two-ring fallback are unchanged; the vanilla list is exact for the frame, so the vanilla path
has no pre-draw band and never draws a column twice.

### 6.1 Independence from the render distance (fix-farrd-1025)

Far terrain bases nothing on `Options.renderDistance` / `getEffectiveRenderDistance`, the server's view distance, or
the chunk storage radius derived from them. The pieces that used them now use:

* Hand-off ownership (`SurfaceHandoffDecider`, pure): the drawn list + loaded + 8 neighbours (above). The prediction
  path (no list) asks the game's own "compiled and visible" for the surface section every frame instead.
* Hand-off box and forget radius (`SurfaceHandoffGrid`): `SurfaceGameArea.extent`, the farthest loaded chunk around the
  camera's chunk (fed by the chunk load / unload events, checked against the game's own `hasChunk`), plus 1.
* Pre-draw band and prediction geometry: `SurfaceGameArea.radius`, the game's drawn reach over the last 5 s
  (`SurfaceDrawnList.frameReach`) capped by the largest fully loaded disc; never larger than what the game drew.
* Hand-off distance limit: the game's environmental fog end only (`SurfaceFog.gameFogEnd`).
* Recording seed when a session starts: every chunk the game reported loaded (`SurfaceGameArea.loadedChunks`), not a
  square of the render distance.
* Draw range, far clip and fade: `SurfaceFarDistance` from the "Unlimited Distance" (default on) / "Distance" (default
  128 chunks) settings only.

Fog choice: far terrain's fade runs over the last 20 % of its own drawn distance (`SurfaceFarDistance.fadeStart` =
0.8 x, `fadeEnd` = 1.0 x the effective far distance in blocks: the Distance setting, or with Unlimited Distance the
farthest resident region). The game's render-distance fog fields are only raised to that fade (`max`), so the game's
own terrain is never fogged nearer than vanilla would; the far-terrain shader never reads them.

`SurfaceRenderDistanceIndependenceTest` checks, for every game render distance 2..32, that far terrain draws every
column the game does not draw (the game's disc of that radius) and the game keeps exactly its drawn columns, with the
same hand-off box, loaded set, range, clip and fog; `SurfaceDrawnHandoffSimTest.anyGameRenderDistanceFrom2To32StaysGapFree`
runs the vanilla and renderer simulations at 2..32 with no gap. It also checks that changing the simulated render distance alone changes no decision,
box, radius, fog or draw range, that ownership follows the drawn list, and scans the bytecode constant pools and the
sources of far terrain (Kotlin, `mixins/surfacelod`, the Sodium reader) for every render-distance name. Allowed, with
the reason in the test: the render-distance fog fields in `SurfaceFog` / `SurfaceFogMixin` (only pushed out) and the
camera far plane in `SurfaceCameraDepthFarMixin` / `SurfaceRenderer.depthFar` (only pushed out).

Sodium replaces vanilla chunk rendering but keeps vanilla's `LevelRenderer` frame and Fabric's
`LevelRenderEvents`. The in-pass mixins are `require = 0`, so if their target call is gone the game still starts. Each
frame the renderer records whether the in-pass hook drew; if not, at `END_MAIN` it opens its own render pass on the
main render target (colour + depth, no clear) and draws solid, cutout and translucent there. The same fallback is used
for vanilla's improved-transparency mode (no shared translucent pass). The same applies to other renderers that keep
Fabric's events (for example NVIDIA-only ones); nothing vendor-specific is needed on our side.

## 7. Memory and VRAM budgets

* RAM: about 2-6 KB per stored chunk (a typical SkyBlock surface run is 1-4 blocks deep). Budget 16 384 chunks in memory
  per island (~60 MB worst case); beyond that the farthest chunks are evicted from memory (they stay on disk).
* VRAM: vanilla `BLOCK` vertex = 28 B, 4 vertices per face. A typical surface chunk has ~400-900 faces, i.e.
  ~45-100 KB. Budget 256 MB of vertex buffers; regions outside the draw distance + 2 regions are freed, farthest first
  when over budget.
* CPU per frame: shared-buffer path 3 draw calls (one per layer; more only past the device's per-call command limit),
  otherwise O(visible regions) draw calls (typically 30-150 at 32 chunks); no per-block work on the render thread.

## 8. Status of this prototype

Code: `src/main/kotlin/features/render/farterrain/surface/`, mixins `mixins/surfacelod/`, shaders
`assets/apex/shaders/surfacelod/`, tests `src/test/kotlin/features/render/farterrain/surface/`.

Implemented:
* Pure core (unit-tested): `SurfaceChunk` + codec, `SurfaceExtractor`, `SurfaceFaces`, `SurfaceStore` (region files,
  NEW/CHANGED/SAME by content hash, atomic flush, eviction that never drops unsaved data), `SurfaceIslandKeys`,
  `SurfaceRegions` / `SurfaceDrawRuns`, `SurfaceStats` (debug counters, event ring, dump text).
* Game side (compiles; full suite green on the box, not yet tested in game): `SurfaceCapture` (loaded chunk + neighbour borders ->
  `SurfaceChunk`, state classifier), `SurfaceMesher` (vanilla model + fluid tessellation over the stored view, per-chunk
  vertex ranges, shape-based culling of every quad, undersides over air drawn), `SurfaceRenderer` (uploads, frustum + distance culling, vanilla-chunk cut-outs, in-pass
  draw + own-pass fallback), the two `require = 0` mixins, `SurfaceManager` (settings, per-island sessions, capture
  queue with settle delay and edge re-capture, worker meshing with dedup, periodic/leave/shutdown flush, debug HUD with
  chunk map, 10 s debug log, dump button, RAM-budget eviction after each periodic flush: over 16 384 chunks, already-saved
  chunks beyond distance + 16 chunks are dropped from memory), translations and settings-map rows.

Stubbed / not yet done:
* VRAM budget is a region-count cap (400 regions) rather than a byte budget. An evicted chunk stays absent from its
  region's mesh until the next arrival if that region is re-meshed meanwhile.
* No mesh cache on disk: meshes are rebuilt from the stored surface on arrival.
* No in-world 3D outline of recorded / pending chunks; the debug HUD shows a text chunk map instead.
* Fog fade uses the shared shader approach; no per-biome fog colour work beyond vanilla's fog uniforms.
