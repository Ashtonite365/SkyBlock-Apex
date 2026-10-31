package dev.ashtonite365.apex.gui.entity

import com.google.gson.JsonObject
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity

object ModifyName : EntityModifier {
	override fun apply(entity: LivingEntity, info: JsonObject): LivingEntity {
		val text = info["name"].asString
		entity.customName = Component.literal(text)
		return entity
	}
}
