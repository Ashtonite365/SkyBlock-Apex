// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

import dev.ashtonite365.apex.features.menus.support.SlotText
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.util.customgui.customGui

data class MenuBounds(val x: Int, val y: Int, val w: Int, val h: Int) {
	companion object {

		fun centred(screenW: Int, screenH: Int, maxW: Int, maxH: Int, scalePct: Int = 100, margin: Int = 12): MenuBounds {
			val w = minOf(screenW - margin, maxW * scalePct / 100)
			val h = minOf(screenH - margin, maxH * scalePct / 100)
			return MenuBounds((screenW - w) / 2, (screenH - h) / 2, w, h)
		}
	}
}

interface CustomMenu {

	val id: String

	val displayName: String

	val title: Regex

	val slotPredicate: ((title: String, slots: List<SlotText>) -> Boolean)? get() = null

	val settings: MenuSettings

	val selfHosted: Boolean get() = false

	fun create(screen: AbstractContainerScreen<*>): MenuView? = null

	val enabled: Boolean get() = MenuSections.allows(this) && settings.apex

	val defaultSize: Pair<Int, Int> get() = 520 to 290

	val section: String? get() = runCatching { MenuSections.sectionOf(settings.name) }.getOrNull()

	val chestFrame: Boolean get() = false
}

object MenuRegistry {
	private val menus = mutableListOf<CustomMenu>()
	val all: List<CustomMenu> get() = menus

	fun registerFirst(menu: CustomMenu) {
		require(menus.none { it.id == menu.id }) { "Duplicate custom menu id '${menu.id}'" }
		menus.add(0, menu)
	}

	fun register(menu: CustomMenu) {
		require(menus.none { it.id == menu.id }) { "Duplicate custom menu id '${menu.id}'" }
		menus += menu
	}

	internal fun clearForTests() = menus.clear()

	fun strip(title: String) = title.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").trim()

	val VANILLA_OWNED: List<Pair<String, Regex>> = listOf()

	fun vanillaOwner(title: String): String? = strip(title).let { t -> VANILLA_OWNED.firstOrNull { it.second.matches(t) }?.first }

	fun match(title: String): CustomMenu? = if (vanillaOwner(title) != null) null else strip(title).let { t -> menus.firstOrNull { it.title.containsMatchIn(t) } }

	fun match(title: String, slots: List<SlotText>): CustomMenu? = match(title)
		?: strip(title).let { t -> if (slots.isEmpty() || vanillaOwner(t) != null) null else menus.firstOrNull { m -> m.slotPredicate != null && isOn(m) && m.slotPredicate?.invoke(t, slots) == true } }

	fun isOn(menu: CustomMenu): Boolean =
		try { MenuSections.allows(menu) } catch (e: RuntimeException) { true } && try { menu.enabled } catch (e: RuntimeException) { true }

	fun hasSlotMatchers() = menus.any { it.slotPredicate != null && isOn(it) }

	fun switchedOff(title: String): Boolean = !denied(title) && match(title)?.let { m -> !isOn(m) } == true

	fun menuForOwner(ownerId: String): CustomMenu? = menus.firstOrNull { it.id == ownerId }
		?: menus.filter { ownerId.startsWith(it.id + "-") }.maxByOrNull { it.id.length }

	fun ownerSwitchedOff(ownerId: String?): Boolean = ownerId != null && menuForOwner(ownerId)?.let { m -> !isOn(m) } == true

	val DENY_TITLES = listOf(
		"experimentation table", "experiment", "experiments", "chronomatron", "ultrasequencer", "superpairs", "harp", "melody",
		"click in order", "correct all the panes", "change all to same color", "select all the",
		"what starts with", "click the button on time", "navigate the maze", "terminal",
	)

	val DENY_EXCEPTIONS = setOf("melody ♫", "melody", "experimentation table")

	private val DENY_PHRASES = MenuTitles.PhraseList(DENY_TITLES)

	fun denied(title: String) = strip(title).lowercase().let { t -> t !in DENY_EXCEPTIONS && DENY_PHRASES.matches(t) }

	sealed interface Resolution {

		data object None : Resolution

		data class Vanilla(val menu: CustomMenu, val sectionOff: Boolean = false) : Resolution

		data class SelfHosted(val menu: CustomMenu) : Resolution

		data class Attach(val menu: CustomMenu, val view: MenuView) : Resolution

		data class Fallback(val menu: CustomMenu, val reason: String, val error: Throwable? = null) : Resolution
	}

	fun resolve(title: String, create: (CustomMenu) -> MenuView?): Resolution = resolve(title, null, create)

	fun resolve(title: String, slots: List<SlotText>?, create: (CustomMenu) -> MenuView?): Resolution {
		if (denied(title)) return Resolution.None
		val menu = (if (slots == null) match(title) else match(title, slots)) ?: return Resolution.None
		if (!MenuSections.allows(menu)) return Resolution.Vanilla(menu, sectionOff = true)
		if (!isOn(menu)) return Resolution.Vanilla(menu)
		if (menu.selfHosted) return Resolution.SelfHosted(menu)
		return try {
			create(menu)?.let { Resolution.Attach(menu, it) } ?: Resolution.Fallback(menu, "could not parse the page")
		} catch (e: Throwable) {
			Resolution.Fallback(menu, "create threw ${e.javaClass.simpleName}: ${e.message}", e)
		}
	}

	fun describe(r: Resolution) = when (r) {
		Resolution.None -> "none"
		is Resolution.Vanilla -> if (r.sectionOff) "${r.menu.id} (section off)" else "${r.menu.id} (normal)"
		is Resolution.SelfHosted -> r.menu.id
		is Resolution.Attach -> r.menu.id
		is Resolution.Fallback -> "${r.menu.id} (fallback)"
	}

	const val SLOT_MATCH_TICKS = 60

	private val slotDumpGate = dev.ashtonite365.apex.features.menus.support.SlotDumpGate()

	@Subscribe
	fun onContentUpdate(event: dev.ashtonite365.apex.events.ChestInventoryUpdateEvent) {
		if (event !is dev.ashtonite365.apex.events.ChestInventoryUpdateEvent.Multi) return
		val screen = event.inventory as? AbstractContainerScreen<*> ?: return
		if (!MenuSettings.Global.debugSlotDump || switchedOff(screen.title.bareText)) return
		runCatching {
			val (texts, _) = dev.ashtonite365.apex.features.menus.support.MenuSlots.snapshot(screen)
			val title = screen.title.bareText
			val tag = slotDumpGate.contentUpdate(screen.menu.containerId, slotDumpGate.contentKey(title, texts), texts.size,
				MenuSettings.Global.debugSlotDump) ?: return
			dev.ashtonite365.apex.Apex.logger.debug(dev.ashtonite365.apex.features.menus.support.MenuDebug.dump(title, screen.menu.containerId, texts)
				.replaceFirst("[ApexSlotDump] id=", "[ApexSlotDump] $tag id="))
		}
	}

	@Subscribe
	fun onInit(event: ClientInitEvent) {
		ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, sw, sh ->
			if (screen !is AbstractContainerScreen<*>) return@AfterInit

			if (switchedOff(screen.title.bareText)) return@AfterInit

			if (!dev.ashtonite365.apex.features.menus.style.MenuStyle.state.customMenus) return@AfterInit

			slotDumpGate.opened(screen.menu.containerId)
			ScreenEvents.afterTick(screen).register(ScreenEvents.AfterTick { _ ->
				if (!MenuSettings.Global.debugSlotDump || switchedOff(screen.title.bareText)) return@AfterTick
				runCatching {
					val (texts, _) = dev.ashtonite365.apex.features.menus.support.MenuSlots.snapshot(screen)
					if (slotDumpGate.offer(screen.menu.containerId, dev.ashtonite365.apex.features.menus.support.MenuSlots.signature(screen), texts.size, System.currentTimeMillis(),
							everyUpdate = dev.ashtonite365.apex.features.menus.support.SlotDumpGate.everyUpdate(screen.title.bareText)))
					{
						slotDumpGate.dumped(screen.menu.containerId, slotDumpGate.contentKey(screen.title.bareText, texts))
						dev.ashtonite365.apex.Apex.logger.debug(dev.ashtonite365.apex.features.menus.support.MenuDebug.dump(screen.title.bareText, screen.menu.containerId, texts))
					}
				}
			})
			val raw = screen.title.bareText
			val r = resolve(raw) { it.create(screen) }
			if (MenuSettings.Global.logTitles) MenuLog.title(raw, screen.menu.slots.size, if (denied(raw)) "denied" else vanillaOwner(raw)?.let { "$it (vanilla, no Apex view)" } ?: describe(r))
			when (r) {
				is Resolution.Fallback -> { MenuLog.fallback(r.menu.id, raw, r.reason, r.error); dev.ashtonite365.apex.features.menus.support.ApexFallback.handle(screen, r.menu.id, r.reason) }
				is Resolution.Attach -> attach(screen, r.menu, r.view, sw, sh)
				else -> {}
			}

			fun genericOk(claimed: Boolean) = coveredByGeneric(r) && dev.ashtonite365.apex.features.menus.generic.GenericWrapper.eligible(
					screen.menu is net.minecraft.world.inventory.ChestMenu, dev.ashtonite365.apex.util.SkyBlockSession.inSkyBlock,
					dev.ashtonite365.apex.features.menus.style.MenuStyle.state.wrapAll, denied(raw), vanillaOwner(raw) != null,
					claimed || dev.ashtonite365.apex.features.menus.generic.GenericWrapper.featureOwned(raw,
						screen.customGui.let { it != null && it !is MenuFront }))

			var generic = if (genericOk(false)) attachGeneric(screen, sw, sh) else null

			var claimedBySlots = false
			if (generic == null && coveredByGeneric(r)) {
				var lateTicks = 0
				ScreenEvents.afterTick(screen).register(ScreenEvents.AfterTick { _ ->
					if (generic != null || claimedBySlots || ++lateTicks > SLOT_MATCH_TICKS) return@AfterTick
					if (dev.ashtonite365.apex.features.menus.generic.GenericWrapper.lateAttach(generic != null, genericOk(false)))
						generic = attachGeneric(screen, screen.width, screen.height)
				})
			}

			if (r == Resolution.None && !denied(raw) && vanillaOwner(raw) == null && hasSlotMatchers()) {
				var ticks = 0
				var done = false
				ScreenEvents.afterTick(screen).register(ScreenEvents.AfterTick { _ ->
					if (done || ++ticks > SLOT_MATCH_TICKS || !hasSlotMatchers()) return@AfterTick
					val texts = runCatching { dev.ashtonite365.apex.features.menus.support.MenuSlots.snapshot(screen).first }.getOrNull() ?: return@AfterTick
					if (texts.isEmpty()) return@AfterTick
					val r2 = resolve(raw, texts) { it.create(screen) }
					if (r2 == Resolution.None) return@AfterTick
					done = true
					if (r2 is Resolution.Attach) claimedBySlots = true

					if (retiresGeneric(r2 is Resolution.Attach)) generic?.let { g -> g.retire(); screen.customGui = null }
					if (MenuSettings.Global.logTitles) MenuLog.title(raw, screen.menu.slots.size, describe(r2) + " (slots)")
					when (r2) {
						is Resolution.Fallback -> { MenuLog.fallback(r2.menu.id, raw, r2.reason, r2.error); dev.ashtonite365.apex.features.menus.support.ApexFallback.handle(screen, r2.menu.id, r2.reason) }
						is Resolution.Attach -> attach(screen, r2.menu, r2.view, screen.width, screen.height)
						else -> {}
					}
				})
			}
		})
	}

	fun coveredByGeneric(r: Resolution) = r == Resolution.None || r is Resolution.Fallback

	fun retiresGeneric(attached: Boolean) = attached

	fun boundsFor(menu: CustomMenu, sw: Int, sh: Int, totalSlots: Int): MenuBounds {

		if (menu.chestFrame) dev.ashtonite365.apex.features.menus.support.ChestFrame.rowsFor(totalSlots).let { rows ->
			return MenuBounds.centred(sw, sh, dev.ashtonite365.apex.features.menus.support.ChestFrame.WIDTH, dev.ashtonite365.apex.features.menus.support.ChestFrame.heightFor(rows))
		}
		val s = menu.settings.customisation
		return MenuBounds.centred(sw, sh, s.width ?: menu.defaultSize.first, s.height ?: menu.defaultSize.second, s.scalePct)
	}

	fun attachGeneric(screen: AbstractContainerScreen<*>, sw: Int, sh: Int): MenuSession {
		var hidden = false
		val view = dev.ashtonite365.apex.features.menus.generic.GenericStyledView(screen)
		val session = MenuSession(screen, view,
			showLabel = { "§6Apex menu" + MenuSettings.Global.showVanillaKeyLabel() },
			toggleKey = MenuSettings.Global::showVanillaMatches,
			isHidden = { hidden }, setHiddenState = { hidden = it }, menuId = "generic")
		MenuHost.attach(session, MenuBounds(0, 0, sw, sh))
		return session
	}

	fun attach(screen: AbstractContainerScreen<*>, menu: CustomMenu, view: MenuView, sw: Int, sh: Int): MenuSession {
		var hidden = false
		val session = MenuSession(screen, view,
			showLabel = { "§6Apex menu" + MenuSettings.Global.showVanillaKeyLabel() },
			toggleKey = MenuSettings.Global::showVanillaMatches,
			isHidden = { hidden }, setHiddenState = { hidden = it }, menuId = menu.id)
		MenuHost.attach(session, boundsFor(menu, sw, sh, screen.menu.slots.size))
		return session
	}
}
