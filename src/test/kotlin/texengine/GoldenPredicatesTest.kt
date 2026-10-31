// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import java.util.Base64
import java.util.UUID
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@Tag("texengine-golden")
@ExtendWith(AutoBootstrapExtension::class)
class GoldenPredicatesTest {
	private val engine: GoldenEngine = ApexGolden

	private val plain = Stacks.sb()
	private val nameA = Stacks.sb(name = Component.literal("A"))
	private val loreB = Stacks.sb(lore = listOf(Component.literal("B")))
	private val both = Stacks.sb(name = Component.literal("A"), lore = listOf(Component.literal("B")))

	@Test
	fun `G-PRED-1 absent predicate never matches`() {
		assertFalse(engine.predicate(null)(plain))
		assertFalse(engine.predicate(null)(both))
	}

	@Test
	fun `G-PRED-2 empty object always matches`() {
		assertTrue(engine.predicate("{}")(plain))
	}

	@Test
	fun `G-PRED-3 unknown apex key or bad item means never`() {
		assertFalse(engine.predicate("""{"apex:does_not_exist":1}""")(plain))
		assertFalse(engine.predicate("""{"apex:item":"minecraft:not_an_item"}""")(plain))
	}

	@Test
	fun `G-PRED-4 foreign keys are ignored`() {
		assertTrue(engine.predicate("""{"foo:bar":1}""")(plain))
	}

	@Test
	fun `G-PRED-4b legacy key prefix`() {
		val p = engine.predicate("""{"firmament:display_name":"X"}""")
		assertFalse(p(plain))
		assertTrue(p(Stacks.sb(name = Component.literal("X"))))
	}

	@Test
	fun `G-PRED-5 keys in one object are ANDed`() {
		val p = engine.predicate("""{"apex:display_name":"A","apex:lore":"B"}""")
		assertTrue(p(both)); assertFalse(p(nameA)); assertFalse(p(loreB))
	}

	@Test
	fun `G-PRED-6 all and any`() {
		val all = engine.predicate("""{"apex:all":[{"apex:display_name":"A"},{"apex:lore":"B"}]}""")
		assertTrue(all(both)); assertFalse(all(nameA))
		val any = engine.predicate("""{"apex:any":[{"apex:display_name":"A"},{"apex:lore":"B"}]}""")
		assertTrue(any(nameA)); assertTrue(any(loreB)); assertFalse(any(plain))
	}

	@Test
	fun `G-PRED-7 QUIRK not over several keys is NOR`() {
		val p = engine.predicate("""{"apex:not":{"apex:display_name":"A","apex:lore":"B"}}""")
		assertFalse(p(both)); assertFalse(p(nameA)); assertFalse(p(loreB)); assertTrue(p(plain))
	}

	@Test
	fun `G-PRED-8 lore matches any line`() {
		val stack = Stacks.sb(lore = listOf(Component.literal("x"), Component.literal("§7Ability: Wither Impact")))
		assertTrue(engine.predicate("""{"apex:lore":{"regex":"Ability: .*"}}""")(stack))
		assertFalse(engine.predicate("""{"apex:lore":{"regex":"Nope.*"}}""")(stack))
	}

	@Test
	fun `G-PRED-9 item id`() {
		val p = engine.predicate("""{"apex:item":"minecraft:diamond_sword"}""")
		assertTrue(p(Stacks.sb(Items.DIAMOND_SWORD))); assertFalse(p(Stacks.sb(Items.IRON_SWORD)))
	}

	@Test
	fun `G-PRED-10 pull and pulling without a holder are false`() {
		assertFalse(engine.predicate("""{"pulling":1}""")(Stacks.sb(Items.BOW)))
		assertFalse(engine.predicate("""{"pull":0.4}""")(Stacks.sb(Items.BOW)))
	}

	private val petInfo = """{"type":"ENDER_DRAGON","exp":2.5E7,"tier":"LEGENDARY","candyUsed":0}"""
	private val hyperion = Stacks.sb(Items.IRON_SWORD, id = "HYPERION") {
		putString("modifier", "heroic")
		putInt("hot_potato_count", 10)
		put("gems", CompoundTag().apply { putString("JADE_0", "FINE") })
		put("runes", CompoundTag().apply { putInt("MUSIC", 3) })
		put("ability_scroll", ListTag().apply { add(StringTag.valueOf("IMPLOSION_SCROLL")); add(StringTag.valueOf("WITHER_SHIELD_SCROLL")) })
		putString("petInfo", petInfo)
		putString("x", Base64.getEncoder().encodeToString("""{"a":"b"}""".toByteArray()))
	}

	private fun nbt(body: String) = engine.predicate("""{"apex:extra_attributes":$body}""")(hyperion)

	@Test
	fun `G-NBT-1 exact string`() {
		assertTrue(nbt("""{"path":"modifier","match":"heroic"}"""))
		assertFalse(nbt("""{"path":"modifier","match":"Heroic"}"""))
	}

	@Test
	fun `G-NBT-2 numbers and number types`() {
		assertTrue(nbt("""{"path":"hot_potato_count","match":10}"""))
		assertTrue(nbt("""{"path":"hot_potato_count","match":{"int":{"min":10}}}"""))
		assertFalse(nbt("""{"path":"hot_potato_count","match":{"int":{"max":10}}}"""))
		assertTrue(nbt("""{"path":"hot_potato_count","match":{"int":{"max":10,"maxExclusive":false}}}"""))
		assertFalse(nbt("""{"path":"hot_potato_count","match":{"long":10}}"""))
	}

	@Test
	fun `G-NBT-3 list wildcard and index`() {
		assertTrue(nbt("""{"path":"ability_scroll.*","match":"IMPLOSION_SCROLL"}"""))
		assertFalse(nbt("""{"path":"ability_scroll.1","match":"IMPLOSION_SCROLL"}"""))
		assertTrue(nbt("""{"path":"ability_scroll.1","match":"WITHER_SHIELD_SCROLL"}"""))
	}

	@Test
	fun `G-NBT-4 compound wildcard`() {
		assertTrue(nbt("""{"path":"gems.*","match":"FINE"}"""))
	}

	@Test
	fun `G-NBT-5 json segment`() {
		assertTrue(nbt("""{"path":"petInfo.*json.type","match":"ENDER_DRAGON"}"""))
	}

	@Test
	fun `G-NBT-6 base64 then json`() {
		assertTrue(nbt("""{"path":"x.*base64.*json.a","match":"b"}"""))
	}

	@Test
	fun `G-NBT-7 inline match form`() {
		assertTrue(nbt("""{"path":"modifier","string":"heroic"}"""))
		assertFalse(nbt("""{"path":"modifier","string":"a","int":1}"""))
	}

	@Test
	fun `G-NBT-8 component predicate decodes and unknown component throws`() {
		val p = engine.predicate("""{"apex:component":{"component":"minecraft:custom_name","path":[],"match":"A"}}""")
		p(nameA)
		assertFalse(p(plain))

		assertFalse(engine.predicate("""{"apex:component":{"component":"minecraft:custom_name","match":"A"}}""")(nameA))
		assertThrows<Exception> {
			engine.predicate("""{"apex:component":{"component":"minecraft:no_such_component","path":[],"match":"x"}}""")
		}
	}

	private fun pet(json: String) = engine.predicate("""{"apex:pet":$json}""")

	@Test
	fun `G-PET-1 bare id without stripping`() {
		assertTrue(pet("\"ENDER_DRAGON\"")(hyperion))
		assertFalse(pet("\"§6ENDER_DRAGON\"")(hyperion))
	}

	@Test
	fun `G-PET-2 tier and level`() {
		val p = pet("""{"id":"ENDER_DRAGON","tier":"[EPIC,)","level":">=100"}""")
		val maxed = Stacks.sb(id = "PET") { putString("petInfo", """{"type":"ENDER_DRAGON","exp":1.0E9,"tier":"LEGENDARY","candyUsed":0}""") }
		assertTrue(p(maxed))
		assertTrue(pet("""{"id":"ENDER_DRAGON","tier":"[EPIC,)"}""")(hyperion))
		val fresh = Stacks.sb(id = "PET") { putString("petInfo", """{"type":"ENDER_DRAGON","exp":0,"tier":"LEGENDARY","candyUsed":0}""") }
		assertFalse(p(fresh))
	}

	@Test
	fun `G-PET-3 no pet info`() {
		assertFalse(pet("\"ENDER_DRAGON\"")(plain))
	}

	@Test
	fun `G-SKL-1 skull predicate`() {
		val value = Stacks.textureValue("http://textures.minecraft.net/texture/abc123")
		val head = Stacks.head(value)
		val p = engine.predicate("""{"apex:skull":{"textureValue":"$value"}}""")
		assertTrue(p(head))
		assertFalse(p(Stacks.sb(Items.PAPER)))
		assertFalse(p(Stacks.sb(Items.PLAYER_HEAD)))
		val uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc")
		assertTrue(engine.predicate("""{"apex:skull":{"profileId":"${uuid.toString().replace("-", "")}"}}""")(head))
		assertTrue(engine.predicate("""{"apex:skull":{"skinUrl":{"regex":".*abc123"}}}""")(head))
	}

	@Test
	fun `G-PFX-P1 apex-only, legacy-only and mixed keys`() {
		val x = Stacks.sb(name = Component.literal("X"), lore = listOf(Component.literal("L")))
		val xNoLore = Stacks.sb(name = Component.literal("X"))
		val apexOnly = engine.predicate("""{"apex:display_name":"X"}""")
		assertTrue(apexOnly(x)); assertFalse(apexOnly(plain))
		val mixed = engine.predicate("""{"apex:display_name":"X","firmament:lore":"L"}""")
		val legacyOnly = engine.predicate("""{"firmament:any":[{"firmament:lore":"L"}]}""")
		assertTrue(mixed(x)); assertFalse(mixed(xNoLore))
		assertTrue(legacyOnly(x)); assertFalse(legacyOnly(xNoLore))
		assertFalse(engine.predicate("""{"firmament:does_not_exist":1}""")(x))
	}
}
