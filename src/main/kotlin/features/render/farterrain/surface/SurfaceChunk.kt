/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, the surface data model (pure Kotlin, unit-tested).
 *
 * One [SurfaceChunk] per 16x16 chunk. For each of its 256 columns it keeps every block with a face toward open air,
 * at any depth (the top, cliff sides, bridges and the ground under them, towers under overhangs, the floor of a
 * roofed area open on one side), with hidden spans between them. Sealed caves and the underground never reach the
 * mesher. Air above the top is implicit; positions below the stored run count as hidden solid ground, with two
 * exceptions that let undersides over open air be drawn:
 *
 * * a run may end in one air marker (palette index 0) when the block above it has open air directly beneath
 *   (an overhang ceiling, the bottom of a thin floating island);
 * * a column may carry one "underside" entry ([underY] / [underState]): the lowest block of a solid mass whose
 *   bottom is below the stored run and has open air (the void) under it, e.g. the interior bottom of a floating
 *   island. Positions between the run and that block stay hidden; positions below it are air.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.zip.DeflaterOutputStream
import dev.ashtonite365.apex.compress.ApexPack
import dev.ashtonite365.apex.compress.ApexPackEncoder
import java.util.zip.InflaterInputStream

enum class BlockClass { AIR, OPAQUE, TRANSPARENT }

fun interface BlockClassifier {
	fun classify(state: String): BlockClass
}

object SurfaceIndex {
	const val COLUMNS = 256

	fun col(lx: Int, lz: Int): Int = (lz shl 4) or lx
	fun colX(col: Int): Int = col and 15
	fun colZ(col: Int): Int = col shr 4

	fun key(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
	fun keyX(key: Long): Int = (key shr 32).toInt()
	fun keyZ(key: Long): Int = key.toInt()
}

class SurfaceChunk(
	val x: Int,
	val z: Int,
	val palette: List<String>,
	val biomes: List<String>,
	val top: IntArray,
	val depth: IntArray,
	val columnBiome: IntArray,
	val blocks: IntArray,
	val offsets: IntArray,
	val capturedAt: Long,
	val edges: Int,

	val underY: IntArray? = null,

	val underState: IntArray? = null,
) {
	init {
		require(palette.isNotEmpty() && palette[0] == AIR) { "palette[0] must be air" }
		require(top.size == 256 && depth.size == 256 && columnBiome.size == 256 && offsets.size == 257)
		require(offsets[256] == blocks.size)
		for (c in 0 until 256) {
			if (offsets[c + 1] - offsets[c] == depth[c]) continue
			var h = 0
			for (i in offsets[c] until offsets[c + 1]) h += if (blocks[i] < 0) -blocks[i] else 1
			require(h == depth[c]) { "column $c: entries cover $h positions, depth ${depth[c]}" }
		}
		require((underY == null) == (underState == null)) { "underside arrays come together" }
		if (underY != null) {
			require(underY.size == 256 && underState!!.size == 256)
			for (c in 0 until 256) if (underY[c] != NO_COLUMN) {
				require(!isEmpty(c) && underY[c] < bottom(c)) { "underside must be below the run" }
				require(underState[c] in 1 until palette.size) { "underside must be a block" }
			}
		}
	}

	val key: Long get() = SurfaceIndex.key(x, z)

	val isAllAir: Boolean get() = top.all { it == NO_COLUMN }

	fun isEmpty(col: Int): Boolean = top[col] == NO_COLUMN || depth[col] == 0
	fun bottom(col: Int): Int = top[col] - depth[col] + 1

	fun underside(col: Int): Int = underY?.get(col) ?: NO_COLUMN

	fun undersideCount(): Int = underY?.count { it != NO_COLUMN } ?: 0

	fun stateIndex(lx: Int, y: Int, lz: Int): Int {
		val c = SurfaceIndex.col(lx, lz)
		val t = top[c]
		if (t == NO_COLUMN || y > t) return 0
		val d = t - y
		if (d < depth[c]) {
			val o = offsets[c]
			if (offsets[c + 1] - o == depth[c]) return blocks[o + d]
			var yy = t
			for (i in o until offsets[c + 1]) {
				val v = blocks[i]
				if (v < 0) {
					if (y > yy + v) return BELOW
					yy += v
				} else {
					if (yy == y) return v
					yy--
				}
			}
			return BELOW
		}
		val u = underY ?: return BELOW
		val uy = u[c]
		return when {
			uy == NO_COLUMN || y > uy -> BELOW
			y == uy -> underState!![c]
			else -> 0
		}
	}

	fun state(lx: Int, y: Int, lz: Int): String? = stateIndex(lx, y, lz).let { if (it == BELOW) null else palette[it] }

	fun storedBlocks(): Int = blocks.count { it > 0 } + undersideCount()

	inline fun forEachInColumn(col: Int, out: (y: Int, p: Int) -> Unit) {
		var y = top[col]
		for (i in offsets[col] until offsets[col + 1]) {
			val v = blocks[i]
			if (v < 0) y += v else { out(y, v); y-- }
		}
	}

	fun lowestBlock(col: Int): Int {
		for (i in offsets[col + 1] - 1 downTo offsets[col]) if (blocks[i] > 0) return blocks[i]
		return 0
	}

	val contentHash: Long by lazy {
		var h = -0x340d631b7bdddcdbL
		fun mix(v: Long) {
			h = (h xor v) * 0x100000001b3L
		}
		val nameHashes = LongArray(palette.size) { i -> stringHash(palette[i]) }
		val biomeHashes = LongArray(biomes.size) { i -> stringHash(biomes[i]) }
		for (c in 0 until 256) {
			if (isEmpty(c)) { mix(-1); continue }
			mix(top[c].toLong()); mix(depth[c].toLong())
			mix(biomeHashes.getOrElse(columnBiome[c]) { 0L })
			for (i in offsets[c] until offsets[c + 1]) { val v = blocks[i]; mix(if (v < 0) 0x48L * 31 + v else nameHashes[v]) }

			val u = underside(c)
			if (u != NO_COLUMN) { mix(0x55L); mix(u.toLong()); mix(nameHashes[underState!![c]]) }
		}
		h
	}

	fun contentEquals(o: SurfaceChunk): Boolean = x == o.x && z == o.z && contentHash == o.contentHash

	fun estimatedBytes(): Long = 96L + 4L * (256 * 3 + 257 + blocks.size) + (if (underY != null) 8L * 256 else 0L) + palette.sumOf { 40L + 2L * it.length } + biomes.size * 48L

	companion object {
		const val AIR = "minecraft:air"
		const val NO_COLUMN = Int.MIN_VALUE
		const val BELOW = -1
		const val EDGE_NORTH = 1
		const val EDGE_SOUTH = 2
		const val EDGE_WEST = 4
		const val EDGE_EAST = 8
		const val ALL_EDGES = 15

		fun stringHash(s: String): Long {
			var h = -0x340d631b7bdddcdbL
			for (ch in s) h = (h xor ch.code.toLong()) * 0x100000001b3L
			return h
		}
	}
}

object SurfaceChunkCodec {
	const val MAGIC = 0x41534331
	const val VERSION = 4
	private const val MIN_VERSION = 1

	fun encode(c: SurfaceChunk): ByteArray = encode(c, VERSION)

	fun encode(c: SurfaceChunk, version: Int): ByteArray {
		require(version in MIN_VERSION..VERSION) { "unknown version $version" }
		require(version >= 3 || c.blocks.none { it < 0 }) { "hidden spans need version 3" }
		val raw = ByteArrayOutputStream()
		DataOutputStream(raw).use { o ->
			o.writeInt(MAGIC)
			o.writeByte(version)
			val bytes = ByteArrayOutputStream()
			DataOutputStream(if (version >= 4) bytes else DeflaterOutputStream(bytes)).use { d ->
				d.writeInt(c.x); d.writeInt(c.z); d.writeLong(c.capturedAt); d.writeByte(c.edges)
				d.writeShort(c.palette.size); c.palette.forEach { d.writeUTF(it) }
				d.writeShort(c.biomes.size); c.biomes.forEach { d.writeUTF(it) }
				for (col in 0 until 256) {
					if (c.isEmpty(col)) { d.writeShort(0); continue }
					d.writeShort(c.depth[col])
					d.writeInt(c.top[col])
					d.writeShort(c.columnBiome[col])
					if (version >= 3) {

						writeVar(d, c.offsets[col + 1] - c.offsets[col])
						for (i in c.offsets[col] until c.offsets[col + 1]) { val v = c.blocks[i]; writeVar(d, (v shl 1) xor (v shr 31)) }
					} else {
						for (i in c.offsets[col] until c.offsets[col + 1]) writeVar(d, c.blocks[i])
					}
				}
				if (version >= 2) {

					writeVar(d, c.undersideCount())
					val cols = (0 until 256).filter { c.underside(it) != SurfaceChunk.NO_COLUMN }
					if (cols.isNotEmpty()) {
						val bits = ByteArray(32)
						for (col in cols) bits[col shr 3] = (bits[col shr 3].toInt() or (1 shl (col and 7))).toByte()
						d.write(bits)
					}
					for (col in cols) d.writeShort(c.underside(col))
					for (col in cols) writeVar(d, c.underState!![col])
				}
			}
			val body = bytes.toByteArray()
			o.write(if (version >= 4) ApexPackEncoder.compress(body, 0, body.size, ApexPackEncoder.Options.fast()) else body)
		}
		return raw.toByteArray()
	}

	fun decode(data: ByteArray): SurfaceChunk {
		val i = DataInputStream(ByteArrayInputStream(data))
		if (i.readInt() != MAGIC) throw IOException("not an Apex surface chunk")
		val version = i.readUnsignedByte()
		if (version !in MIN_VERSION..VERSION) throw IOException("unsupported surface chunk version")
		val body = if (version >= 4) ByteArrayInputStream(ApexPack.decompress(i.readAllBytes())) else InflaterInputStream(i)
		DataInputStream(body).use { d ->
			val x = d.readInt(); val z = d.readInt(); val at = d.readLong(); val edges = d.readUnsignedByte()
			val palette = List(d.readUnsignedShort()) { d.readUTF() }
			val biomes = List(d.readUnsignedShort()) { d.readUTF() }
			if (palette.isEmpty() || palette[0] != SurfaceChunk.AIR) throw IOException("bad palette")
			val top = IntArray(256) { SurfaceChunk.NO_COLUMN }
			val depth = IntArray(256)
			val biome = IntArray(256)
			val offsets = IntArray(257)
			val blocks = ArrayList<Int>()
			for (col in 0 until 256) {
				offsets[col] = blocks.size
				val n = d.readUnsignedShort()
				if (n == 0) continue
				if (n > SurfaceExtractor.MAX_COLUMN) throw IOException("bad depth")
				depth[col] = n
				top[col] = d.readInt()
				biome[col] = d.readUnsignedShort()
				if (version >= 3) {
					val entries = readVar(d)
					if (entries !in 1..n) throw IOException("bad entry count")
					var h = 0
					repeat(entries) {
						val z = readVar(d)
						val v = (z ushr 1) xor -(z and 1)
						if (v >= 0 && v !in palette.indices) throw IOException("bad palette index")
						h += if (v < 0) -v else 1
						blocks.add(v)
					}
					if (h != n) throw IOException("bad column height")
				} else repeat(n) {
					val v = readVar(d)
					if (v !in palette.indices) throw IOException("bad palette index")
					blocks.add(v)
				}
			}
			offsets[256] = blocks.size
			var underY: IntArray? = null
			var underState: IntArray? = null
			if (version >= 2) {
				val n = readVar(d)
				if (n !in 0..256) throw IOException("bad underside count")
				if (n > 0) {
					underY = IntArray(256) { SurfaceChunk.NO_COLUMN }
					underState = IntArray(256)
					val bits = ByteArray(32).also { d.readFully(it) }
					val cols = (0 until 256).filter { (bits[it shr 3].toInt() shr (it and 7)) and 1 != 0 }
					if (cols.size != n) throw IOException("bad underside bitmap")
					for (col in cols) {
						val y = d.readShort().toInt()
						if (depth[col] == 0 || y >= top[col] - depth[col] + 1 || underY[col] != SurfaceChunk.NO_COLUMN) throw IOException("bad underside")
						underY[col] = y
					}
					for (col in cols) {
						val v = readVar(d)
						if (v !in 1 until palette.size) throw IOException("bad underside palette index")
						underState[col] = v
					}
				}
			}
			return SurfaceChunk(x, z, palette, biomes, top, depth, biome, blocks.toIntArray(), offsets, at, edges, underY, underState)
		}
	}

	private fun writeVar(o: DataOutputStream, v: Int) {
		var x = v
		while (x and 0x7F.inv() != 0) { o.writeByte((x and 0x7F) or 0x80); x = x ushr 7 }
		o.writeByte(x)
	}

	private fun readVar(i: DataInputStream): Int {
		var r = 0
		var s = 0
		while (true) {
			val b = i.readUnsignedByte()
			r = r or ((b and 0x7F) shl s)
			if (b and 0x80 == 0) return r
			s += 7
			if (s > 28) throw IOException("bad varint")
		}
	}
}
