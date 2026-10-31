package dev.ashtonite365.apex.gui.entity

import com.google.gson.JsonObject
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.DyedItemColor
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.placeholderHeadUuid
import dev.ashtonite365.apex.util.mc.applyEncodedSkin

object ModifyEquipment : EntityModifier {

	private val spellings: List<Pair<EquipmentSlot, List<String>>> = listOf(
		EquipmentSlot.MAINHAND to listOf("hand", "mainhand"),
		EquipmentSlot.OFFHAND to listOf("offhand", "off_hand"),
		EquipmentSlot.HEAD to listOf("helmet", "head"),
		EquipmentSlot.CHEST to listOf("chestplate", "chest"),
		EquipmentSlot.LEGS to listOf("leggings", "legs"),
		EquipmentSlot.FEET to listOf("feet", "boots"),
	)

	val names: Map<String, EquipmentSlot> = buildMap {
		for ((slot, keys) in spellings) keys.forEach { key -> put(key, slot) }
	}

	private val dyeable: Map<String, Item> = mapOf(
		"LEATHER_LEGGINGS" to Items.LEATHER_LEGGINGS,
		"LEATHER_BOOTS" to Items.LEATHER_BOOTS,
		"LEATHER_HELMET" to Items.LEATHER_HELMET,
		"LEATHER_CHESTPLATE" to Items.LEATHER_CHESTPLATE,
	)

	fun slotItems(info: JsonObject): Map<EquipmentSlot, String> = buildMap {
		for ((slot, keys) in spellings) {
			val text = keys.firstNotNullOfOrNull { key -> textAt(info, key) } ?: continue
			put(slot, text)
		}
	}

	private fun textAt(info: JsonObject, key: String): String? {
		val value = info[key]
		if (value == null || !value.isJsonPrimitive) return null
		return value.asString.trim().ifEmpty { null }
	}

	override fun apply(entity: LivingEntity, info: JsonObject): LivingEntity {

		for ((slot, text) in slotItems(info)) entity.setItemSlot(slot, stackFor(text).copy())
		return entity
	}

	@OptIn(ExpensiveItemCacheApi::class, NeedsComponents::class)
	private fun stackFor(text: String): ItemStack {
		val parts = text.split("#")
		if (parts.size != 2) return SBItemStack(ItemId(text)).asImmutableItemStack().current()
		val kind = parts[0]
		val payload = parts[1]
		if (kind == "SKULL") return ItemStack(Items.PLAYER_HEAD).apply { applyEncodedSkin(placeholderHeadUuid, payload) }
		val piece = dyeable[kind] ?: error("Unknown leather piece: $kind")
		return ItemStack(piece).apply { set(DataComponents.DYED_COLOR, DyedItemColor(payload.toInt(16))) }
	}
}
