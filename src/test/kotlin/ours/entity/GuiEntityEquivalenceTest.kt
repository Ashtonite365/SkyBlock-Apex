// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.entity

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Test
import net.minecraft.world.entity.LivingEntity
import dev.ashtonite365.apex.gui.entity.EntityRenderer
import dev.ashtonite365.apex.gui.entity.ModifyEquipment
import dev.ashtonite365.apex.test.ours.OursGolden

class GuiEntityEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	private fun attempt(block: () -> Any?): String = try {
		val v = block()
		if (v is LivingEntity) "entity:" + v.javaClass.simpleName else v.toString()
	} catch (e: Throwable) {
		"ERR:" + e.javaClass.simpleName
	}

	private fun json(s: String): JsonObject = JsonParser.parseString(s).asJsonObject

	private val unsafe: sun.misc.Unsafe by lazy {
		sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").also { it.isAccessible = true }.get(null) as sun.misc.Unsafe
	}

	private fun bare(cls: String): LivingEntity = unsafe.allocateInstance(Class.forName(cls)) as LivingEntity

	@Test
	fun tablesAreTheSame() {
		val out = mutableListOf<String>()
		out += "ids ${EntityRenderer.entityIds.keys}"
		for ((k, f) in EntityRenderer.entityIds) out += "make $k ${attempt { f() }}"
		for ((k, m) in EntityRenderer.entityModifiers) out += "modifier $k ${m.javaClass.simpleName}"
		out += "size ${EntityRenderer.defaultSize}"
		out += "slots ${ModifyEquipment.names}"
		OursGolden.check("entity-tables", out)
	}

	@Test
	fun repoJsonIsReadTheSame() {
		val out = mutableListOf<String>()
		val infos = listOf(
			"{}", """{"entity":"Nope"}""", """{"entity":"Cow"}""", """{"entity":"Cow","modifiers":[{"type":"zzz"}]}""",
			"""{"entity":"Cow","modifiers":[]}""", """{"entity":3}""", """{"modifiers":[{"type":"age"}]}""",
		)
		for (i in infos) out += "construct $i ${attempt { EntityRenderer.constructEntity(json(i)) }}"
		out += "apply-unknown ${attempt { EntityRenderer.applyModifiers("Nope", emptyList()) }}"
		out += "apply-player ${attempt { EntityRenderer.applyModifiers("Player", listOf(json("""{"type":"name","name":"x"}"""))) }}"
		val equipment = listOf(
			"{}", """{"hand":"ASPECT_OF_THE_END"}""", """{"mainhand":"A","hand":"B"}""", """{"helmet":" ","head":"H"}""",
			"""{"boots":"B","feet":"F","legs":"L","leggings":"G","chest":"C","chestplate":"P","off_hand":"O","offhand":"Q"}""",
			"""{"hand":{"x":1},"helmet":["a"],"chest":5,"legs":true}""", """{"HAND":"x","type":"equipment"}""",
			"""{"helmet":"SKULL#abc","chestplate":"LEATHER_CHESTPLATE#ff0000","boots":"IRON#1"}""",
		)
		for (e in equipment) out += "slots $e ${attempt { ModifyEquipment.slotItems(json(e)) }}"
		OursGolden.check("entity-json", out)
	}

	@Test
	fun modifiersCheckTheirTargetsTheSame() {
		val out = mutableListOf<String>()
		val classes = listOf(
			"net.minecraft.world.entity.monster.Creeper",
			"net.minecraft.world.entity.monster.zombie.Zombie",
			"net.minecraft.world.entity.decoration.ArmorStand",
			"net.minecraft.world.entity.animal.cow.Cow",
			"net.minecraft.world.entity.boss.wither.WitherBoss",
			"net.minecraft.world.entity.animal.equine.Horse",
			"net.minecraft.world.entity.monster.cubemob.Slime",
			"dev.ashtonite365.apex.gui.entity.GuiPlayer",
		)
		val infos = mapOf(
			"age" to listOf("{}", """{"baby":true}""", """{"baby":false}"""),
			"charged" to listOf("{}"),
			"witherdata" to listOf("{}", """{"tiny":true}""", """{"armored":false}"""),
			"invisible" to listOf("{}", """{"invisible":false}"""),
			"horse" to listOf("{}", """{"kind":"mule"}""", """{"kind":"pony"}""", """{"armor":null}""", """{"armor":"gold"}""", """{"saddled":true}"""),
			"name" to listOf("{}", """{"name":"Bob"}"""),
			"size" to listOf("{}", """{"size":3}"""),
			"riding" to listOf("{}", """{"entity":"Nope"}"""),
			"playerdata" to listOf("{}", """{"slim":true}""", """{"parts":true}""", """{"parts":{"cape":false}}"""),
			"equipment" to listOf("{}", """{"hand":"SKULL#abc"}"""),
		)
		for (cls in classes) {
			val known = runCatching { Class.forName(cls) }.isSuccess
			out += "class ${cls.substringAfterLast('.')} loadable=$known"
			if (!known) continue
			for ((type, samples) in infos) for (s in samples) {
				val modifier = EntityRenderer.entityModifiers.getValue(type)
				out += "  $type $s ${attempt { val e = bare(cls); val r = modifier.apply(e, json(s)); if (r === e) "same" else r }}"
			}
			out += "  reason ${attempt { EntityRenderer.drawsNothingReason(bare(cls)) }}"
		}
		OursGolden.check("entity-modifiers", out)
	}
}
