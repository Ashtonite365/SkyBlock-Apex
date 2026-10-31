// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.minion

object MinionSlotMap {
	const val CHEST_SLOTS = 54

	const val IDEAL_LAYOUT = 3
	const val MINION_INFO = 4
	const val NEXT_TIER = 5
	const val SKIN = 10
	const val FUEL = 19
	const val SHIPPING = 28
	const val UPGRADE_1 = 37
	const val UPGRADE_2 = 46
	const val COLLECT_ALL = 48
	const val QUICK_UPGRADE = 50
	const val PICKUP = 53

	val STORAGE: List<Int> = listOf(21, 22, 23, 24, 25, 30, 31, 32, 33, 34, 39, 40, 41, 42, 43)

	enum class ItemSlot(val slot: Int, val label: String, val emptyName: String?, val filledLore: String?, val hint: String) {
		SKIN(MinionSlotMap.SKIN, "Skin", "Minion Skin Slot", null, "Drop a Minion Skin here"),

		FUEL(MinionSlotMap.FUEL, "Fuel", null, "Minion Fuel", "Drop a fuel item here"),
		SHIPPING(MinionSlotMap.SHIPPING, "Automated Shipping", "Automated Shipping", null, "Drop a Budget / Enchanted Hopper here"),
		UPGRADE_1(MinionSlotMap.UPGRADE_1, "Upgrade 1", "Upgrade Slot", null, "Drop a minion upgrade here"),
		UPGRADE_2(MinionSlotMap.UPGRADE_2, "Upgrade 2", "Upgrade Slot", null, "Drop a minion upgrade here"),
	}

	val REQUIRED = mapOf(COLLECT_ALL to "Collect All", PICKUP to "Pickup Minion")

	val KNOWN: Set<Int> = setOf(IDEAL_LAYOUT, MINION_INFO, NEXT_TIER, COLLECT_ALL, QUICK_UPGRADE, PICKUP) +
		ItemSlot.entries.map { it.slot } + STORAGE

	fun inventorySlot(row: Int, col: Int, chestSlots: Int = CHEST_SLOTS): Int {
		require(row in 0..3 && col in 0..8)
		return chestSlots + row * 9 + col
	}
}

object IdealLayoutSlotMap {
	const val TITLE = "Ideal Layout (Top-down view)"
	const val CHEST_SLOTS = 54

	val GRID: List<Int> = listOf(2, 3, 4, 5, 6, 11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33, 38, 39, 40, 41, 42)
	const val CENTRE = 22
	const val GO_BACK = 48
	const val CLOSE = 49
}
