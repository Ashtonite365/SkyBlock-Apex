package dev.ashtonite365.apex.features.inventory.storageoverlay

object StorageNavButtons {
	enum class Nav(val label: String, val tooltip: String, val colour: Int, val names: List<String>) {
		BACK("↩", "Back (Go Back)", WHITE, listOf("Go Back", "Back")),
		FIRST("«", "First page", GOLD, listOf("First Page")),
		PREVIOUS("‹", "Previous page", GREEN, listOf("Previous Page")),
		NEXT("›", "Next page", GREEN, listOf("Next Page")),
		LAST("»", "Last page", GOLD, listOf("Last Page")),
	}

	const val SIZE = 18
	const val GAP = 2
	const val GOLD = 0xFFFFAA00.toInt()
	const val GREEN = 0xFF55FF55.toInt()
	const val WHITE = 0xFFFFFFFF.toInt()

	fun rect(overviewX: Int, overviewY: Int, nav: Nav): IntArray =
		intArrayOf(overviewX - SIZE - 4, overviewY + 4 + nav.ordinal * (SIZE + GAP), SIZE, SIZE)

	fun hit(overviewX: Int, overviewY: Int, mx: Double, my: Double): Nav? = Nav.entries.firstOrNull {
		val r = rect(overviewX, overviewY, it)
		StorageClickLogic.inside(r[0], r[1], r[2], r[3], mx, my)
	}

	fun resolve(names: List<String?>): Map<Nav, Int> {
		val out = LinkedHashMap<Nav, Int>()
		for (nav in Nav.entries) {
			val idx = names.indexOfFirst { n -> n != null && nav.names.any { normalise(it) == normalise(n) } }
			if (idx >= 0) out[nav] = idx
		}
		return out
	}

	fun normalise(name: String): String =
		name.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim().replace(dev.ashtonite365.apex.util.SharedRegex.WHITESPACE, " ").lowercase()

	fun backpackTarget(nav: Nav, current: StoragePageSlot?, unlocked: Collection<StoragePageSlot>): StoragePageSlot? {
		if (nav == Nav.BACK) return null
		val packs = (unlocked.filter { it.isBackPack } + listOfNotNull(current?.takeIf { it.isBackPack })).distinct().sorted()
		if (packs.isEmpty()) return null
		val onPack = current != null && current.isBackPack
		val target = when (nav) {
			Nav.FIRST -> packs.first()
			Nav.LAST -> packs.last()
			Nav.NEXT -> if (!onPack) packs.first() else packs.firstOrNull { it > current!! }
			Nav.PREVIOUS -> if (!onPack) null else packs.lastOrNull { it < current!! }
			Nav.BACK -> null
		}
		return target?.takeIf { it != current }
	}

	fun target(overviewX: Int, overviewY: Int, mx: Double, my: Double, isLeft: Boolean, names: List<String?>): Int? {
		if (!isLeft) return null
		val nav = hit(overviewX, overviewY, mx, my) ?: return null
		return resolve(names)[nav]
	}
}
