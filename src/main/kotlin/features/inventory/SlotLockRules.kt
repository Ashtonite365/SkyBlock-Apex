package dev.ashtonite365.apex.features.inventory

import net.minecraft.world.inventory.ContainerInput

object SlotLockRules {

	private val harmlessClicks = setOf(
		ContainerInput.PICKUP, ContainerInput.QUICK_MOVE, ContainerInput.SWAP,
		ContainerInput.CLONE, ContainerInput.QUICK_CRAFT, ContainerInput.PICKUP_ALL,
	)

	fun isHarmless(click: ContainerInput) = click in harmlessClicks

	data class ItemFacts(val huntingBox: Boolean, val uuidLocked: Boolean)

	inline fun itemGuarded(
		click: ContainerInput, inSellingMenu: Boolean, fromPlayerInventory: Boolean, guardHuntingBoxes: Boolean, itemFacts: () -> ItemFacts?,
	): Boolean {
		val everyClickCounts = inSellingMenu && fromPlayerInventory
		if (!everyClickCounts && isHarmless(click)) return false
		val item = itemFacts() ?: return false
		return (guardHuntingBoxes && item.huntingBox) || item.uuidLocked
	}

	fun slotGuarded(fromPlayerInventory: Boolean, slotLocked: Boolean, bindingsLock: Boolean, hasBinding: Boolean): Boolean =
		fromPlayerInventory && when {
			slotLocked -> true
			else -> bindingsLock && hasBinding
		}

	enum class BoundClick {

		IGNORE,

		CANCEL,

		SWAP_WITH_PARTNER,
	}

	fun boundClick(
		click: ContainerInput,
		shiftRequired: Boolean,
		inventoryScreenOnly: Boolean,
		inInventoryScreen: Boolean,
		screenOpen: Boolean,
		fromPlayerInventory: Boolean,
		bindingCount: Int,
	): BoundClick {
		val triggers = when (click) {
			ContainerInput.QUICK_MOVE -> true
			ContainerInput.PICKUP -> !shiftRequired
			else -> false
		}
		val applies = triggers && screenOpen && fromPlayerInventory && (inInventoryScreen || !inventoryScreenOnly)
		return when {
			!applies || bindingCount <= 0 -> BoundClick.IGNORE
			bindingCount == 1 -> BoundClick.SWAP_WITH_PARTNER
			else -> BoundClick.CANCEL
		}
	}

	fun keepsProtectionInDungeon(protectedNow: Boolean, hotbarDrop: Boolean, inActiveDungeon: Boolean, dropsAllowedInDungeons: Boolean): Boolean {
		val waived = hotbarDrop && inActiveDungeon && dropsAllowedInDungeons
		return protectedNow && !waived
	}

	data class MenuFacts(val chest: Boolean, val containerSize: Int, val title: String, val keyItemName: String?, val keyItemLastLore: String?)

	fun salvageMenu(title: String): Boolean = title.contains("Salvage Item")

	private fun keyItem(menu: MenuFacts): String? = menu.keyItemName.takeIf { menu.chest && menu.containerSize >= 9 }

	fun tradeMenu(menu: MenuFacts): Boolean = keyItem(menu) == "⇦ Your stuff"

	fun npcShop(menu: MenuFacts): Boolean {
		val name = keyItem(menu) ?: return false
		return name == "Sell Item" || menu.keyItemLastLore == "Click to buyback!"
	}
}

sealed interface LockKeyEffect {
	data class Toggle(val slot: Int) : LockKeyEffect
	data class Bind(val hotbar: Int, val inventory: Int) : LockKeyEffect
	data class Unbind(val slot: Int) : LockKeyEffect

	fun applyTo(locks: GlobalSlotLocks, exclusiveBinds: Boolean): Boolean = when (this) {
		is Toggle -> {

			if (!locks.boundSlots.unbind(slot) && !locks.lockedSlots.remove(slot)) locks.lockedSlots.add(slot)
			true
		}
		is Bind -> {
			locks.lockedSlots.removeAll(listOf(hotbar, inventory))
			locks.boundSlots.releaseInventorySlot(inventory)
			locks.boundSlots.bind(hotbar, inventory, exclusiveBinds)
			true
		}
		is Unbind -> locks.boundSlots.unbind(slot)
	}
}

class LockKeyGesture<S : Any>(private val indexOf: (S) -> Int) {
	var remembered: S? = null
		private set

	fun forget() {
		remembered = null
	}

	private fun S.onHotbar() = indexOf(this) < 9

	fun pressed(slot: S?, playerSlot: Boolean, bindKey: Boolean, lockKey: Boolean): LockKeyEffect? {
		if (slot == null || !playerSlot) return null
		if (bindKey) {
			if (remembered == null) remembered = slot
			return null
		}
		return if (lockKey) LockKeyEffect.Toggle(indexOf(slot)) else null
	}

	fun released(slot: S?, bindKey: Boolean, lockKey: Boolean): LockKeyEffect? {
		val start = remembered ?: return null
		if (bindKey && slot != null && slot != start && slot.onHotbar() != start.onHotbar()) {
			remembered = null
			val (hotbar, inventory) = if (slot.onHotbar()) slot to start else start to slot
			return LockKeyEffect.Bind(indexOf(hotbar), indexOf(inventory))
		}
		if (lockKey && slot == start) {
			remembered = null
			return LockKeyEffect.Toggle(indexOf(start))
		}
		if (!bindKey) return null
		remembered = null
		return slot?.let { LockKeyEffect.Unbind(indexOf(it)) }
	}
}
