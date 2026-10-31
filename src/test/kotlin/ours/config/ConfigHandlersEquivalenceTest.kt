// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.github.notenoughupdates.moulconfig.ChromaColour
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.AllConfigsGui
import dev.ashtonite365.apex.gui.config.ApexConfigScreenProvider
import dev.ashtonite365.apex.gui.config.BooleanHandler
import dev.ashtonite365.apex.gui.config.ClickHandler
import dev.ashtonite365.apex.gui.config.ColourHandler
import dev.ashtonite365.apex.gui.config.DurationHandler
import dev.ashtonite365.apex.gui.config.HudPosition
import dev.ashtonite365.apex.gui.config.IntegerHandler
import dev.ashtonite365.apex.gui.config.IntegerInputHandler
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.gui.config.StringHandler
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.util.data.ManagedConfig

class ConfigHandlersEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	private fun attempt(block: () -> Any?): String = try {
		block().toString()
	} catch (e: Throwable) {
		"ERR:" + e.javaClass.simpleName + ":" + e.message
	}

	private val cfg: ManagedConfig get() = AllConfigsGui.ConfigConfig

	private fun <T : Any> codec(out: MutableList<String>, name: String, h: ManagedConfig.OptionCodec<T>, samples: List<T>, raw: List<JsonElement>) {
		for (v in samples) out += "$name enc $v -> ${attempt { h.encode(v) }} back=${attempt { h.encode(h.decode(h.encode(v)!!)) }}"
		for (j in raw) out += "$name dec $j -> ${attempt { h.decode(j) }}"
	}

	@Test
	fun handlersEncodeAndDecodeTheSame() {
		val out = mutableListOf<String>()
		val p = { s: String -> Json.parseToJsonElement(s) }
		codec(out, "bool", BooleanHandler(cfg), listOf(true, false), listOf(p("true"), p("\"false\""), p("1"), p("{}")))
		codec(out, "int", IntegerHandler(cfg, 0, 10), listOf(0, 5, -3, Int.MAX_VALUE), listOf(p("7"), p("\"8\""), p("7.5"), p("null"), p("[]")))
		val input = IntegerInputHandler(cfg, 5, 120)
		codec(out, "intInput", input, listOf(5, 200), listOf(p("1"), p("130"), p("60"), p("\"x\"")))
		for (t in listOf("", " 60 ", "4", "121", "120", "-5", "abc", "0060", "99999999999"))
			out += "parse '$t' -> ${input.parse(t)}"
		codec(out, "dur", DurationHandler(cfg, 1.seconds, 10.seconds), listOf(1500.milliseconds, 0.seconds, 2.5.seconds), listOf(p("250"), p("1.5"), p("\"9\"")))
		codec(out, "str", StringHandler(cfg), listOf("", "héllo", "a\"b"), listOf(p("\"x\""), p("5"), p("true"), p("{}")))
		val click = ClickHandler(cfg) {}
		out += "click enc ${click.encode(Unit)} dec ${attempt { click.decode(p("1")) }}"
		val colour = ColourHandler(cfg)
		val colours = listOf(ChromaColour(0.25f, 0.5f, 0.75f, 0, 128), ChromaColour(1f, 1f, 1f, 3000, 255), ChromaColour.forLegacyString("0:255:0:0:0"))
		for (c in colours) out += "colour enc ${attempt { colour.encode(c) }} legacy=${c.toLegacyString()} back=${attempt { colour.decode(colour.encode(c)!!).toLegacyString() }}"
		for (j in listOf("""{"h":0.1,"s":0.2,"b":0.3,"a":4,"c":5}""", """{"h":0.1}""", """{"c":5,"a":4,"b":0.3,"s":0.2,"h":0.1}"""))
			out += "colour dec $j -> ${attempt { colour.decode(p(j)).toLegacyString() }}"
		out += "chroma descriptor ${ColourHandler.ChromaSerializer.descriptor.serialName} ${ColourHandler.ChromaSerializer.descriptor.elementsCount}"
		out += "providers ${attempt { ApexConfigScreenProvider.providers.map { it.key } }}"
		OursGolden.check("config-handlers", out)
	}

	@Test
	fun managedOptionBehavesTheSame() {
		val out = mutableListOf<String>()
		val opt = ManagedOption(cfg, "zz-probe", { 5 }, IntegerHandler(cfg, 0, 10))
		out += "keys ${opt.rawLabelText} ${opt.descriptionTranslationKey} label=${opt.labelText.string} desc=${opt.labelDescription.string}"
		out += "unloaded ${attempt { opt.value }} raw=${opt._actualValue} json=${opt.toJson()} parentActive=${opt.parentActive} risk=${opt.riskLevel} forced=${attempt { opt.forcedOff }} tier=${attempt { opt.riskTier }}"
		val roots: List<JsonElement> = listOf(
			buildJsonObject { put("zz-probe", 7) },
			buildJsonObject { put("zz-probe", "bad") },
			buildJsonObject { put("other", 1) },
			JsonPrimitive(3),
			JsonObject(emptyMap()),
			buildJsonObject { put("zz-probe", 9) },
		)
		for (r in roots) {
			opt.load(r)
			out += "load $r -> ${opt.value} raw=${opt._actualValue} json=${opt.toJson()}"
		}
		opt.set(2)
		out += "set ${opt.get()} ${opt.value}"
		opt.value = 4
		out += "value ${opt.get()}"
		opt.labelOverride = net.minecraft.network.chat.Component.literal("Over")
		opt.descriptionOverride = net.minecraft.network.chat.Component.literal("Desc")
		out += "override ${opt.labelText.string} ${opt.labelDescription.string}"
		val flag = ManagedOption(cfg, "zz-flag", { true }, BooleanHandler(cfg))
		flag.load(JsonObject(emptyMap()))
		out += "flag ${flag.value} json=${flag.toJson()} desc=${flag.labelDescription.string}"
		OursGolden.check("config-option-probe", out)
	}

	@Test
	fun hudPlacementMathsIsTheSame() {
		val out = mutableListOf<String>()
		val screens = listOf(480 to 270, 427 to 240, 961 to 541)
		for ((sw, sh) in screens) for ((x, y) in listOf(0 to 0, 10 to 200, 230 to 120, 470 to 5, 213 to 119, -5 to 300)) {
			val pos = HudPosition(x, y, 1f)
			pos.migrate(41, 17, sw, sh)
			out += "migrate $sw x $sh ($x,$y) -> $pos resolve=${pos.resolve(41, 17, sw, sh)}"
			pos.place(x + 3, y + 1, 41, 17, sw, sh)
			out += "  place -> $pos resolve=${pos.resolve(41, 17, sw, sh)}"
			pos.dragTo(1.5f, 1.25f, x + 1, y + 2, 41, 17, sw, sh)
			out += "  drag -> $pos sx=${pos.effectiveScaleX} sy=${pos.effectiveScaleY}"
			pos.setUniformScale(2f)
			out += "  uniform -> $pos"
			pos.resetScale()
			out += "  reset -> $pos"
		}
		val p = HudPosition(3, 4, 1.5f)
		p.setScales(2f, 2f); out += "same $p"
		p.setScales(2f, 0.5f); out += "diff $p"
		p.setUniformScale(3f); out += "uni $p"
		val q = HudPosition(1, 2, 0f); q.setUniformScale(2f); out += "zero $q"
		val unanchored = HudPosition(5, 6, 1f); unanchored.dragTo(2f, 1f, 50, 60, 10, 10, 100, 100); out += "unanchored $unanchored"
		for (j in listOf("""{"x":1,"y":2,"scale":1.5}""", """{"x":1,"y":2,"scale":1.0,"anchor":"top-left","scaleX":2.0,"scaleY":0.5}"""))
			out += "json $j -> ${attempt { Json.decodeFromString(HudPosition.serializer(), j) }} -> ${attempt { Json.encodeToString(HudPosition.serializer(), Json.decodeFromString(HudPosition.serializer(), j)) }}"
		out += "enc ${Json.encodeToString(HudPosition.serializer(), HudPosition(7, 8, 0.5f))}"
		OursGolden.check("config-hud", out)
	}
}
