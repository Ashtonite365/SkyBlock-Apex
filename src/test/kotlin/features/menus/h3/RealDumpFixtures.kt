package dev.ashtonite365.apex.test.features.menus.h3

import dev.ashtonite365.apex.features.menus.support.SlotText

object RealDumpFixtures {
	data class Dump(val title: String, val slots: List<SlotText>)
	private val SLOT = Regex("^slot=(\\d+) name=(.*?) lore=\\[(.*)]$")
	fun load(name: String): Dump {
		val lines = javaClass.getResource("/apex-fixtures/$name.txt")!!.readText().lines().filter { it.isNotBlank() }
		val title = lines.first().substringAfter("title=").replace("\\u279C", "\u279C")
		val slots = lines.drop(1).mapNotNull { SLOT.matchEntire(it) }.map { m ->
			SlotText(m.groupValues[1].toInt(), m.groupValues[2], if (m.groupValues[3].isEmpty()) emptyList() else m.groupValues[3].split(", "))
		}
		return Dump(title, slots)
	}
}
