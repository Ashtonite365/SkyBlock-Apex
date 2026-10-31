package dev.ashtonite365.apex.gui.entity

import com.google.gson.JsonObject
import net.minecraft.world.entity.LivingEntity

object ModifyInvisible : EntityModifier {
	override fun apply(entity: LivingEntity, info: JsonObject): LivingEntity =
		entity.apply { isInvisible = info["invisible"]?.asBoolean ?: true }
}
