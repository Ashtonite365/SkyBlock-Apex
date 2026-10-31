// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.museum

import kotlinx.serialization.Serializable
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder

@Serializable
data class MuseumAppraisalData(val value: String? = null, val rank: String? = null, val lore: List<String> = emptyList(),

	val topItems: List<Pair<String, String>> = emptyList())

object MuseumAppraisal {
	private val I = setOf(RegexOption.IGNORE_CASE)
	private val COINS_LINE = Regex("^([\\d,]+)\\s+coins$", I)
	private val RANK = Regex("#\\s*([\\d,]+)")

	fun fromLore(s: SlotText): MuseumAppraisalData? {
		if (!MuseumParser.APPRAISAL.containsMatchIn(s.cleanName)) return null
		val l = s.cleanLore.map { it.trim() }
		val i = l.indexOfFirst { it.equals("Your Museum Value", true) }
		if (i < 0) return null
		val value = l.getOrNull(i + 1)?.let { COINS_LINE.matchEntire(it) }?.groupValues?.get(1) ?: return null
		val rank = l.firstNotNullOfOrNull { RANK.find(it)?.groupValues?.get(1) }
		return MuseumAppraisalData(value, rank, s.cleanLore, MuseumParser.topItems(l))
	}

	fun fromSlots(slots: List<SlotText>): MuseumAppraisalData? = slots.firstNotNullOfOrNull { fromLore(it) }

	const val LABEL = "Appraisal Service"
	const val EMPTY = "not appraised yet"
	fun label(d: MuseumAppraisalData?) = if (d?.value != null) LABEL else EMPTY

	fun tooltip(d: MuseumAppraisalData?): List<String> {
		val v = d?.value ?: return emptyList()
		return listOf("§6Museum Appraisal Service", "§eAppraised value: §6$v coins") +
			listOfNotNull(d.rank?.let { "§eRank: §f#$it" }) +
			(if (d.topItems.isEmpty()) emptyList() else listOf("§eTop Items") + d.topItems.mapIndexed { i, (n, c) -> "§7${i + 1}. §f$n §8- §6$c" }) +
			listOf("") + d.lore.map { "§7$it" }
	}

	fun update(store: MutableMap<String, MuseumAppraisalData>, profile: String, d: MuseumAppraisalData?): Boolean {
		if (d == null || store[profile] == d) return false
		store[profile] = d; return true
	}
}

@Serializable
data class MuseumAppraisalProfile(var data: MuseumAppraisalData? = null)

@Config
object MuseumAppraisalCache : ProfileSpecificDataHolder<MuseumAppraisalProfile>(MuseumAppraisalProfile.serializer(), "museum-appraisal", ::MuseumAppraisalProfile) {
	fun current(): MuseumAppraisalData? = runCatching { data.data }.getOrNull()
	fun remember(d: MuseumAppraisalData?) {
		d ?: return
		val p = runCatching { data }.getOrNull() ?: return
		if (p.data != d) { p.data = d; runCatching { markDirty() } }
	}
}

object MuseumAppraisalBox {
	const val H = 11

	const val CONTENT_Y = 32
	fun rect(vanillaX: Int, y: Int, vanillaW: Int, labelW: Int): MuseumBottomRow.Rect {
		val bw = maxOf(vanillaW, labelW + 6)
		return MuseumBottomRow.Rect(vanillaX + vanillaW - bw, y + 19, bw, H)
	}
}
