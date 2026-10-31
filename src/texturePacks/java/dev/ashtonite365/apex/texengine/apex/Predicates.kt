/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: item predicates from pack JSON (written from SPEC §3.1, §3.2, §3.6, §3.7).
 */
package dev.ashtonite365.apex.texengine.apex

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.serialization.Codec
import dev.ashtonite365.apex.util.customDataTag
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.petBlob
import java.util.Base64
import java.util.UUID
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

fun interface StackTest {
	fun test(stack: ItemStack, holder: LivingEntity?): Boolean

	fun test(stack: ItemStack) = test(stack, null)

	companion object {
		val ALWAYS = StackTest { _, _ -> true }
		val NEVER = StackTest { _, _ -> false }

		fun allOf(parts: List<StackTest>): StackTest = when (parts.size) {
			0 -> ALWAYS
			1 -> parts[0]
			else -> {
				val array = parts.toTypedArray()
				StackTest { stack, holder -> array.all { it.test(stack, holder) } }
			}
		}
	}
}

object Predicates {

	val recognisedKeyPrefixes = listOf("apex:", "firmament:")

	private fun interface Reader {
		fun read(value: JsonElement, source: String?): StackTest?
	}

	private val readers: Map<String, Reader> = mapOf(
		"display_name" to Reader { v, _ ->
			val m = TextMatch.read(v)
			StackTest { s, _ -> m.test(s.view().displayNameAccordingToNbt) }
		},
		"lore" to Reader { v, _ ->
			val m = TextMatch.read(v)
			StackTest { s, _ -> s.view().loreAccordingToNbt.any(m::test) }
		},
		"item" to Reader { v, _ ->
			val id = Identifier.tryParse(v.asString) ?: return@Reader null
			val item = BuiltInRegistries.ITEM.getOptional(id).orElse(null) ?: return@Reader null
			StackTest { s, _ -> s.item == item }
		},
		"all" to Reader { v, src ->
			StackTest.allOf(v.asJsonArray.flatMap { partsOrNever(it, src) })
		},
		"any" to Reader { v, src ->
			val options = v.asJsonArray.map { StackTest.allOf(partsOrNever(it, src)) }
			StackTest { s, _ -> options.any { it.test(s, null) } }
		},
		"not" to Reader { v, src ->

			val parts = partsOrNever(v, src)
			StackTest { s, _ -> parts.none { it.test(s, null) } }
		},
		"extra_attributes" to Reader { v, _ ->
			val q = NbtQuery.read(v.asJsonObject)
			StackTest { s, _ -> q.matches(s.view().customDataTag) }
		},
		"component" to Reader { v, _ -> componentTest(v.asJsonObject) },
		"pet" to Reader { v, _ -> petTest(v) },
		"skull" to Reader { v, _ -> skullTest(v.asJsonObject) },
	)

	fun read(json: JsonElement?, source: String? = null): StackTest =
		if (json == null || json.isJsonNull) StackTest.NEVER else StackTest.allOf(partsOrNever(json, source))

	fun partsOrNever(json: JsonElement, source: String?): List<StackTest> =
		parts(json, source) ?: listOf(StackTest.NEVER)

	private fun parts(json: JsonElement, source: String?): List<StackTest>? {
		if (!json.isJsonObject) return null
		val result = mutableListOf<StackTest>()
		for ((key, value) in json.asJsonObject.entrySet()) {
			when (key) {
				"cast" -> result += castTest(value.asDouble)
				"pull" -> result += pullTest(value.asFloat)
				"pulling" -> result += pullTest(0.1F)
				else -> {
					val prefix = recognisedKeyPrefixes.firstOrNull { key.startsWith(it) } ?: continue
					val reader = readers[key.substring(prefix.length)] ?: return null
					val test = try {
						reader.read(value, source)
					} catch (e: PackFormatException) {
						null
					} catch (e: IllegalStateException) {
						null
					} catch (e: UnsupportedOperationException) {
						null
					} catch (e: ClassCastException) {
						null
					}
					result += test ?: return null
				}
			}
		}
		return result
	}

	private fun castTest(value: Double): StackTest {
		val wantsCasting = value >= 1
		return StackTest { stack, holder ->
			if (holder == null) return@StackTest false
			val casting = holder is Player && holder.fishing != null && holder.mainHandItem === stack
			casting == wantsCasting
		}
	}

	private fun pullTest(threshold: Float) = StackTest { _, holder ->
		holder != null && BowItem.getPowerForTime(holder.ticksUsingItem) >= threshold
	}

	private fun componentTest(obj: JsonObject): StackTest {
		val id = Identifier.parse(obj.get("component")?.asString ?: badPack("component predicate needs 'component'"))
		@Suppress("UNCHECKED_CAST")
		val type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(id).orElse(null) as DataComponentType<Any>?
			?: throw IllegalArgumentException("Unknown data component type $id")
		val query = NbtQuery.read(obj, setOf("component"))
		return StackTest { stack, _ ->
			val nbt = componentNbt(stack, type) ?: return@StackTest false
			query.matches(nbt)
		}
	}

	private fun componentNbt(stack: ItemStack, type: DataComponentType<Any>): Tag? {
		val value = stack.get(type) ?: return null
		if (type == DataComponents.CUSTOM_DATA) return stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
		val codec: Codec<Any> = type.codec() ?: return null
		return codec.encodeStart(NbtOps.INSTANCE, value).result().orElse(null)
	}

	private fun petTest(json: JsonElement): StackTest {
		if (json.isJsonPrimitive) {
			val type = json.asString
			return StackTest { s, _ -> s.view().petBlob?.species == type }
		}
		val obj = json.asJsonObject
		val id = obj.get("id")?.let(TextMatch::read)
		val exp = obj.get("exp")?.let { NumberTest.readOrNull(it) ?: badPack("bad pet exp") }
		val level = obj.get("level")?.let { NumberTest.readOrNull(it) ?: badPack("bad pet level") }
		val candy = obj.get("candyUsed")?.let { NumberTest.readOrNull(it) ?: badPack("bad pet candyUsed") }
		val tier = obj.get("tier")?.let { RarityTest.readOrNull(it) ?: badPack("bad pet tier") }
		return StackTest { s, _ ->
			val pet = s.view().petBlob ?: return@StackTest false
			(id == null || id.test(pet.species)) &&
				(exp == null || exp.test(pet.xp)) &&
				(candy == null || candy.test(pet.candies.toDouble())) &&
				(tier == null || tier.test(pet.rarity)) &&
				(level == null || level.test(pet.level.currentLevel.toDouble()))
		}
	}

	private fun readUuid(text: String): UUID {
		val hex = text.replace("-", "")
		if (hex.length != 32) badPack("bad UUID '$text'")
		return UUID(java.lang.Long.parseUnsignedLong(hex.substring(0, 16), 16), java.lang.Long.parseUnsignedLong(hex.substring(16), 16))
	}

	private fun skullTest(obj: JsonObject): StackTest {
		val profileId = obj.get("profileId")?.asString?.let(::readUuid)
		val textureProfileId = obj.get("textureProfileId")?.asString?.let(::readUuid)
		val textureValue = obj.get("textureValue")?.let(TextMatch::read)
		val skinUrl = obj.get("skinUrl")?.let(TextMatch::read)
		val needsDecode = textureProfileId != null || skinUrl != null
		return StackTest { stack, _ ->
			if (stack.item != Items.PLAYER_HEAD) return@StackTest false
			val profile = stack.get(DataComponents.PROFILE)?.partialProfile() ?: return@StackTest false
			if (profileId != null && profile.id() != profileId) return@StackTest false
			val value = profile.properties()["textures"].firstOrNull()?.value() ?: ""
			if (textureValue != null && !textureValue.test(value)) return@StackTest false
			if (!needsDecode) return@StackTest true
			val decoded = runCatching {
				JsonParser.parseString(String(Base64.getDecoder().decode(value))).asJsonObject
			}.getOrNull()
			if (textureProfileId != null) {
				val raw = decoded?.get("profileId")?.asString
				if (raw == null || runCatching { readUuid(raw) }.getOrNull() != textureProfileId) return@StackTest false
			}
			if (skinUrl != null) {
				val url = runCatching { decoded?.getAsJsonObject("textures")?.getAsJsonObject("SKIN")?.get("url")?.asString }
					.getOrNull() ?: ""
				if (!skinUrl.test(url)) return@StackTest false
			}
			true
		}
	}
}
