// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.garden

import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object PestHeads {
	enum class PestType(val displayName: String, val textureHash: String) {
		BEETLE("Beetle", "70a1e836bf1968b2eaa4837227a19204f17295d870ee9e754bd6b6d60ddbed3c"),
		CRICKET("Cricket", "a24c69f96ce556221e195c8ef2bfad71ebf7f95f5ae914a484a8d0ec21672674"),
		DRAGONFLY("Dragonfly", "254aff4c0b2dce3a672349cc0ee9e6f3a9deebe4b3556e84611eca250a7821bf"),
		EARTHWORM("Earthworm", "6403ba4027a333d8d2fd32ab59d1cfdbaa7d908d80d2381db2a69cbe65450ad8"),
		FIELD_MOUSE("Field Mouse", "f379e09252817314bd0b694f7d53b48af2c7fa8499109802a41bb294d2f93e3e"),
		FIREFLY("Firefly", "4ce79e90adf34718f313ec24d6c6135b69b3788c618498446ccc83ca640c0b14"),
		FLY("Fly", "9d90e777826a52461368e26d1b2e19bfa1ba582d602483e545f4124d0f731842"),
		LOCUST("Locust", "4b24a482a32db1ea78fb98060b0c2fa4a373cbd18a68edddeb7419455a59cda9"),
		LUNAR_MOTH("Lunar Moth", "bee4fcc5aac27bdbb0be210df08b05bca5aebc89bf2821f608a55fd2cf0434be"),
		MITE("Mite", "be6baf6431a9daa2ca604d5a3c26e9a761d5952f0817174a4fe0b764616e21ff"),
		MOSQUITO("Mosquito", "52a9fe05bc663efcd12e56a3ccc5ec035bf577b78708548b6f4ffcf1d30eccfe"),
		MOTH("Moth", "65485c4b34e5b5470be94de100e61f7816f81bc5a11dfdf0eccf890172da5d0a"),
		PRAYING_MANTIS("Praying Mantis", "1e04bb6367caa4e88f5fd0ee80f0745d137a6060223dbbc42a16471fdf64bb83"),
		RAT("Rat", "a8abb471db0ab78703011979dc8b40798a941f3a4dec3ec61cbeec2af8cffe8"),
		SLUG("Slug", "7a79d0fd677b54530961117ef84adc206e2cc5045c1344d61d776bf8ac2fe1ba"),
	}

	private val byHash: Map<String, PestType> = PestType.entries.associateBy { it.textureHash }
	private val urlHash = Regex("textures\\.minecraft\\.net/texture/([0-9a-fA-F]{16,})")
	private val bareHash = Regex("[0-9a-fA-F]{40,}")

	fun textureHash(value: String?): String? {
		if (value.isNullOrBlank()) return null
		val trimmed = value.trim()
		urlHash.find(trimmed)?.let { return it.groupValues[1].lowercase() }
		if (bareHash.matches(trimmed)) return trimmed.lowercase()
		val decoded = runCatching {
			var b = trimmed.filterNot { it.isWhitespace() }.trimEnd('=').replace('-', '+').replace('_', '/')
			b += "=".repeat((4 - b.length % 4) % 4)
			java.util.Base64.getDecoder().decode(b).decodeToString()
		}.getOrNull() ?: return null
		return urlHash.find(decoded)?.groupValues?.get(1)?.lowercase()
	}

	fun pestFromTexture(value: String?): PestType? = textureHash(value)?.let { byHash[it] }

	fun headTexture(stack: ItemStack?): String? {
		if (stack == null || stack.isEmpty || stack.item != Items.PLAYER_HEAD) return null
		val profile = stack.get(DataComponents.PROFILE)?.partialProfile() ?: return null
		return profile.properties()["textures"].firstOrNull()?.value()?.takeIf { it.isNotBlank() }
	}

	fun isCandidate(entity: Entity): Boolean = entity.isAlive && entity is LivingEntity && entity !is Player

	fun entityHeadTexture(entity: Entity): String? =
		(entity as? LivingEntity)?.let { headTexture(it.getItemBySlot(EquipmentSlot.HEAD)) }

	class LockOn<K>(val maxChecks: Int = DEFAULT_MAX_CHECKS) {
		private val locked = mutableMapOf<K, PestType>()
		private val misses = mutableMapOf<K, Int>()

		val pests: Map<K, PestType> get() = locked

		fun update(candidates: Map<K, String?>): Map<K, PestType> = update(candidates.keys) { candidates[it] }

		fun update(candidates: Collection<K>, texture: (K) -> String?): Map<K, PestType> {
			val present = candidates.toSet()
			locked.keys.retainAll(present)
			misses.keys.retainAll(present)
			for (key in present) {
				if (!needsCheck(key)) continue
				val tex = texture(key)
				val type = pestFromTexture(tex)
				when {
					type != null -> { locked[key] = type; misses.remove(key) }
					key in locked -> if (tex != null) { locked.remove(key); misses[key] = maxChecks }
					else -> misses[key] = (misses[key] ?: 0) + 1
				}
			}
			return locked
		}

		fun needsCheck(key: K): Boolean = key in locked || (misses[key] ?: 0) < maxChecks

		fun clear() { locked.clear(); misses.clear() }
	}

	const val DEFAULT_MAX_CHECKS = 20
}
