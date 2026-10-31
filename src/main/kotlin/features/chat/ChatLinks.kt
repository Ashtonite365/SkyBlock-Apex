package dev.ashtonite365.apex.features.chat

import com.mojang.blaze3d.platform.NativeImage
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.joml.Vector2i
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.future.await
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.ActiveTextCollector.ClickableStyleFinder
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.resources.Identifier
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ModifyChatEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mapComponentTree
import dev.ashtonite365.apex.util.net.HttpUtil
import dev.ashtonite365.apex.util.render.textureRegion

object ChatLinks {
	val identifier: String get() = "chat-links"

	private const val PREVIEW_W = 320
	private const val PREVIEW_H = 180

	@Config
	object TConfig : ManagedConfig(category = Category.CHAT, name = identifier) {
		val enableLinks by toggle("links-enabled", default = { true })
		val imageEnabled by toggle("image-enabled", default = { true })
		val allowAllHosts by toggle("allow-all-hosts", default = { false })
		val allowedHosts by string("allowed-hosts", default = { "cdn.discordapp.com,media.discordapp.com,media.discordapp.net,i.imgur.com" })
		val actualAllowedHosts: List<String> get() = allowedHosts.split(',').mapNotNull { host -> host.trim().takeIf(String::isNotEmpty) }
		val position by position("position", PREVIEW_W, PREVIEW_H) { Vector2i(0, 0) }
	}

	val urlRegex = "https://[^. ]+\\.[^ ]+(\\.?(\\s|$))".toRegex()

	data class Image(val textureId: Identifier, val pixelsWide: Int, val pixelsHigh: Int)

	val imageCache: MutableMap<String, Deferred<Image?>> = ConcurrentHashMap()

	private val textureNumbers = AtomicInteger()

	val imageExtensions: List<String> = "jpg png gif jpeg".split(' ')

	fun isImageUrl(url: String): Boolean {
		val ending = url.substringAfterLast('.')
		return imageExtensions.contains(ending.lowercase())
	}

	private fun hostAllowed(url: URI): Boolean = TConfig.allowAllHosts || url.host in TConfig.actualAllowedHosts

	private fun previewable(url: String): URI? {
		if (!TConfig.imageEnabled || !isImageUrl(url)) return null
		val parsed = runCatching { URI(url) }.getOrNull() ?: return null
		return parsed.takeIf { hostAllowed(it) }
	}

	const val MAX_CACHED_IMAGES = 48
	private val cacheOrder = java.util.concurrent.ConcurrentLinkedQueue<String>()

	private fun download(url: URI): Deferred<Image?> {
		val key = url.toString()
		imageCache[key]?.let { return it }
		val made = fetch(url)
		cacheOrder.add(key)
		while (imageCache.size > MAX_CACHED_IMAGES) {
			val old = cacheOrder.poll() ?: break
			val gone = imageCache.remove(old) ?: continue
			@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
			if (gone.isCompleted) runCatching { gone.getCompleted() }.getOrNull()?.let { img -> Client.onClientThread { runCatching { Client.textures.release(img.textureId) } } }
			else gone.cancel()
		}
		return made
	}

	private fun fetch(url: URI): Deferred<Image?> = imageCache.getOrPut(url.toString()) {
		Apex.coroutineScope.async {
			runCatching {
				val bytes = HttpUtil.request(url).forInputStream().await().use { it.readAllBytes() }
				val picture = NativeImage.read(bytes)
				val id = Apex.identifier("dynamic_image_preview${textureNumbers.getAndIncrement()}")
				CompletableFuture.supplyAsync({
					Client.textures.register(id, DynamicTexture({ id.toString() }, picture))
				}, Client.game).await()
				Image(id, picture.width, picture.height)
			}.getOrNull()
		}
	}

	private fun linkPart(url: URI): MutableComponent {
		val text = url.toString()
		previewable(text)?.let { download(it) }
		return Component.literal(text).withStyle { style ->
			style.withColor(ChatFormatting.AQUA).withUnderlined(true)
				.withClickEvent(ClickEvent.OpenUrl(url))
				.withHoverEvent(HoverEvent.ShowText(Component.literal(text)))
		}
	}

	private fun linkify(node: Component): Component? {
		val text = (node.contents as? PlainTextContents)?.text() ?: return null
		if ("://" !in text) return null
		val rebuilt = Component.empty().withStyle(node.style)
		val plain = StringBuilder()
		var cursor = 0
		var match = urlRegex.find(text)
		while (match != null) {
			val url = runCatching { URI(match.value) }.getOrNull() ?: break
			plain.append(text, cursor, match.range.first)
			if (plain.isNotEmpty()) rebuilt.append(plain.toString())
			plain.clear()
			rebuilt.append(linkPart(url))
			cursor = match.range.last + 1
			match = match.next()
		}
		plain.append(text, cursor, text.length)
		if (plain.isNotEmpty()) rebuilt.append(plain.toString())
		return rebuilt
	}

	@Subscribe
	fun onModifyChat(event: ModifyChatEvent) {
		if (TConfig.enableLinks) event.replaceWith = event.replaceWith.mapComponentTree { linkify(it) ?: it }
	}

	private fun hoveredUrl(mouseX: Int, mouseY: Int): String? {
		val finder = ClickableStyleFinder(Client.font, mouseX, mouseY).includeInsertions(false)
		Client.hud.chat.captureClickableText(finder, Client.window.guiScaledHeight, Client.hud.guiTicks, ChatComponent.DisplayMode.FOREGROUND)
		return (finder.result()?.clickEvent as? ClickEvent.OpenUrl)?.uri()?.toString()
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Subscribe
	fun onRender(it: ScreenRenderPostEvent) {
		if (it.screen !is ChatScreen || !TConfig.imageEnabled) return
		val url = hoveredUrl(it.mouseX, it.mouseY) ?: return
		val wanted = previewable(url) ?: return
		val pending = download(wanted)
		if (!pending.isCompleted) return
		val image = runCatching { pending.getCompleted() }.getOrNull() ?: return
		val shrink = minOf(1F, PREVIEW_W / image.pixelsWide.toFloat(), PREVIEW_H / image.pixelsHigh.toFloat())
		val at = TConfig.position.topLeft
		val pose = it.drawContext.pose()
		pose.pushMatrix()
		pose.translate(at.x().toFloat(), at.y().toFloat())
		pose.scale(shrink, shrink)
		it.drawContext.textureRegion(image.textureId, 0, 0, 0F, 0F, image.pixelsWide, image.pixelsHigh, image.pixelsWide, image.pixelsHigh)
		pose.popMatrix()
	}
}
