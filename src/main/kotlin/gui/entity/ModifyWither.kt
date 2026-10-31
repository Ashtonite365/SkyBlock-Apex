package dev.ashtonite365.apex.gui.entity

import com.google.gson.JsonObject
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.wither.WitherBoss

object ModifyWither : EntityModifier {
	private const val SPAWN_TICKS = 800

	override fun apply(entity: LivingEntity, info: JsonObject): LivingEntity {
		val wither = requireNotNull(entity as? WitherBoss) { "Failed requirement." }
		info["tiny"]?.asBoolean?.let { tiny -> wither.invulnerableTicks = if (tiny) SPAWN_TICKS else 0 }
		info["armored"]?.asBoolean?.let { armored -> wither.health = if (armored) 1F else wither.maxHealth }
		return wither
	}
}
