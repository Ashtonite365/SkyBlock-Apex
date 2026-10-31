/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: the game side of HypixelPackDownload.kt, in every build. Replaces the bundled "Hypixel SkyBlock
 * (built-in copy)" pack: the same keep-list ([BuiltinHypixelPack.serves]) is applied to Hypixel's own pack, downloaded
 * from Hypixel's API, checked against Hypixel's SHA-1 and cached under config/apex/cache/; the cached copy is offered
 * to the client pack list exactly like the built-in copy was (required, top of the client packs, so Hypixel's live
 * server pack still sits above it and the "Skip Hypixel Resource Pack" toggles behave as before).
 * Download off the main thread with timeouts; no automatic retries. Failures fall back silently to the last good cache
 * (log only). Only with no cache at all: one toast per session; /apex hypixelpack retries and links to Hypixel.
 */
package dev.ashtonite365.apex.features.misc

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.util.Optional
import java.util.function.Consumer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.repository.RepositorySource
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.util.data.ManagedConfig
import net.minecraft.network.chat.ClickEvent
import net.minecraft.ChatFormatting
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.tr

object DownloadedHypixelPack : RepositorySource {
	const val PACK_ID = "apex/hypixel_skyblock_downloaded"
	private const val UA = "SkyBlockApex (Minecraft mod; fetches Hypixel's SkyBlock resource pack from api.hypixel.net)"

	@Volatile private var root: Path? = null
	@Volatile private var started = false
	@Volatile private var reloadPending = false
	@Volatile private var noticePending = false
	@Volatile private var unavailablePending = false
	private val gate = RetryGate()
	private val policy = PackNoticePolicy()

	private val cache by lazy { HypixelPackCache(Client.game.gameDirectory.toPath().resolve("config/apex/cache")) }

	private val http by lazy {
		HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()
	}

	private fun get(url: String, maxBytes: Long): ByteArray {
		require(url == HypixelPackApi.PACKS_URL || HypixelPackApi.allowedDownload(url)) { "refusing to fetch $url" }
		val req = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(60)).header("User-Agent", UA).GET().build()
		val res = http.send(req, HttpResponse.BodyHandlers.ofInputStream())
		res.body().use { inp ->
			if (res.statusCode() != 200) throw java.io.IOException("HTTP ${res.statusCode()} from $url")
			val bytes = inp.readNBytes((maxBytes + 1).toInt())
			if (bytes.size > maxBytes) throw java.io.IOException("response from $url is over $maxBytes bytes")
			return bytes
		}
	}

	private val notifier = object : HypixelPackFetcher.Notifier {
		override fun ready(root: Path, downloaded: Boolean) {
			if (DownloadedHypixelPack.root != root) reloadPending = true
			DownloadedHypixelPack.root = root
		}

		override fun unavailable(reason: String) {
			Apex.logger.warn("Hypixel SkyBlock pack unavailable (no verified cache), continuing without it: $reason")
		}
	}

	fun start() {
		if (started) return
		started = true
		ClientTickEvents.END_CLIENT_TICK.register { tick() }
		fetchAsync()
	}

	fun fetchAsync(): Boolean {
		if (!gate.tryAcquire()) return false
		Apex.coroutineScope.launch(Dispatchers.IO) {
			val result = runCatching { HypixelPackFetcher(cache, ::get, notifier).run() }
				.getOrElse { HypixelPackFetcher.Result.Unavailable("${it.javaClass.simpleName}: ${it.message}") }
			when (result) {
				is HypixelPackFetcher.Result.Fresh -> Apex.logger.info("Hypixel SkyBlock pack ready (${if (result.downloaded) "downloaded" else "cache up to date"}): ${result.root}")
				is HypixelPackFetcher.Result.FromCache -> Apex.logger.warn("Hypixel SkyBlock pack: using the last good cached copy (${result.reason})")
				is HypixelPackFetcher.Result.Unavailable -> {}
			}
			when (policy.onResult(result)) {
				PackNoticePolicy.Show.NOTICE -> noticePending = true
				PackNoticePolicy.Show.UNAVAILABLE -> unavailablePending = true
				PackNoticePolicy.Show.NONE -> {}
			}
		}
		return true
	}

	private fun tick() {
		if (Client.gui.overlay() != null) return
		val inWorld = Client.game.level != null
		if (reloadPending && !inWorld) {
			reloadPending = false
			Client.game.reloadResourcePacks()
			return
		}
		if (noticePending) {
			noticePending = false
			SystemToast.add(Client.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
				tr("apex.hypixelpack.notice.title", "Hypixel SkyBlock pack"),
				tr("apex.hypixelpack.notice", "(C) Hypixel Inc., under its own licence, downloaded from Hypixel, not distributed by Apex"))
		}
		if (unavailablePending) {
			unavailablePending = false
			SystemToast.add(Client.gui.toastManager(), SystemToast.SystemToastId.PACK_LOAD_FAILURE,
				tr("apex.hypixelpack.unavailable.title", "Hypixel SkyBlock pack unavailable"),
				tr("apex.hypixelpack.unavailable", "Continuing without it. Retry with /apex hypixelpack"))
		}
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.MISC, "hypixelpack", "Retry downloading Hypixel's SkyBlock resource pack") {
			onRun {
				val started = fetchAsync()
				Client.showChat(Component.literal(if (started) "Downloading Hypixel's SkyBlock resource pack from Hypixel. " else "Already tried in the last 30 seconds. ")
					.withStyle(ChatFormatting.GRAY)
					.append(Component.literal("hypixel.net").withStyle { it.withColor(ChatFormatting.AQUA).withUnderlined(true).withClickEvent(ClickEvent.OpenUrl(URI(HypixelPackApi.HYPIXEL_URL))) }))
			}
		}
	}

	private fun currentRoot(): Path? {
		root?.let { return it }
		return runCatching {
			cache.verified()?.let { cache.extract(it, HypixelPackFetcher.NOTICE) }
		}.getOrNull()?.also { root = it }
	}

	override fun loadPacks(consumer: Consumer<Pack>) {
		val dir = currentRoot() ?: return
		val info = PackLocationInfo(PACK_ID,
			tr("apex.resourcepack.hypixelskyblock.downloaded", "Hypixel SkyBlock (downloaded from Hypixel, cached)"),
			PackSource.BUILT_IN, Optional.empty())
		val pack = Pack.readMetaAndCreate(info, PathPackResources.PathResourcesSupplier(dir), PackType.CLIENT_RESOURCES,
			PackSelectionConfig(true, Pack.Position.TOP, false))
		if (pack != null) consumer.accept(pack)
	}
}
