// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.ours

import net.minecraft.nbt.ByteArrayTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.EndTag
import net.minecraft.nbt.IntArrayTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.LongArrayTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.test.util.ours.Golden.Companion.golden
import dev.ashtonite365.apex.util.LegacyTagParser
import dev.ashtonite365.apex.util.LegacyTagWriter
import dev.ashtonite365.apex.util.LegacyTagWriter.Notation.toLegacyString
import dev.ashtonite365.apex.util.mc.SNbtFormatter
import dev.ashtonite365.apex.util.mc.SNbtFormatter.Companion.toPrettyString

@ExtendWith(AutoBootstrapExtension::class)
class NbtTextGoldenTest {
	private fun tags(seed: Long, n: Int, arrays: Boolean = true): List<Tag> {
		val f = Fuzz(seed)
		val fixed = listOf<Tag>(
			CompoundTag(), ListTag(), EndTag.INSTANCE, StringTag.valueOf(""), IntTag.valueOf(0),
			ByteArrayTag(byteArrayOf()), IntArrayTag(intArrayOf(1, -2)), LongArrayTag(longArrayOf(Long.MIN_VALUE)),
			CompoundTag().also { it.put("x", ListTag()); it.put("y", CompoundTag()); it.putString("weird key", "v\"q") },
		)
		return fixed + List(n) { if (it % 3 == 0) f.compound() else f.tag(0, arrays) }
	}

	@Test
	fun writers() = golden("nbt-writers") {
		for ((i, t) in tags(31, 400).withIndex()) {
			case("$i compact") { t.toLegacyString() }
			case("$i pretty") { t.toLegacyString(pretty = true) }
			case("$i stringify") { LegacyTagWriter.stringify(t, true) == t.toLegacyString() }
			case("$i compactFlag") { LegacyTagWriter(false).compact }
			case("$i snbt") { t.toPrettyString() }
			case("$i prettify") { SNbtFormatter.prettify(t) == t.toPrettyString() }
		}
		for (k in listOf("", "a", "A.b+c-d_9", "has space", "quo\"te", "é", "x:y", "back\\slash", "tab\t", "0")) {
			case("escapeName $k") { SNbtFormatter.escapeName(k) }
			case("simple $k") { SNbtFormatter.SIMPLE_NAME.matches(k) }
		}
		case("simplePattern") { SNbtFormatter.SIMPLE_NAME.pattern }
	}

	private fun Golden.parseCase(label: String, input: String) = case(label) {
		try {
			val t = LegacyTagParser.parse(input)
			"OK " + t.toString()
		} catch (e: LegacyTagParser.MalformedTag) {
			if (e.reason.startsWith("Unexpected end of input")) "!EOF"
			else "TPE ${e.offset} ${e.reason} | ${e.message} | ${e.source == input}"
		} catch (e: NoSuchElementException) {
			"!EOF"
		} catch (e: IllegalStateException) {
			if (e.message == "HANG-GUARD") "!EOF" else throw e
		}
	}

	@Test
	fun parser() = golden("nbt-parser") {
		val fixed = listOf(
			"{}", " { } ", "{a:1}", "{a:1,}", "{a:1 b:2}", "{\"q k\":\"v\"}", "{a:\"x\\\"y\\\\z\"}", "{a:\"bad\\n\"}", "{a:\"unterminated}",
			"{a:[0:1,1:2,]}", "{a:[1,2,3]}", "{a:[5:\"x\",\"y\"]}", "{a:[]}", "{a:[{b:1b},{c:2s}]}", "{a:1.5f,b:2.5d,c:3L,d:4S,e:5B,f:6,g:7.25}",
			"{a:-1,b:-0.5,c:-.5f}", "{a:+1}", "{a:.5}", "{a:1.}", "{a:-}", "{a:999999999999}", "{a:300b}", "{a:1e5}", "{a:01}", "{a:1|}",
			"[]", "", "x", "{a}", "{a:}", "{a::1}", "{:1}", "{a:{b:{c:{}}}}", "{a:1}trailing", "{a:1}}", "{a:[1 2]}", "{a:[1,,2]}", "{a:[-:1]}",
			"{a:[-1:5]}", "{a:[12 : 5]}", "{ a : 1 , b : \"x\" }", "{\ta:\n1}", "{a:true}", "{a:\"\"}", "{\"\":1}", "{a:[\"x\",1,{}]}", "{a:1,a:2}",
			"{a:1F,b:1D,c:1l,d:1s,e:1b}", "{a:0.0001d,b:123456789012345L}", "{a:\"é§\"}", "{a:\"\\q\"}", "{a:\"\\", "{a:1", "{a:[1", "{a", "{",
			"{a:1, ", "{a:[1,2", "{a:\"x\"", "{ab", "{a:[0:", "{a:[0", "{a:1.5", "{a:-", "  ",
		)
		for (s in fixed) parseCase("fixed $s", s)
		val f = Fuzz(32)
		val valid = tags(33, 300, arrays = false).filterIsInstance<CompoundTag>()
		for ((i, t) in valid.withIndex()) {
			val compact = t.toLegacyString()
			val pretty = t.toLegacyString(pretty = true)
			parseCase("rt$i $compact", compact)
			parseCase("rtp$i", pretty)
			repeat(3) { m ->
				val sb = StringBuilder(compact)
				repeat(1 + f.r.nextInt(3)) {
					if (sb.isEmpty()) return@repeat
					val at = f.r.nextInt(sb.length)
					when (f.r.nextInt(3)) {
						0 -> sb.deleteCharAt(at)
						1 -> sb.insert(at, f.pick("{}[]:,\" 0123456789az.-+fFbBlLsSdD\\"))
						else -> sb.setCharAt(at, f.pick("{}[]:,\" 0123456789az.-+fFbBlLsSdD\\"))
					}
				}
				val s = sb.toString()
				parseCase("mut$i.$m $s", s)
			}
		}
		repeat(300) {
			val s = "{" + f.str("abc019.-+fFbBlLsSdD\"\\:{}[], ", 18) + "}"
			parseCase("rand $s", s)
		}
	}
}
