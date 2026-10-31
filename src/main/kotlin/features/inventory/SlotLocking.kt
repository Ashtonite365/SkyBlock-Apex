package dev.ashtonite365.apex.features.inventory

import com.mojang.blaze3d.platform.InputConstants
import kotlinx.serialization.serializer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.inventory.Slot
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.events.HandledScreenForegroundEvent
import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent
import dev.ashtonite365.apex.events.HandledScreenKeyReleasedEvent
import dev.ashtonite365.apex.events.IsSlotProtectedEvent
import dev.ashtonite365.apex.events.ScreenChangeEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.features.menus.MenuFront
import dev.ashtonite365.apex.features.menus.style.MenuStyle
import dev.ashtonite365.apex.gui.config.storage.ApexConfigLoader
import dev.ashtonite365.apex.keybindings.GenericInputAction
import dev.ashtonite365.apex.keybindings.InputModifiers
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.util.CommonSoundEffects
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.accessors.castAccessor
import dev.ashtonite365.apex.util.customgui.customGui
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.customDataTag
import dev.ashtonite365.apex.util.lime
import dev.ashtonite365.apex.util.mc.ItemView
import dev.ashtonite365.apex.util.mc.ContainerSlotLookup.slotNumbered
import dev.ashtonite365.apex.util.mc.SlotUtils.swapWithHotBar
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.mc.isEmpty
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.red
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.skyblock.SkyBlockItems
import dev.ashtonite365.apex.util.itemUuid
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.bareText

enum class BindLinkStyle : StringRepresentable {
	EVERYTHING,
	ONLY_BOXES,
	NOTHING;

	override fun getSerializedName(): String = name
}

object SlotLocking {
	@Config
	object TConfig : ManagedConfig("slot-locking", Category.INVENTORY) {
		val lockSlotKey by keyBinding("lock") { InputConstants.KEY_L }
		val lockItemKey by keyBindingExact("lock-uuid") {
			SavedKeyBinding.keyWithMods(InputConstants.KEY_L, InputModifiers.of(shift = true))
		}
		val bindKey by keyBinding("bind") { InputConstants.KEY_L }
		val bindingsLock by toggle("lock-bind") { false }
		val bindNeedsShift by toggle("require-quick-move") { true }
		val bindLines by choice("bind-render") { BindLinkStyle.ONLY_BOXES }
		val bindInInventoryOnly by toggle("bind-only-in-inv") { false }
		val allowMultiBinding by toggle("multi-bind") { true }
		val guardHuntingBoxes by toggle("hunting-box") { false }
		val dungeonDrops by toggle("drop-in-dungeons") { true }
		val protectMessage by toggle("drop-message") { true }
		val protectSound by toggle("drop-sound") { true }
	}

	@Config
	object PerIslandLegacy : ProfileSpecificDataHolder<LegacyProfileLocks>(serializer(), SlotLockStore.LEGACY_PREFIX, ::LegacyProfileLocks)

	@Config
	object GlobalConfig : DataHolder<GlobalSlotLocks>(serializer(), SlotLockStore.PREFIX, ::GlobalSlotLocks)

	val globalLockFile get() = ApexConfigLoader.storageFolder.resolve("${SlotLockStore.PREFIX}.json")

	val globalData: GlobalSlotLocks
		get() {
			ensureMigrated()
			return GlobalConfig.data
		}

	private var migrationChecked = false

	fun ensureMigrated() {
		if (migrationChecked) return
		val current = runCatching { GlobalConfig.data }.getOrNull() ?: return
		migrationChecked = true
		if (current.migratedFromPerIsland) return
		Faults.attempt("Slot locking: could not merge the old per-island locks") {
			val inMemory = runCatching {
				PerIslandLegacy.keys().mapNotNull { key ->
					PerIslandLegacy.saveTo(key)[SlotLockStore.LEGACY_PREFIX]?.let { Apex.json.decodeFromJsonElement(LegacyProfileLocks.serializer(), it) }
				}
			}.getOrDefault(listOf())
			val report = SlotLockStore.migrateOnce(
				current,
				globalLockFile,
				ApexConfigLoader.profilePath,
				kotlin.io.path.Path("backups").resolve(Apex.MOD_ID).resolve("slot-locks-per-island-${System.currentTimeMillis()}"),
				inMemory,
			)
			if (report != null) ApexConfigLoader.debugLogger.log("Slot locking: merged old per-island locks into one global set: $report")
		}
	}

	fun saveNow() {
		Faults.attempt("Slot locking: could not save locks") {
			SlotLockStore.save(globalLockFile, GlobalConfig.data)
		}
	}

	private val Slot.inPlayerInventory get() = container is Inventory

	private fun ItemView.huntingBox(): Boolean =
		repoItemId == SkyBlockItems.HUNTING_TOOLKIT || customDataTag.get("tool_kit") != null

	private fun menuFacts(screen: AbstractContainerScreen<*>?): SlotLockRules.MenuFacts? {
		screen ?: return null
		val chest = screen.menu as? ChestMenu
		val container = chest?.container
		val keyStack = container?.takeIf { it.containerSize >= 9 }?.getItem(container.containerSize - 5)?.takeUnless { it.isEmpty }
		val key = keyStack?.view()
		return SlotLockRules.MenuFacts(
			chest = chest != null,
			containerSize = container?.containerSize ?: 0,
			title = screen.title.bareText,
			keyItemName = key?.displayNameAccordingToNbt?.bareText,
			keyItemLastLore = key?.loreAccordingToNbt?.lastOrNull()?.bareText,
		)
	}

	fun isSalvageScreen(screen: AbstractContainerScreen<*>?): Boolean =
		screen != null && SlotLockRules.salvageMenu(screen.title.bareText)

	fun isTradeScreen(screen: AbstractContainerScreen<*>?): Boolean =
		menuFacts(screen)?.let(SlotLockRules::tradeMenu) ?: false

	fun isNpcShop(screen: AbstractContainerScreen<*>?): Boolean =
		menuFacts(screen)?.let(SlotLockRules::npcShop) ?: false

	private fun inSellingMenu(screen: AbstractContainerScreen<*>?) =
		isNpcShop(screen) || isTradeScreen(screen) || isSalvageScreen(screen)

	@Subscribe
	fun guardSalvageButton(event: IsSlotProtectedEvent) {
		val button = event.slot?.takeIf { it.hasItem() } ?: return
		if (button.item.view().displayNameAccordingToNbt.bareText != "Salvage Items") return
		val queued = (0 until button.containerSlot).map { button.container.getItem(it) }
		val verdicts = queued.map {
			IsSlotProtectedEvent.shouldBlockInteraction(null, ContainerInput.THROW, IsSlotProtectedEvent.MoveOrigin.SALVAGE, it)
		}
		if (true in verdicts) event.protectSilent()
	}

	@Subscribe
	fun guardLockedItems(event: IsSlotProtectedEvent) {
		val guarded = SlotLockRules.itemGuarded(
			event.actionType, inSellingMenu(Client.containerScreen), event.slot?.inPlayerInventory == true, TConfig.guardHuntingBoxes,
		) {
			event.itemStack.view().takeUnless { it.isEmpty() }?.let { stack ->
				SlotLockRules.ItemFacts(stack.huntingBox(), stack.itemUuid?.let { it in globalData.lockedUUIDs } ?: false)
			}
		}
		if (guarded) event.protect()
	}

	@Subscribe
	fun guardLockedSlots(event: IsSlotProtectedEvent) {
		val slot = event.slot ?: return
		val locks = globalData
		val guarded = SlotLockRules.slotGuarded(
			slot.inPlayerInventory,
			slot.containerSlot in locks.lockedSlots,
			TConfig.bindingsLock,
			locks.boundSlots.involving(slot.containerSlot).isNotEmpty(),
		)
		if (guarded) event.protect()
	}

	@Subscribe
	fun registerDungeonWaiver(event: ClientInitEvent) {

		val waiverLabel = "SlotLocking:unlockInDungeons"
		IsSlotProtectedEvent.subscribe(label = waiverLabel, receivesCancelled = true) {
			val keep = SlotLockRules.keepsProtectionInDungeon(
				it.isProtected,
				it.origin == IsSlotProtectedEvent.MoveOrigin.DROP_FROM_HOTBAR,
				DungeonUtil.isInActiveDungeon,
				TConfig.dungeonDrops,
			)
			if (it.isProtected && !keep) it.isProtected = false
		}
	}

	@Subscribe
	fun swapBoundSlot(event: IsSlotProtectedEvent) {
		val screen = Client.containerScreen
		val menu = screen?.menu
		val slot = event.slot
		val partners = slot?.let { globalData.boundSlots.involving(it.containerSlot) }.orEmpty()
		val verdict = SlotLockRules.boundClick(
			event.actionType, TConfig.bindNeedsShift, TConfig.bindInInventoryOnly, menu is InventoryMenu,
			menu != null, slot?.inPlayerInventory == true, partners.size,
		)
		if (verdict == SlotLockRules.BoundClick.IGNORE) return
		event.protectSilent()
		if (verdict != SlotLockRules.BoundClick.SWAP_WITH_PARTNER || menu == null) return
		val binding = partners.single()
		screen.slotNumbered(binding.inventory, true)?.swapWithHotBar(menu, binding.hotbar)
	}

	private val gesture = LockKeyGesture<Slot> { it.containerSlot }

	val pendingBindSlot: Slot? get() = gesture.remembered

	private var slotOverride: Slot? = null

	private fun hoveredSlot(screen: AbstractContainerScreen<*>): Slot? =
		slotOverride ?: screen.castAccessor().focusedSlot_Apex

	@Subscribe
	fun forgetGestureOnScreenChange(event: ScreenChangeEvent) = gesture.forget()

	private fun chime(ok: Boolean) = if (ok) CommonSoundEffects.playSuccess() else CommonSoundEffects.playFailure()

	private fun apply(effect: LockKeyEffect?) {
		effect ?: return
		val changed = effect.applyTo(globalData, exclusiveBinds = !TConfig.allowMultiBinding)
		if (changed) saveNow()
		if (effect !is LockKeyEffect.Unbind) chime(ok = true)
	}

	@Subscribe
	fun lockKeyDown(event: HandledScreenKeyPressedEvent) {
		val screen = Client.containerScreen ?: return
		val slot = hoveredSlot(screen) ?: return
		apply(gesture.pressed(slot, slot.inPlayerInventory, event.matches(TConfig.bindKey), event.matches(TConfig.lockSlotKey)))
	}

	@Subscribe
	fun lockKeyUp(event: HandledScreenKeyReleasedEvent) {
		val screen = Client.containerScreen ?: return

		val drawn = runCatching { (screen.customGui as? MenuFront)?.session?.view?.hoveredRealSlot() }.getOrNull()
		val slot = lockTarget(screen.castAccessor().focusedSlot_Apex, drawn)
		apply(gesture.released(slot, event.matches(TConfig.bindKey), event.matches(TConfig.lockSlotKey)))
	}

	@Subscribe
	fun itemLockKey(event: HandledScreenKeyPressedEvent) {
		if (!event.matches(TConfig.lockItemKey)) return
		val screen = Client.containerScreen ?: return
		val stack = hoveredSlot(screen)?.item?.view()?.takeUnless { it.isEmpty() } ?: return
		if (stack.huntingBox()) {

			Client.showChat(
				tr("apex.slot-locking.hunting-box-unbindable-hint",
					"The hunting box cannot be UUID bound reliably. It changes its own UUID frequently when switching tools. ").red()
					.append(tr("apex.slot-locking.hunting-box-unbindable-hint.solution",
						"Use the SkyBlock Apex config option for locking all hunting boxes instead.").lime())
			)
			chime(ok = false)
			return
		}
		val id = stack.itemUuid ?: return
		val ids = globalData.lockedUUIDs
		if (!ids.remove(id)) ids.add(id)
		saveNow()
		chime(ok = true)
		event.cancel()
	}

	fun isLockKey(ev: HandledScreenKeyPressedEvent): Boolean =
		ev.matches(TConfig.lockSlotKey) || ev.matches(TConfig.lockItemKey) || ev.matches(TConfig.bindKey)

	fun handleLockKeyFromApexMenu(screen: AbstractContainerScreen<*>, key: net.minecraft.client.input.KeyEvent, drawnSlot: Slot? = null): Boolean {
		val ev = HandledScreenKeyPressedEvent(screen, GenericInputAction.of(key), InputModifiers.of(key))
		if (!isLockKey(ev)) return false
		val target = lockTarget(screen.castAccessor().focusedSlot_Apex, drawnSlot) ?: return false
		slotOverride = target
		try { lockKeyDown(ev); itemLockKey(ev) } finally { slotOverride = null }
		return true
	}

	fun <S> lockTarget(focused: S?, drawn: S?): S? = focused ?: drawn

	@Subscribe
	fun drawBindings(event: HandledScreenForegroundEvent) {
		if (event.screen.customGui != null) return
		val hovered = event.screen.castAccessor().focusedSlot_Apex
		val boxes = SlotLinkOverlay.realSlotBoxes(event.screen)
		val frame = SlotLinkOverlay.planFrame(boxes, SlotLinkOverlay.liveState(), SlotLinkOverlay.inventoryIndex(hovered),
			TConfig.bindLines, SlotLinkOverlay.inventoryIndex(pendingBindSlot))
		SlotLinkOverlay.drawLinks(event.context, frame, event.mouseX, event.mouseY)
	}

	@Subscribe
	fun drawSlotPadlock(event: SlotRenderEvents.After) = drawLockIcon(event.context, event.slot, event.slot.x, event.slot.y)

	fun drawLockIcon(context: GuiGraphicsExtractor, slot: Slot, x: Int, y: Int) {
		val locks = globalData
		val kind = when {
			slot.inPlayerInventory && slot.containerSlot in locks.lockedSlots -> SlotLinkOverlay.LockKind.SLOT
			slot.item.view().itemUuid in locks.lockedUUIDs -> SlotLinkOverlay.LockKind.UUID
			else -> return
		}
		drawLockSprite(context, kind, x, y)
	}

	fun drawLockSprite(context: GuiGraphicsExtractor, kind: SlotLinkOverlay.LockKind, x: Int, y: Int, size: Int = 16) {
		val r = LockIcons.place(x, y, size)
		context.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.parse(LockIcons.sprite(kind, MenuStyle.state)), r.x, r.y, r.size, r.size, -1)
	}
}
