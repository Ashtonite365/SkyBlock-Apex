// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.wiki

import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.apache.logging.log4j.LogManager
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.gui.config.storage.ConfigFixEvent
import dev.ashtonite365.apex.gui.config.storage.ConfigStorageClass
import dev.ashtonite365.apex.repo.ReforgeStore
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.customDataTag
import dev.ashtonite365.apex.util.stackUnderCursor
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.petBlob
import dev.ashtonite365.apex.util.hypixelIdText
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText

object ItemWiki {
	@Config
	object TConfig : ManagedConfig("item-wiki", Category.INVENTORY) {
		val enabled by toggle("enabled") { true }
		val holdTenths by integer("hold-tenths", 3, 50) { 15 }
		val key by keyBinding("key") { com.mojang.blaze3d.platform.InputConstants.KEY_J }
		val smoothFont by toggle("smooth-font") { true }
		val darkPage by toggle("dark-page") { false }
	}

	private val log = LogManager.getLogger("Apex.Wiki")
	val hold = WikiHold(1500, delayMs = 0)
	private var hoveredStack: ItemStack? = null

	private fun keyDown(screen: Screen): Boolean =
		TConfig.enabled && SkyBlockSession.inSkyBlock && TConfig.key.isBound && TConfig.key.isPressed(atLeast = true)

			&& !dev.ashtonite365.apex.util.InventoryKeyClose.textInputFocused(screen)

	@Subscribe
	fun onRender(event: ScreenRenderPostEvent) {
		try { tick(event) } catch (t: Throwable) { warnOnce(t) }
	}

	private fun tick(event: ScreenRenderPostEvent) {
		if (event.screen is ItemWikiScreen) return
		hold.holdMs = TConfig.holdTenths.coerceIn(3, 50) * 100L
		val down = keyDown(event.screen)
		val stack = if (down) event.screen.stackUnderCursor?.takeIf { !it.isEmpty } else null
		diagnose(event.screen, down, stack != null)
		hoveredStack = stack

		val s = hold.update(System.nanoTime() / 1_000_000L, down, stack?.let(::hoverKey))
		if (s == WikiHold.State.Fire && stack != null) {
			val target = resolve(stack)
			hoveredStack = null
			Client.screen = ItemWikiScreen(event.screen, target, stack.hoverName.bareText, stack.copy())
		}
	}

	private fun hoverKey(stack: ItemStack): Any {
		val acc = stack.view()
		return listOf(acc.repoItemId?.skyblockId, stack.hoverName.bareText)
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		val stack = hoveredStack ?: return
		if (event.stack !== stack && hoverKey(event.stack) != hoverKey(stack)) return
		val screen = Client.screen ?: return
		if (screen is ItemWikiScreen || !keyDown(screen)) return
		val l = ItemWikiHint.lines(hold.state, TConfig.key.format().string) ?: return
		event.lines += Component.literal("§7Hold §e[${l.keyName}]§7 to view wiki")
		event.lines += Component.literal("§a" + "▮".repeat(l.filled) + "§8" + "▮".repeat(l.total - l.filled))
	}

	private var lastDiag = 0L
	private var lastDiagKey = ""

	private fun diagnose(screen: Screen, down: Boolean, hasStack: Boolean) {
		val physical = runCatching { TConfig.key.isBound && TConfig.key.button.isPressed() }.getOrDefault(false)
		if (!physical) return
		val why = when {
			!TConfig.enabled -> "disabled"
			!SkyBlockSession.inSkyBlock -> "not in SkyBlock"
			!down && dev.ashtonite365.apex.util.InventoryKeyClose.textInputFocused(screen) -> "text input focused on ${screen.javaClass.simpleName}"
			!down -> "modifiers do not match ${TConfig.key}"
			!hasStack -> "no item under the cursor on ${screen.javaClass.simpleName}"
			else -> "holding (${hold.state})"
		}
		val now = System.currentTimeMillis()
		if (why == lastDiagKey && now - lastDiag < 2_000) return
		if (now - lastDiag < 2_000 && why.startsWith("holding")) return
		lastDiag = now; lastDiagKey = why
		log.info("[ApexWiki] key held: {}", why)
	}

	@Volatile private var warned = false
	private fun warnOnce(t: Throwable) {
		if (warned) return
		warned = true
		log.warn("[Apex] Item Wiki hold disabled for this frame: $t")
	}

	private object RepoView : WikiRepoView {
		private val url = Regex("https?://${Regex.escape(WikiHosts.HOST)}/w/[^\\s\"'<>§]+")
		override fun ids(): Collection<String> = runCatching { RepoManager.allItemIds() }.getOrDefault(emptyList())
		override fun wikiUrl(id: String): String? = runCatching {
			RepoManager.getCoreItem(ItemId(id))?.info?.firstNotNullOfOrNull { url.find(it)?.value }
		}.getOrNull()
		override fun displayName(id: String): String? = runCatching { RepoManager.getCoreItem(ItemId(id))?.displayName }.getOrNull()
	}

	private var resolver: ItemWikiResolver? = null
	private var resolverSize = -1

	private fun resolver(): ItemWikiResolver {
		val n = RepoView.ids().size
		resolver?.takeIf { n == resolverSize }?.let { return it }
		val reforges = runCatching { ReforgeStore.allReforges.map { it.prefixName } }.getOrDefault(emptyList())
		return ItemWikiResolver(RepoView, reforges).also { resolver = it; resolverSize = n }
	}

	fun refOf(stack: ItemStack): ItemRef {
		val acc = stack.view()
		val name = stack.hoverName.bareText
		val raw = acc.hypixelIdText
		val ench = runCatching { acc.customDataTag.getCompound("enchantments").orElse(null)?.keySet()?.toList() }.getOrNull().orEmpty()
		val isBook = raw == "ENCHANTED_BOOK"
		val ordered = if (isBook && ench.size > 1) {

			val lore = runCatching { acc.loreAccordingToNbt.joinToString("\n") { it.bareText.lowercase() } }.getOrDefault("")
			ench.sortedBy { k -> lore.indexOf(ItemWikiEnchants.pageOf(k).lowercase()).let { if (it < 0) Int.MAX_VALUE else it } }
		} else ench
		val id = when {
			isBook -> ordered.firstOrNull()?.let { "${it.uppercase()};1" } ?: "ENCHANTED_BOOK"
			else -> acc.repoItemId?.skyblockId
		}
		return ItemRef(id, name, if (isBook) ordered else emptyList(), isPet = acc.petBlob != null, isBook = isBook)
	}

	fun resolve(stack: ItemStack): WikiTarget? = runCatching { resolver().resolve(refOf(stack)) }.getOrElse { log.warn("[Apex] wiki resolve failed: $it"); null }

	private val cache by lazy { WikiCache(runCatching { dev.ashtonite365.apex.gui.config.storage.ApexConfigLoader.configFolder.resolve("wiki-cache") }.getOrNull()) }

	private fun fetch(title: String): WikiApi.Outcome = runBlocking {
		val req = dev.ashtonite365.apex.util.net.HttpUtil.request(WikiHosts.apiUrl(title))
		req.request.setHeader("User-Agent", WikiHosts.userAgent(Apex.version.friendlyString)).timeout(Duration.ofSeconds(15))
		try {
			val resp = withTimeout(20_000) { req.execute(HttpResponse.BodyHandlers.ofString()).await() }
			WikiApi.fromResponse(resp.statusCode(), resp.body(), title)
		} catch (t: kotlinx.coroutines.TimeoutCancellationException) {
			WikiApi.errorFor(null, java.util.concurrent.TimeoutException(), title)
		} catch (t: Exception) {
			WikiApi.errorFor(null, t, title)
		}
	}

	private fun search(q: String): List<String> = runBlocking {
		val req = dev.ashtonite365.apex.util.net.HttpUtil.request(WikiHosts.searchUrl(q))
		req.request.setHeader("User-Agent", WikiHosts.userAgent(Apex.version.friendlyString)).timeout(Duration.ofSeconds(15))
		runCatching { withTimeout(20_000) { req.execute(HttpResponse.BodyHandlers.ofString()).await() } }.getOrNull()
			?.takeIf { it.statusCode() == 200 }?.let { WikiApi.searchTitles(it.body()) }.orEmpty()
	}

	private val loader by lazy { WikiLoaderCore(cache, ::fetch, ::search) }

	fun load(target: WikiTarget, done: (WikiLoaderCore.Result) -> Unit) {
		Apex.coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
			val r = try { loader.load(target) } catch (t: Throwable) {
				if (t is kotlinx.coroutines.CancellationException) throw t
				WikiLoaderCore.Result.Fail(WikiApi.errorFor(null, t, target.title).message)
			}
			if (r is WikiLoaderCore.Result.Fail) log.info("[Apex] wiki '${target.title}': ${r.message}")
			Client.onClientThread { runCatching { done(r) } }
		}
	}

	@Subscribe
	fun onConfigFix(event: ConfigFixEvent) {
		event.on(1003, ConfigStorageClass.CONFIG) { ItemWikiMigration.migrate(event.data) }
	}
}
