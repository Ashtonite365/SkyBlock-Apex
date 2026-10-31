package dev.ashtonite365.apex.features.menus.bank

import kotlinx.serialization.Serializable
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder

@Config
object BankProfileCache : ProfileSpecificDataHolder<BankProfileData>(BankProfileData.serializer(), "bank-cache", ::BankProfileData) {

	fun update(d: BankProfileData, m: BankModel): Boolean {
		if (m.page != BankPage.MAIN) return false
		var changed = false
		m.cap?.let { if (it != d.cap) { d.cap = it; changed = true } }
		m.tier?.let { if (it != d.tier) { d.tier = it; changed = true } }
		return changed
	}

	fun remember(m: BankModel) {
		val d = runCatching { data }.getOrNull() ?: return
		if (update(d, m)) runCatching { markDirty() }
	}
	fun cached(): BankProfileData? = runCatching { data }.getOrNull()
}

@Serializable
data class BankProfileData(var cap: Double? = null, var tier: String? = null)
