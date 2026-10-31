/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, the per-island surface store (pure Kotlin + java.io, unit-tested).
 *
 * One store per island key ([SurfaceIslandKeys]). On disk: region files "r.<rx>.<rz>.asr" of 32x32 chunk blobs
 * ([SurfaceChunkCodec]), always rewritten whole into a temp file and moved over the old one. A store without a folder
 * (visited islands, instances) lives in memory only.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

class SurfaceStore(val folder: File?, private val stats: SurfaceStats? = null) : SurfaceWorld {
	enum class PutResult {

		NEW,

		CHANGED,

		SAME,

		REFINED,
	}

	@Volatile var lastDifference: String? = null
		private set

	private val chunks = ConcurrentHashMap<Long, SurfaceChunk>()
	private val dirtyRegions = ConcurrentHashMap.newKeySet<Long>()

	val chunkCount: Int get() = chunks.size
	val keys: Set<Long> get() = chunks.keys
	override fun chunk(cx: Int, cz: Int): SurfaceChunk? = chunks[SurfaceIndex.key(cx, cz)]
	fun has(cx: Int, cz: Int): Boolean = chunks.containsKey(SurfaceIndex.key(cx, cz))

	fun estimatedBytes(): Long = chunks.values.sumOf { it.estimatedBytes() }

	fun put(c: SurfaceChunk): PutResult {
		val old = chunks[c.key]
		if (old == null) {
			stats?.onPut(PutResult.NEW)
			chunks[c.key] = c
			dirtyRegions.add(regionKey(c.x shr 5, c.z shr 5))
			return PutResult.NEW
		}

		val outcome = if (old.contentEquals(c)) SurfaceChurn.Outcome(SurfaceChurn.Result.SAME, if ((old.edges or c.edges) != old.edges) c else old, null)
		else SurfaceChurn.compare(old, c)
		val result = when (outcome.result) {
			SurfaceChurn.Result.SAME -> PutResult.SAME
			SurfaceChurn.Result.REFINED -> PutResult.REFINED
			SurfaceChurn.Result.CHANGED -> PutResult.CHANGED
		}
		stats?.onPut(result)
		if (result == PutResult.SAME) {

			if (outcome.chunk !== old) chunks[c.key] = outcome.chunk
			return result
		}
		if (result == PutResult.CHANGED) lastDifference = outcome.firstDifference
		chunks[c.key] = outcome.chunk
		dirtyRegions.add(regionKey(c.x shr 5, c.z shr 5))
		return result
	}

	fun hasDirty(): Boolean = dirtyRegions.isNotEmpty()

	fun load(): Int {
		val dir = folder ?: return 0
		val t0 = System.nanoTime()
		val files = dir.listFiles { f -> f.isFile && REGION_NAME.matches(f.name) } ?: return 0
		var n = 0
		for (f in files) {
			val r0 = System.nanoTime()
			var inFile = 0
			try {
				for (blob in readRegion(f.readBytes())) {
					try {
						val c = SurfaceChunkCodec.decode(blob)
						chunks[c.key] = c
						inFile++
					} catch (e: Exception) {

					}
				}
			} catch (e: Exception) {

			}
			n += inFile
			stats?.onRegionLoaded(f.name, inFile, (System.nanoTime() - r0) / 1_000_000.0)
		}
		stats?.onIslandLoaded(folder.path, n, files.size, (System.nanoTime() - t0) / 1_000_000.0)
		return n
	}

	fun flush(): Int {
		val dir = folder ?: run { dirtyRegions.clear(); return 0 }
		if (dirtyRegions.isEmpty()) return 0
		dir.mkdirs()
		var written = 0
		for (rk in dirtyRegions.toList()) {
			dirtyRegions.remove(rk)
			val rx = SurfaceIndex.keyX(rk)
			val rz = SurfaceIndex.keyZ(rk)
			val entries = chunks.values.filter { (it.x shr 5) == rx && (it.z shr 5) == rz }.sortedBy { it.key }
			val target = File(dir, regionName(rx, rz))
			val tmp = File(dir, regionName(rx, rz) + ".tmp")
			try {
				val bytes = writeRegion(entries.map { SurfaceChunkCodec.encode(it) })
				tmp.writeBytes(bytes)
				try {
					Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
				} catch (e: AtomicMoveNotSupportedException) {
					Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
				}
				written++
				stats?.onSaved(target.name, entries.size, bytes.size.toLong())
			} catch (e: IOException) {
				dirtyRegions.add(rk)
				tmp.delete()
			}
		}
		return written
	}

	fun evict(keep: (SurfaceChunk) -> Boolean): Int {
		var n = 0
		val dirtyNow = dirtyRegions.toSet()
		for (c in chunks.values.toList()) {
			if (keep(c)) continue
			if (folder == null || regionKey(c.x shr 5, c.z shr 5) in dirtyNow) continue
			if (chunks.remove(c.key, c)) n++
		}
		return n
	}

	fun diskBytes(): Long = folder?.listFiles { f -> f.isFile && REGION_NAME.matches(f.name) }?.sumOf { it.length() } ?: 0L

	companion object {
		const val REGION_MAGIC = 0x41535231
		val REGION_NAME = Regex("^r\\.-?\\d+\\.-?\\d+\\.asr$")

		fun regionName(rx: Int, rz: Int) = "r.$rx.$rz.asr"
		fun regionKey(rx: Int, rz: Int): Long = SurfaceIndex.key(rx, rz)

		fun writeRegion(blobs: List<ByteArray>): ByteArray {
			val bytes = ByteArrayOutputStream()
			DataOutputStream(bytes).use { o ->
				o.writeInt(REGION_MAGIC)
				o.writeInt(1)
				o.writeInt(blobs.size)
				for (b in blobs) { o.writeInt(b.size); o.write(b) }
			}
			return bytes.toByteArray()
		}

		fun readRegion(data: ByteArray): List<ByteArray> {
			DataInputStream(ByteArrayInputStream(data)).use { i ->
				if (i.readInt() != REGION_MAGIC) throw IOException("not an Apex surface region")
				if (i.readInt() != 1) throw IOException("unsupported region version")
				val n = i.readInt()
				if (n !in 0..1024) throw IOException("bad chunk count")
				return List(n) {
					val len = i.readInt()
					if (len !in 1..(4 shl 20)) throw IOException("bad blob size")
					ByteArray(len).also { b -> i.readFully(b) }
				}
			}
		}
	}
}
