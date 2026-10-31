package dev.ashtonite365.apex.gui.entity

import com.google.gson.JsonObject
import net.minecraft.world.entity.LivingEntity

object ModifyRiding : EntityModifier {
	override fun apply(entity: LivingEntity, info: JsonObject): LivingEntity {
		val rider = requireNotNull(EntityRenderer.constructEntity(info)) { "Failed requirement." }
		rider.startRiding(entity, true, false)
		return entity
	}
}
