package dev.ashtonite365.apex.gui.entity

import com.google.gson.JsonObject
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Creeper

object ModifyCharged : EntityModifier {
	override fun apply(entity: LivingEntity, info: JsonObject): LivingEntity {
		val creeper = entity as? Creeper
		requireNotNull(creeper) { "Failed requirement." }
		creeper.entityData.set(Creeper.DATA_IS_POWERED, true)
		return creeper
	}
}
