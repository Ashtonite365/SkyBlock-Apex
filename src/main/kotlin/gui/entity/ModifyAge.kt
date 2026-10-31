package dev.ashtonite365.apex.gui.entity

import com.google.gson.JsonObject
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.zombie.Zombie

object ModifyAge : EntityModifier {
	override fun apply(entity: LivingEntity, info: JsonObject): LivingEntity {
		val baby = info["baby"]?.asBoolean == true
		when (entity) {
			is AgeableMob -> entity.age = if (baby) -1 else 1
			is Zombie -> entity.isBaby = baby
			is ArmorStand -> entity.isSmall = baby
			else -> error("Cannot set age for $entity")
		}
		return entity
	}
}
