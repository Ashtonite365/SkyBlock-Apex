package dev.ashtonite365.apex.gui.entity

import com.google.gson.JsonObject
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob

object ModifySize : EntityModifier {
	override fun apply(entity: LivingEntity, info: JsonObject): LivingEntity {
		val cube = entity as? AbstractCubeMob ?: return entity
		val wanted = info.get("size")?.takeIf { it.isJsonPrimitive }?.asInt ?: return entity
		cube.setSize(wanted, false)
		return entity
	}
}
