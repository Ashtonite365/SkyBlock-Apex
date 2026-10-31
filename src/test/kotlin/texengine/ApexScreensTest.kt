// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.texengine.ScreenHooks
import dev.ashtonite365.apex.texengine.apex.ApexScreens
import dev.ashtonite365.apex.texengine.apex.ApexTextColors
import dev.ashtonite365.apex.texengine.apex.ApexTexts
import dev.ashtonite365.apex.texengine.apex.TextMatch
import com.google.gson.JsonPrimitive
import com.google.gson.JsonObject
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.screens.inventory.HangingSignEditScreen
import net.minecraft.client.gui.screens.inventory.SignEditScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.Identifier
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.random.Random

@ExtendWith(AutoBootstrapExtension::class)
class ApexScreensTest {
	@AfterEach
	fun clean() {
		ApexScreens.use(listOf())
		ApexScreens.screenChanged(null)
		ApexTextColors.use(null)
		ApexTexts.use(listOf())
		TexConfig.reset()
	}

	private fun layouts(files: Map<String, String>) =
		ApexScreens.use(ApexScreens.load(FixturePacks.managerAt(BenchPacks.pack("m7-layouts-${files.hashCode()}",
			files.mapKeys { "assets/firmskyblock/overrides/screen_layout/${it.key}.json" }))))

	private fun texts(files: Map<String, String>) =
		ApexTexts.use(ApexTexts.load(FixturePacks.managerAt(BenchPacks.pack("m7-texts-${files.hashCode()}",
			files.mapKeys { "assets/firmskyblock/overrides/texts/${it.key}.json" }))))

	@Test
	fun `screen types pick the right layout`() {
		layouts(mapOf(
			"a" to """{"predicates":{"label":"Edit","screenType":"firmskyblock:hanging_sign"},"slots":[{"index":0,"x":1,"y":1}]}""",
			"b" to """{"predicates":{"label":"Edit","screenType":"firmskyblock:sign"},"slots":[{"index":0,"x":2,"y":2}]}""",
			"c" to """{"predicates":{"label":"Edit"},"slots":[{"index":0,"x":3,"y":3}]}""",
		))
		val title = Component.literal("Edit")
		ApexScreens.screenChanged(Fakes.screen(title, HangingSignEditScreen::class.java))
		assertEquals(1, ApexScreens.current!!.slotMoves.single().x)
		ApexScreens.screenChanged(Fakes.screen(title, SignEditScreen::class.java))
		assertEquals(2, ApexScreens.current!!.slotMoves.single().x)

		ApexScreens.screenChanged(Fakes.screen(title))
		assertEquals(3, ApexScreens.current!!.slotMoves.single().x)
		ApexScreens.screenChanged(null)
		assertNull(ApexScreens.current)
		assertNull(ScreenHooks.layout())
	}

	@Test
	fun `layout labels follow the text match rules and the toggle is read on screen change`() {
		layouts(mapOf("a" to """{"predicates":{"label":{"regex":"Auction.*"}},"containerTitle":{"y":3,"replace":"AH"}}"""))
		ApexScreens.screenChanged(Fakes.screen(Component.literal("§6Auction House")))
		val layout: dev.ashtonite365.apex.texengine.ActiveLayout = ApexScreens.current!!
		assertEquals("AH", layout.containerTitle.text(Component.literal("Auction House")).string)
		assertEquals(3, layout.containerTitle.y(99))
		assertEquals(10, layout.containerTitle.x(Fakes.alloc(net.minecraft.client.gui.Font::class.java), Component.literal("x"), 10))
		assertSame(ApexScreens.noLayoutTitle, layout.playerTitle)
		TexConfig.set("screen-layouts", false)
		ApexScreens.screenChanged(Fakes.screen(Component.literal("Auction House")))
		assertNull(ApexScreens.current)
	}

	@Test
	fun `broken layout files are skipped and the rest load`() {
		layouts(mapOf(
			"a_noLabel" to """{"predicates":{},"slots":[]}""",
			"b_badAlign" to """{"predicates":{"label":"X"},"playerTitle":{"align":"middle"}}""",
			"c_badBackground" to """{"predicates":{"label":"X"},"background":{"texture":"a:b","x":0}}""",
			"d_threeLines" to """{"predicates":{"label":"X"},"signLines":[{"x":0,"y":0},{"x":0,"y":0},{"x":0,"y":0}]}""",
			"e_ok" to """{predicates:{label:"X"},background:{texture:"a:b.png",x:1,y:2,width:3,height:4},nameField:{x:5,y:6,width:7},
				signLines:[{x:0,y:1},{x:0,y:2},{x:0,y:3},{x:0,y:4}],playerTitle:{align:"RIGHT"}}""",
		))
		ApexScreens.screenChanged(Fakes.screen(Component.literal("X")))
		val layout = ApexScreens.current!!
		assertEquals(Identifier.parse("a:b.png"), layout.background!!.texture)
		assertEquals(listOf(1, 2, 3, 4), with(layout.background!!) { listOf(x, y, width, height) })
		assertEquals(7, layout.nameField!!.width)
		assertNull(layout.nameField!!.height)
		assertEquals(4, layout.signLine(3)!!.y)
		assertNull(layout.signLine(4))
		assertSame(layout, ScreenHooks.layout())
		assertSame(layout.background, ScreenHooks.background())
	}

	@Test
	fun `title colours are remembered per component and forgotten on reload`() {
		ApexTextColors.use(ApexTextColors.Table(1, listOf(TextMatch.read(JsonPrimitive("Bazaar")) to 2)))
		val bazaar = Component.literal("Bazaar")
		assertEquals(2, ApexTextColors.colorFor(bazaar, 9))
		assertEquals(1, ApexTextColors.colorFor(Component.literal("Other"), 9))
		assertEquals(2, ApexTextColors.colorFor(bazaar, 9))
		ApexTextColors.use(ApexTextColors.Table(5, listOf()))
		assertEquals(5, ApexTextColors.colorFor(bazaar, 9))
		ApexTextColors.use(null)
		assertEquals(9, ApexTextColors.colorFor(bazaar, 9))

		ApexTextColors.use(ApexTextColors.Table(3, listOf()))
		assertEquals(3, ApexScreens.noLayoutTitle.color(bazaar, 9))
	}

	@Test
	fun `no replacement files return the same text and the same tooltip list`() {
		val text = Component.literal("Hello")
		assertSame(text, ApexTexts.rewrite(text))
		val lines = listOf(text, Component.literal("World"))
		assertSame(lines, ScreenHooks.lines(lines))
		texts(mapOf("a" to """{"match":{"regex":".*Nope.*"},"replacements":[{"match":"Nope","replace":"Yes"}]}"""))
		assertSame(text, ApexTexts.rewrite(text))
		assertSame(lines, ScreenHooks.lines(lines))
	}

	@Test
	fun `tooltip lists are copied only when a line changes`() {
		texts(mapOf("a" to """{"match":{"regex":".*World.*"},"replacements":[{"match":"World","replace":"There"}]}"""))
		val lines = listOf(Component.literal("Hello"), Component.literal("World"))
		val out = ScreenHooks.lines(lines)
		assertEquals(listOf("Hello", "There"), out.map { it.string })
		assertSame(lines[0], out[0])
		assertEquals("World", lines[1].string)
	}

	@Test
	fun `several hits, kept styles, unknown and empty groups`() {
		texts(mapOf("a" to """{"match":{"regex":".*x.*"},"replacements":[
			{"match":{"regex":"x(?<d>[0-9])?"},"replace":{"text":"[${'$'}{d}${'$'}{nope}]","color":"red"}}]}"""))
		val text = Component.literal("ax1bxc").withStyle(ChatFormatting.BOLD)
		val out = ApexTexts.rewrite(text)
		assertEquals("a[1${'$'}{nope}]b[${'$'}{nope}]c", out.string)
		val flat = out.toFlatList()
		assertTrue(flat.all { it.style.isBold }, "the part's style is kept")
		assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), flat.first { it.string.startsWith("[") }.style.color)
		assertEquals(null, flat.first().style.color)
	}

	@Test
	fun `replacement text without group references is reused`() {
		texts(mapOf("a" to """{"match":{"regex":".*Coins.*"},"replacements":[{"match":"Coins","replace":{"text":"C"}}]}"""))
		val a = ApexTexts.rewrite(Component.literal("1 Coins")).toFlatList().map { it.string }
		assertEquals(listOf("1 ", "C"), a)
	}

	@Test
	fun `broken text files are skipped`() {
		texts(mapOf(
			"a" to """{"match":"x"}""",
			"b" to """{"match":"x","replacements":[{"match":"x","style":{"color":"not a colour"},"replace":"y"}]}""",
			"c" to """{"match":{"regex":".*x.*"},"replacements":[{"match":"x","replace":"y"}]}""",
		))
		assertEquals("y", ApexTexts.rewrite(Component.literal("x")).string)
	}

	@Test
	fun `regex contains shortcut agrees with the regex`() {
		val sources = listOf(".*Coins.*", ".*a b.*", ".*x-y.*", ".*é.*", ".*.*", ".*a.c.*", ".*(a).*", ".*\\d.*", ".*a\nb.*")
		val rnd = Random(7)
		val alphabet = "aCoinsb x-yé\n\r\u2028.dc"
		val subjects = listOf("", "Coins", "1 Coins", "Co\nins", "a\nCoins", "x-y", "a b c", "a.c", "abc", "é") +
			(0 until 2000).map { (0 until rnd.nextInt(12)).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("") }
		for (source in sources) {
			val regex = Regex(source)
			val match = TextMatch.read(JsonObject().apply { addProperty("regex", source); addProperty("color", "preserve") })
			for (s in subjects) assertEquals(regex.matches(s), match.test(s), "regex '$source' on '$s'")
		}
		assertEquals("Coins", TextMatch.plainMiddle(".*Coins.*"))
		assertNull(TextMatch.plainMiddle(".*a.c.*"))
		assertNull(TextMatch.plainMiddle(".*.*"))
		assertFalse(TextMatch.hasLineBreak("plain"))
	}
}
