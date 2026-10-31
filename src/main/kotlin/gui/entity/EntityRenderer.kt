// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.entity

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.shedaniel.math.Dimension
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.iterate
import dev.ashtonite365.apex.util.openApexResource

object EntityRenderer {
	private var lastLevel: ClientLevel? = null

	private fun clientLevel(): ClientLevel {
		val now = Client.level
		if (now != null) lastLevel = now
		return (now ?: lastLevel)!!
	}

	val fakeWorld: Level get() = clientLevel()

	private fun spawnOf(type: () -> EntityType<out LivingEntity>): () -> LivingEntity = {
		val level = fakeWorld
		type().create(level, EntitySpawnReason.LOAD)!!
	}

	val entityIds: Map<String, () -> LivingEntity> = linkedMapOf(
		"Allay" to spawnOf { EntityTypes.ALLAY },
		"Camel" to spawnOf { EntityTypes.CAMEL },
		"Cat" to spawnOf { EntityTypes.CAT },
		"Hoglin" to spawnOf { EntityTypes.HOGLIN },
		"Nautilus" to spawnOf { EntityTypes.NAUTILUS },
		"Parched" to spawnOf { EntityTypes.PARCHED },
		"Parrot" to spawnOf { EntityTypes.PARROT },
		"SkeletonHorse" to spawnOf { EntityTypes.SKELETON_HORSE },
		"ZombieHorse" to spawnOf { EntityTypes.ZOMBIE_HORSE },
		"ZombieNautilus" to spawnOf { EntityTypes.ZOMBIE_NAUTILUS },
		"Armadillo" to spawnOf { EntityTypes.ARMADILLO },
		"ArmorStand" to spawnOf { EntityTypes.ARMOR_STAND },
		"Axolotl" to spawnOf { EntityTypes.AXOLOTL },
		"Bat" to spawnOf { EntityTypes.BAT },
		"Bee" to spawnOf { EntityTypes.BEE },
		"Blaze" to spawnOf { EntityTypes.BLAZE },
		"Bogged" to spawnOf { EntityTypes.BOGGED },
		"Breeze" to spawnOf { EntityTypes.BREEZE },
		"CaveSpider" to spawnOf { EntityTypes.CAVE_SPIDER },
		"Chicken" to spawnOf { EntityTypes.CHICKEN },
		"Cod" to spawnOf { EntityTypes.COD },
		"Cow" to spawnOf { EntityTypes.COW },
		"Creaking" to spawnOf { EntityTypes.CREAKING },
		"Creeper" to spawnOf { EntityTypes.CREEPER },
		"Dolphin" to spawnOf { EntityTypes.DOLPHIN },
		"Donkey" to spawnOf { EntityTypes.DONKEY },
		"Dragon" to spawnOf { EntityTypes.ENDER_DRAGON },
		"Drowned" to spawnOf { EntityTypes.DROWNED },
		"Eisengolem" to spawnOf { EntityTypes.IRON_GOLEM },
		"Enderman" to spawnOf { EntityTypes.ENDERMAN },
		"Endermite" to spawnOf { EntityTypes.ENDERMITE },
		"Evoker" to spawnOf { EntityTypes.EVOKER },
		"Fox" to spawnOf { EntityTypes.FOX },
		"Frog" to spawnOf { EntityTypes.FROG },
		"Ghast" to spawnOf { EntityTypes.GHAST },
		"Giant" to spawnOf { EntityTypes.GIANT },
		"GlowSquid" to spawnOf { EntityTypes.GLOW_SQUID },
		"Goat" to spawnOf { EntityTypes.GOAT },
		"Guardian" to spawnOf { EntityTypes.GUARDIAN },
		"Horse" to spawnOf { EntityTypes.HORSE },
		"Husk" to spawnOf { EntityTypes.HUSK },
		"Illusioner" to spawnOf { EntityTypes.ILLUSIONER },
		"LLama" to spawnOf { EntityTypes.LLAMA },
		"MagmaCube" to spawnOf { EntityTypes.MAGMA_CUBE },
		"Mooshroom" to spawnOf { EntityTypes.MOOSHROOM },
		"Mule" to spawnOf { EntityTypes.MULE },
		"Ocelot" to spawnOf { EntityTypes.OCELOT },
		"Panda" to spawnOf { EntityTypes.PANDA },
		"Phantom" to spawnOf { EntityTypes.PHANTOM },
		"Pig" to spawnOf { EntityTypes.PIG },
		"Piglin" to spawnOf { EntityTypes.PIGLIN },
		"PiglinBrute" to spawnOf { EntityTypes.PIGLIN_BRUTE },
		"Pigman" to spawnOf { EntityTypes.ZOMBIFIED_PIGLIN },
		"Pillager" to spawnOf { EntityTypes.PILLAGER },
		"Player" to { GuiPlayer(clientLevel()) },
		"PolarBear" to spawnOf { EntityTypes.POLAR_BEAR },
		"Pufferfish" to spawnOf { EntityTypes.PUFFERFISH },
		"Rabbit" to spawnOf { EntityTypes.RABBIT },
		"Salmom" to spawnOf { EntityTypes.SALMON },
		"Salmon" to spawnOf { EntityTypes.SALMON },
		"Sheep" to spawnOf { EntityTypes.SHEEP },
		"Shulker" to spawnOf { EntityTypes.SHULKER },
		"Silverfish" to spawnOf { EntityTypes.SILVERFISH },
		"SiNelverfish" to spawnOf { EntityTypes.SILVERFISH },
		"Skeleton" to spawnOf { EntityTypes.SKELETON },
		"Slime" to spawnOf { EntityTypes.SLIME },
		"Sniffer" to spawnOf { EntityTypes.SNIFFER },
		"Snowman" to spawnOf { EntityTypes.SNOW_GOLEM },
		"Spider" to spawnOf { EntityTypes.SPIDER },
		"Squid" to spawnOf { EntityTypes.SQUID },
		"Stray" to spawnOf { EntityTypes.STRAY },
		"Strider" to spawnOf { EntityTypes.STRIDER },
		"Tadpole" to spawnOf { EntityTypes.TADPOLE },
		"TropicalFish" to spawnOf { EntityTypes.TROPICAL_FISH },
		"Turtle" to spawnOf { EntityTypes.TURTLE },
		"Vex" to spawnOf { EntityTypes.VEX },
		"Villager" to spawnOf { EntityTypes.VILLAGER },
		"Vindicator" to spawnOf { EntityTypes.VINDICATOR },
		"Warden" to spawnOf { EntityTypes.WARDEN },
		"Witch" to spawnOf { EntityTypes.WITCH },
		"Wither" to spawnOf { EntityTypes.WITHER },
		"WitherSkeleton" to spawnOf { EntityTypes.WITHER_SKELETON },
		"Wolf" to spawnOf { EntityTypes.WOLF },
		"Zoglin" to spawnOf { EntityTypes.ZOGLIN },
		"Zombie" to spawnOf { EntityTypes.ZOMBIE },
		"ZombieVillager" to spawnOf { EntityTypes.ZOMBIE_VILLAGER },
	)

	val entityModifiers: Map<String, EntityModifier> = linkedMapOf(
		"playerdata" to ModifyPlayerSkin,
		"equipment" to ModifyEquipment,
		"riding" to ModifyRiding,
		"charged" to ModifyCharged,
		"witherdata" to ModifyWither,
		"invisible" to ModifyInvisible,
		"age" to ModifyAge,
		"horse" to ModifyHorse,
		"name" to ModifyName,
		"size" to ModifySize,
	)

	fun applyModifiers(typeId: String, steps: List<JsonObject>): LivingEntity? {
		val factory = entityIds[typeId]
		if (factory == null) {
			Faults.reportFault("Could not create entity with id $typeId")
			return null
		}
		var entity: LivingEntity = Faults.attempt("Could not create entity with id $typeId") { PreviewEntityIds.ensure(factory()) }
			.orElse { return null }
		for (step in steps) {
			val modifier = step["type"]?.takeIf { it.isJsonPrimitive }?.asString?.let(entityModifiers::get)
			if (modifier == null) {
				Faults.reportFault("Could not create entity with id $typeId. Failed to apply modifier $step")
				return null
			}
			val current = entity
			entity = Faults.attempt("Could not create entity with id $typeId. Failed to apply modifier $step") { modifier.apply(current, step) }
				.orElse { return null }
		}
		return entity
	}

	fun constructEntity(info: JsonObject): LivingEntity? {
		val modifiers = info["modifiers"]?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
		val steps = modifiers.filter { it.isJsonObject }.map { it.asJsonObject }
		val id = info["entity"]?.takeIf { it.isJsonPrimitive }?.asString
		if (id == null) {
			Faults.reportFault("Missing entity type on entity object")
			return null
		}
		return applyModifiers(id, steps)
	}

	fun constructEntity(location: Identifier): LivingEntity? =
		constructEntity(location.openApexResource().reader().use { Gson().fromJson(it, JsonObject::class.java) })

	private fun stackOf(entity: LivingEntity): List<LivingEntity> = entity.iterate { it.firstPassenger as? LivingEntity }.toList()

	private fun shapeOf(e: LivingEntity) = PreviewShape(
		BuiltInRegistries.ENTITY_TYPE.getKey(e.type).toString(), e.isInvisible, e.bbWidth, e.bbHeight, e.scale,
		EquipmentSlot.entries.any { !e.getItemBySlot(it).isEmpty },
	)

	fun drawsNothingReason(entity: LivingEntity): String? = PreviewVisibility.reasonForChain(stackOf(entity).map(::shapeOf))

	fun renderEntity(
		entity: LivingEntity, graphics: GuiGraphicsExtractor, left: Int, top: Int,
		boxWidth: Double, boxHeight: Double, mouseX: Double, mouseY: Double, entityScale: Double = (boxHeight - 10.0) / 2.0,
	) {
		val stack = stackOf(entity).onEach { PreviewEntityIds.ensure(it) }
		val totalHeight = stack.sumOf { it.bbHeight.toDouble() }.coerceAtLeast(0.01)
		val size = if (totalHeight > 2.0) entityScale * 2.0 / totalHeight else entityScale
		val right = left + boxWidth.toInt()
		val bottom = top + boxHeight.toInt()

		val box = PreviewScreenBox.toScreen(graphics.pose(), left, top, right, bottom, size, mouseX, mouseY)
		graphics.pose().pushMatrix()
		try {
			graphics.pose().identity()
			var below = 0.0
			for (layer in stack) {
				val height = layer.bbHeight.toDouble()

				val yOffset = totalHeight / 2.0 - below - height / 2.0
				InventoryScreen.extractEntityInInventoryFollowsMouse(
					graphics, box.x0, box.y0, box.x1, box.y1, box.size.toInt().coerceAtLeast(1),
					yOffset.toFloat(), box.mouseX.toFloat(), box.mouseY.toFloat(), layer,
				)
				below += height
			}
		} finally {
			graphics.pose().popMatrix()
		}
	}

	val defaultSize: Dimension = Dimension(50, 80)
}
