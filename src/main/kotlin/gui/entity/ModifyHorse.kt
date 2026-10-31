package dev.ashtonite365.apex.gui.entity

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.equine.AbstractHorse
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object ModifyHorse : EntityModifier {
	private val kinds: Map<String, EntityType<out AbstractHorse>> = mapOf(
		"skeleton" to EntityTypes.SKELETON_HORSE,
		"zombie" to EntityTypes.ZOMBIE_HORSE,
		"mule" to EntityTypes.MULE,
		"donkey" to EntityTypes.DONKEY,
		"horse" to EntityTypes.HORSE,
	)

	private val armours: Map<String, Item> = mapOf(
		"iron" to Items.IRON_HORSE_ARMOR,
		"golden" to Items.GOLDEN_HORSE_ARMOR,
		"diamond" to Items.DIAMOND_HORSE_ARMOR,
	)

	override fun apply(entity: LivingEntity, info: JsonObject): LivingEntity {
		var horse = requireNotNull(entity as? AbstractHorse) { "Failed requirement." }
		info["kind"]?.let { kind -> horse = freshOfKind(kind) }
		info["armor"]?.let { armour -> horse.setHorseArmor(armourStack(armour)) }
		info["saddled"]?.let { saddle -> horse.setIsSaddled(saddle.asBoolean) }
		return horse
	}

	private fun freshOfKind(kind: JsonElement): AbstractHorse {
		val type = kinds[kind.asString] ?: error("Unknown horse kind $kind")
		return type.create(EntityRenderer.fakeWorld, EntitySpawnReason.LOAD)!!
	}

	private fun armourStack(armour: JsonElement): ItemStack {
		if (armour is JsonNull) return ItemStack.EMPTY
		val item = armours[armour.asString] ?: error("Unknown horse armor $armour")
		return ItemStack(item)
	}
}

fun AbstractHorse.setIsSaddled(shouldBeSaddled: Boolean) =
	setItemSlot(EquipmentSlot.SADDLE, if (shouldBeSaddled) ItemStack(Items.SADDLE) else ItemStack.EMPTY)

fun AbstractHorse.setHorseArmor(itemStack: ItemStack) {
	setItemSlot(EquipmentSlot.BODY, itemStack)
}
