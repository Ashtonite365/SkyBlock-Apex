// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.wiki

import com.mojang.blaze3d.platform.NativeImage
import java.io.ByteArrayInputStream
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.util.Client

class WikiImageCore<T>(private val max: Int = 256, private val clock: () -> Long = System::currentTimeMillis) {
	sealed interface State<out T> {
		data object Loading : State<Nothing>
		data object Failed : State<Nothing>
		data class Ready<T>(val tex: T, val w: Int, val h: Int) : State<T>
	}

	private val states = LinkedHashMap<String, State<T>>(64, 0.75f, true)
	private val used = HashMap<String, Long>()

	fun get(url: String): State<T>? { val s = states[url] ?: return null; used[url] = clock(); return s }
	fun loading(url: String) { states[url] = State.Loading; used[url] = clock() }
	fun failed(url: String) { states[url] = State.Failed }

	fun ready(url: String, tex: T, w: Int, h: Int): List<T> {
		states[url] = State.Ready(tex, w, h)
		used[url] = clock()
		val evicted = ArrayList<T>()
		val ready = states.count { it.value is State.Ready }
		if (ready > max) {
			val victims = states.entries.filter { it.value is State.Ready && it.key != url }.sortedBy { used[it.key] ?: 0L }.take(ready - max)
			for (v in victims) { (v.value as State.Ready<T>).let { evicted += it.tex }; states.remove(v.key); used.remove(v.key) }
		}
		return evicted
	}

	val size: Int get() = states.size

	companion object {
		const val MAX_BYTES = 4 * 1024 * 1024
		const val MAX_SIDE = 1024
		fun allowed(url: String) = url.startsWith("https://") && WikiHosts.allowed(url)
		fun fileName(url: String): String =
			MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".img"
	}
}

object WikiImages {
	class Tex(val id: Identifier)

	private val core = WikiImageCore<Tex>()
	private val gate = Semaphore(4)
	private val next = AtomicInteger()
	private val dir: Path? by lazy { runCatching { dev.ashtonite365.apex.gui.config.storage.ApexConfigLoader.configFolder.resolve("wiki-cache").resolve("img") }.getOrNull() }

	fun get(url: String): WikiImageCore.State.Ready<Tex>? {
		when (val s = core.get(url)) {
			is WikiImageCore.State.Ready -> return s
			WikiImageCore.State.Loading, WikiImageCore.State.Failed -> return null
			null -> { start(url); return null }
		}
	}

	fun failed(url: String): Boolean = core.get(url) == WikiImageCore.State.Failed

	private fun start(url: String) {
		if (!WikiImageCore.allowed(url)) { core.failed(url); return }
		core.loading(url)
		Apex.coroutineScope.launch(Dispatchers.IO) {
			val bytes = runCatching { gate.withPermit { bytes(url) } }.getOrNull()
			val image = bytes?.let { b -> runCatching { decode(b) }.getOrNull() }
			Client.onClientThread {
				if (image == null) { core.failed(url); return@onClientThread }
				runCatching {
					val id = Apex.identifier("wiki_image_${next.getAndIncrement()}")
					Client.textures.register(id, DynamicTexture({ id.path }, image))
					for (old in core.ready(url, Tex(id), image.width, image.height)) runCatching { Client.textures.release(old.id) }
				}.onFailure { image.close(); core.failed(url) }
			}
		}
	}

	private suspend fun bytes(url: String): ByteArray? {
		val f = dir?.resolve(WikiImageCore.fileName(url))
		if (f != null && Files.isRegularFile(f)) runCatching { return Files.readAllBytes(f) }
		val req = dev.ashtonite365.apex.util.net.HttpUtil.request(url)
		req.request.setHeader("User-Agent", WikiHosts.userAgent(Apex.version.friendlyString)).timeout(Duration.ofSeconds(15))
		val resp = withTimeout(20_000) { req.execute(HttpResponse.BodyHandlers.ofByteArray()).await() }
		if (resp.statusCode() != 200) return null
		val body = resp.body()
		if (body.isEmpty() || body.size > WikiImageCore.MAX_BYTES) return null
		if (f != null) runCatching {
			Files.createDirectories(f.parent)
			val tmp = f.resolveSibling(f.fileName.toString() + ".tmp")
			Files.write(tmp, body)
			Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
		}
		return body
	}

	private fun decode(b: ByteArray): NativeImage? {
		val png = b.size > 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte()
		val img = if (png) NativeImage.read(ByteArrayInputStream(b)) else {
			val bi = javax.imageio.ImageIO.read(ByteArrayInputStream(b)) ?: return null
			NativeImage(bi.width, bi.height, false).also { ni ->
				for (yy in 0 until bi.height) for (xx in 0 until bi.width) ni.setPixel(xx, yy, bi.getRGB(xx, yy))
			}
		}
		if (img.width > WikiImageCore.MAX_SIDE || img.height > WikiImageCore.MAX_SIDE || img.width < 1 || img.height < 1) { img.close(); return null }
		return img
	}
}
