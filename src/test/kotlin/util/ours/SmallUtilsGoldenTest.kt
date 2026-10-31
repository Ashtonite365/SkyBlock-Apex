// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.ours

import io.github.notenoughupdates.moulconfig.ChromaColour
import java.awt.Color
import java.util.TreeMap
import java.util.regex.Pattern
import kotlin.properties.ReadOnlyProperty
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import net.minecraft.core.BlockPos
import net.minecraft.world.item.DyeColor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.test.util.ours.Golden.Companion.golden
import dev.ashtonite365.apex.util.Base64Util
import dev.ashtonite365.apex.util.Base64Util.padToValidBase64
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.IdentityCharacteristics
import dev.ashtonite365.apex.util.IntUtil.toRGBA
import dev.ashtonite365.apex.util.IntUtil.toRomanNumeral
import dev.ashtonite365.apex.util.LegacyFormattingCode
import dev.ashtonite365.apex.util.SHORT_NUMBER_FORMAT
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.SortedMapSerializer
import dev.ashtonite365.apex.util.StringUtil
import dev.ashtonite365.apex.util.StringUtil.camelWords
import dev.ashtonite365.apex.util.StringUtil.title
import dev.ashtonite365.apex.util.StringUtil.unwords
import dev.ashtonite365.apex.util.StringUtil.words
import dev.ashtonite365.apex.util.TIME_PATTERN
import dev.ashtonite365.apex.util.ShareCodes
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.nonNullOr
import dev.ashtonite365.apex.util.trueOr
import dev.ashtonite365.apex.util.errorBoundary
import dev.ashtonite365.apex.util.getRGBAWithoutAnimation
import dev.ashtonite365.apex.util.ifDropLast
import dev.ashtonite365.apex.util.ifMatches
import dev.ashtonite365.apex.util.intoOptional
import dev.ashtonite365.apex.util.iterate
import dev.ashtonite365.apex.util.map
import dev.ashtonite365.apex.util.parseDashlessUUID
import dev.ashtonite365.apex.util.parsePotentiallyDashlessUUID
import dev.ashtonite365.apex.util.parseShortNumber
import dev.ashtonite365.apex.util.parseShortNumberOrNull
import dev.ashtonite365.apex.util.parseTimePattern
import dev.ashtonite365.apex.util.siScalars
import dev.ashtonite365.apex.util.toChromaWithoutAnimation
import dev.ashtonite365.apex.util.toShedaniel
import dev.ashtonite365.apex.util.toTextColor
import dev.ashtonite365.apex.util.toTicks
import dev.ashtonite365.apex.util.useMatch

@ExtendWith(AutoBootstrapExtension::class)
class SmallUtilsGoldenTest {
	@Test
	fun stringAndInt() = golden("small-string-int") {
		val f = Fuzz(21)
		val strs = listOf("", " ", "a b", "  x  ", "camelCaseWord", "HTTPServer", "aB cD", "already Title", "éa", "ǆx", "1,234", "1,2,3", "-5,000", "12a", "", ",")
		for (s in strs + List(80) { f.str("aB c,D1é", 9) }) {
			case("words $s") { s.words().toList() }
			case("camel $s") { s.camelWords().toList() }
			case("title $s") { s.title() }
			case("int $s") { StringUtil.parseIntWithComma(s) }
			case("next $s") { StringUtil.nextLexicographicStringOfSameLength(s) }
		}
		val max = Character.MAX_VALUE.toString()
		for (s in listOf("a$max", "$max$max", max, "z", "a${max}b", "\u0000")) case("next ${s.map { it.code }}") { StringUtil.nextLexicographicStringOfSameLength(s).map { it.code } }
		case("unwords") { listOf("a", "", "b c").unwords() }
		case("unwordsEmpty") { emptyList<String>().unwords() }
		for (i in -3..4005) case("roman $i") { i.toRomanNumeral() }
		for (i in listOf(Int.MIN_VALUE, Int.MAX_VALUE, 0, -1, 0x12345678, 0x80FF00FF.toInt())) case("rgba $i") { i.toRGBA() }
		repeat(40) { val i = f.r.nextInt(); case("rgba $i") { i.toRGBA().let { listOf(it.r, it.g, it.b, it.a, it.hashCode(), it) } } }
		case("rgbaData") { val a = 5.toRGBA(); listOf(a == 5.toRGBA(), a.copy(a = 9), a.component1(), a.component4()) }
	}

	@Test
	fun legacyCodes() = golden("small-legacy-codes") {
		for (e in LegacyFormattingCode.entries) case(e.name) { listOf(e.ordinal, e.label, e.char, e.index, e.modern, e.formattingCode) }
		case("byCode") { LegacyFormattingCode.byCode.entries.map { "${it.key}=${it.value}" } }
		case("byCodeMiss") { LegacyFormattingCode.byCode['z'] }
	}

	@Test
	fun regexAndNumbers() = golden("small-regex") {
		case("TIME_PATTERN") { TIME_PATTERN }
		case("SHORT_NUMBER_FORMAT") { SHORT_NUMBER_FORMAT }
		case("siScalars") { siScalars.entries.map { "${it.key}=${it.value}" } }
		val r = Regex("([a-z]+)([0-9]*)")
		val f = Fuzz(22)
		for (s in listOf("abc12", "abc", "12", "", "ab c") + List(40) { f.str("ab1 ", 5) }) {
			case("ifMatches $s") { s.ifMatches(r) { it.groupValues } }
			case("useMatch $s") { Pattern.compile("([a-z]+)([0-9]*)").useMatch(s) { group(1) + "|" + group(2) } }
			case("ifDropLast $s") { s.ifDropLast("1") { "<$it>" } }
			case("ifDropLastEmpty $s") { s.ifDropLast("") { "<$it>" } }
		}
		case("useMatchNull") { Pattern.compile("x").useMatch(null) { "hit" } }
		for (s in listOf("5m", "0s", "120s", "7m", "12", "3h", "m", "-4s", "10S")) case("time $s") { parseTimePattern(s) }
		val nums = listOf("1.2k", "3M", "48§7 Mana", "1,234", "-5", "+.5k", "abc", "", "1.2.3", "7b", "§a12§r", "1,000,000.5", "9B", ".5", "12x")
		for (s in nums + List(60) { f.str("0123,.kMb§a +-", 7) }) {
			case("short $s") { parseShortNumber(s) }
			case("shortN $s") { parseShortNumberOrNull(s) }
			case("shortRe $s") { Regex(SHORT_NUMBER_FORMAT).matches(s) }
			case("timeRe $s") { Regex(TIME_PATTERN).matches(s) }
		}
	}

	@Test
	fun formatters() = golden("small-formatters") {
		val f = Fuzz(23)
		val doubles = listOf(0.0, 0.5, 1.0, 9.99, 9.95, 10.0, 99.4, 99.95, 999.0, 999.5, 1000.0, 1049.0, 1050.0, 9999.0, 10_000.0, 99_999.0, 100_000.0,
			999_999.0, 1e6, 1.5e9, 2.5e12, 1e15, -1.0, -1234.0, -0.0, 0.001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.95)
		for (d in doubles + List(100) { Math.pow(10.0, f.r.nextDouble() * 14) * (if (f.r.nextInt(5) == 0) -1 else 1) }) {
			case("short $d") { ApexFormatters.compactNumber(d) }
			case("dist $d") { ApexFormatters.distanceText(d) }
			case("pct $d") { ApexFormatters.percentText(d) }
			case("pct3 $d") { ApexFormatters.percentText(d, 3) }
			case("pct0 $d") { ApexFormatters.percentText(d, 0) }
			for (fd in 0..3) {
				case("commasD $d $fd") { ApexFormatters.withThousands(d, fd) }
				case("commasDs $d $fd") { ApexFormatters.withThousands(d, fd, includeSign = true) }
				case("commasF $d $fd") { ApexFormatters.withThousands(d.toFloat(), fd) }
			}
		}
		val longs = listOf(0L, 1L, 999L, 1000L, 1001L, 999_999L, 1_000_000L, 1_234_567_890L, -1L, -1000L, -1_000_001L, Long.MAX_VALUE, Long.MIN_VALUE, Long.MIN_VALUE + 1)
		for (l in longs + List(60) { f.r.nextLong() shr f.r.nextInt(63) }) {
			case("commasL $l") { ApexFormatters.withThousands(l) }
			case("commasLs $l") { ApexFormatters.withThousands(l, includeSign = true) }
			case("commasLseg $l") { ApexFormatters.withThousands(l, groupSize = 4) }
			case("commasI $l") { ApexFormatters.withThousands(l.toInt()) }
			case("size $l") { ApexFormatters.fileSizeText(l) }
		}
		val durs = listOf(Duration.ZERO, 1.milliseconds, 99.milliseconds, 100.milliseconds, 1999.milliseconds, 4999.milliseconds, 5.seconds, 5001.milliseconds,
			61.seconds, 3600.seconds, 90061.seconds, 400000.seconds, -1500.milliseconds, -90.seconds, Duration.INFINITE, -Duration.INFINITE, 1.nanoseconds, 2.minutes)
		for (d in durs + List(60) { (f.r.nextLong(0, 400_000_000) * (if (f.r.nextBoolean()) 1 else -1)).milliseconds }) {
			case("span $d") { ApexFormatters.timespanText(d) }
			case("spanMs $d") { ApexFormatters.timespanText(d, millis = true) }
		}
		for (b in listOf(true, false)) for (g in listOf(true, false)) case("bool $b $g") { ApexFormatters.boolText(b, g).toString() }
		case("boolDefault") { ApexFormatters.boolText(false).toString() }
		case("pos") { ApexFormatters.positionText(BlockPos(-1, 64, 30_000_000)).toString() }
		val root = java.nio.file.Files.createTempDirectory("apexgolden")
		try {
			val file = root.resolve("f.bin"); java.nio.file.Files.write(file, ByteArray(5000))
			val dir = root.resolve("d"); java.nio.file.Files.createDirectories(dir.resolve("a")); java.nio.file.Files.createDirectories(dir.resolve("b"))
			for ((n, p) in listOf("missing" to root.resolve("nope"), "file" to file, "dir" to dir))
				case("path $n") { ApexFormatters.debugPath(p).toString().replace(root.toString(), "<root>") }
		} finally {
			root.toFile().deleteRecursively()
		}
	}

	@Test
	fun timeMarks() = golden("small-timemark") {
		val ctor = TimeMark::class.java.getDeclaredConstructor(Long::class.javaPrimitiveType).also { it.isAccessible = true }
		fun tm(l: Long) = ctor.newInstance(l)
		val vals = listOf(0L, 1L, 1000L, 1_700_000_000_000L, -5L)
		for (a in vals) {
			case("str $a") { tm(a).toString() }
			case("hash $a") { tm(a).hashCode() }
			for (b in vals) {
				case("minus $a $b") { tm(a) - tm(b) }
				case("passedAt $a $b") { tm(a).passedAt(tm(b)) }
				case("cmp $a $b") { tm(a).compareTo(tm(b)) }
				case("eq $a $b") { tm(a) == tm(b) }
			}
		}
		case("farPast") { TimeMark.farPast().toString() }
		case("farPastPassed") { TimeMark.farPast().passedTime() }
		case("agoInf") { TimeMark.ago(Duration.INFINITE).toString() }
		case("agoNegInf") { TimeMark.ago(-Duration.INFINITE).toString() }
		case("agoSane") { val d = TimeMark.ago(10.seconds).passedTime(); d >= 10.seconds && d < 20.seconds }
		case("nowSane") { (TimeMark.now() - TimeMark.ago(1.minutes)).inWholeSeconds in 59..61 }
		case("nowVsFar") { TimeMark.now() - TimeMark.farPast() }
		case("farVsNow") { TimeMark.farPast() - TimeMark.now() }
		for (d in listOf(Duration.ZERO, 49.milliseconds, 50.milliseconds, 1.seconds, -120.milliseconds, 3.minutes, Duration.INFINITE)) case("ticks $d") { d.toTicks() }
	}

	@Test
	fun encodings() = golden("small-encodings") {
		val f = Fuzz(24)
		for (s in listOf("", "a", "ab", "abc", "abcd", "YWJj", "YWJjZA", "YW", "Y", "!!", "YWJjZA==", "§", "aGk=\n") + List(40) { f.str("ABCxyz019+/=", 9) }) {
			case("pad $s") { s.padToValidBase64() }
			case("decB $s") { Base64Util.decodeBytes(s) }
			case("decS $s") { Base64Util.decodeString(s) }
			case("tplDec $s") { ShareCodes.decodeBase64OrNull(s) }
			case("tplPrefix $s") { ShareCodes.sharePrefixOf(s) }
			case("tplSafe $s") { ShareCodes.base64PrefixKey(s) }
			case("tplInto $s") { ShareCodes.encodeBase64Text(s) }
		}
		for (b in listOf(ByteArray(0), byteArrayOf(-1, 0, 1), ByteArray(7) { it.toByte() })) case("enc ${b.toList()}") { Base64Util.encodeToString(b) }
		val ser = MapSerializer(String.serializer(), Int.serializer())
		case("tplEnc") { ShareCodes.toShareCode("apex/test/", mapOf("a" to 1, "b" to 2), ser) }
		case("tplEncBad") { ShareCodes.toShareCode("apex", mapOf("a" to 1), ser) }
		val enc = ShareCodes.toShareCode("apex/test/", mapOf("a" to 1, "b" to 2), ser)
		for ((n, d) in listOf("ok" to enc, "ws" to "  $enc\n", "other" to ShareCodes.encodeBase64Text("apex/other/{}"), "garbage" to "!!!",
			"badjson" to ShareCodes.encodeBase64Text("apex/test/{nope"), "prefixOnly" to ShareCodes.encodeBase64Text("apex/test/"),
			"lenient" to ShareCodes.encodeBase64Text("apex/test/{\"a\":1,\"zz\":\"x\"}"),
			"wrongType" to ShareCodes.encodeBase64Text("apex/test/{\"a\":\"x\"}")))
			case("tplDecode $n") { ShareCodes.fromShareCode("apex/test/", d, ser) }
		case("tplDecodeBad") { ShareCodes.fromShareCode("apex", enc, ser) }
		for (s in listOf("d3cb85e2-3075-48a1-b213-a9bfb62360c1", "d3cb85e2307548a1b213a9bfb62360c1", "ffffffffffffffffffffffffffffffff", "0000000000000000000000000000000000",
				"123", "zz", "1-2-3-4-5", "D3CB85E2307548A1B213A9BFB62360C1", "-1")) {
			case("uuid $s") { parsePotentiallyDashlessUUID(s) }
			case("uuidDashless $s") { parseDashlessUUID(s) }
		}
	}

	@Test
	fun misc() = golden("small-misc") {
		case("iterate") { 1.iterate { if (it < 40) it * 3 else null }.toList() }
		case("iterateOnce") { "x".iterate { null }.toList() }
		case("iterateLazy") { var calls = 0; val s = 1.iterate { calls++; it + 1 }; s.take(3).toList(); calls }
		for (d in DyeColor.entries) {
			case("dye ${d.name}") { d.toShedaniel().color }
			case("dyeText ${d.name}") { d.toTextColor().value }
		}
		for (c in listOf(Color(1, 2, 3, 4), Color(255, 0, 128), Color(0, 0, 0, 0))) {
			case("chroma $c ${c.alpha}") { c.toChromaWithoutAnimation().toLegacyString() }
			case("chromaT $c") { c.toChromaWithoutAnimation(5000).toLegacyString() }
			case("chromaBack $c") { c.toChromaWithoutAnimation().getRGBAWithoutAnimation().rgb }
		}
		case("chromaAnim") { ChromaColour.forLegacyString("0:255:10:20:30:40").getRGBAWithoutAnimation().rgb }
		val ser = SortedMapSerializer(String.serializer(), Int.serializer())
		case("sortedDesc") { ser.descriptor.serialName + " " + ser.descriptor.kind }
		case("sortedDecode") { Json.decodeFromString(ser, "{\"b\":2,\"a\":1,\"c\":0}").let { "${it.javaClass.simpleName} $it" } }
		case("sortedEncode") { Json.encodeToString(ser, TreeMap(mapOf("z" to 1, "a" to 2))) }
		val p = ReadOnlyProperty<Any?, Int> { _, _ -> 20 }.map { it * 2 + 1 }
		case("propMap") { p.getValue(null, SmallUtilsGoldenTest::dummy) }
		case("optional") { listOf(null.intoOptional<String>(), "x".intoOptional()) }
		val o = Any(); val o2 = String(charArrayOf('a'))
		case("identity") { listOf(IdentityCharacteristics(o) == IdentityCharacteristics(o), IdentityCharacteristics(o2) == IdentityCharacteristics(String(charArrayOf('a'))),
			IdentityCharacteristics(o).hashCode() == System.identityHashCode(o), IdentityCharacteristics(o).equals("x"), IdentityCharacteristics(null) == IdentityCharacteristics(null)) }
		case("errorBoundary") { errorBoundary { 5 } }
		case("errorBoundaryThrow") { errorBoundary<Int> { error("boom") } }
		case("assertNotNullOk") { nonNullOr("v") { "fallback" } }
		case("assertNotNullNull") { nonNullOr<String>(null) { "fallback" } }
		case("assertNotNullMsg") { nonNullOr<String>(null, "msg") { "fallback" } }
		case("assertTrueOk") { var hit = false; trueOr(true) { hit = true }; hit }
		case("assertTrueFail") { var hit = false; trueOr(false) { hit = true }; hit }
		case("aggressive") { Faults.strictFaults }
		case("softCheckOk") { Faults.expectOrReport("m", true); "ok" }
		case("softCheckFail") { Faults.expectOrReport("m", false); "ok" }
		case("lazyCheckOk") { Faults.debugCheck("m") { true }; "ok" }
		case("lazyCheckFail") { Faults.debugCheck("m") { false }; "ok" }
		case("softError") { Faults.reportFault("m"); "ok" }
		case("softErrorExc") { try { Faults.reportFault("m", RuntimeException("inner")); "ok" } catch (e: IllegalStateException) { "${e.message} cause=${e.cause}" } }
		case("logError") { Faults.logFault("logged"); Faults.logFault("logged", RuntimeException("x")); "ok" }
		case("catchOk") { Faults.attempt("m") { 5 }.let { listOf(it.value, it.failure, it.valueOrNull(), it.orElse { -1 }) } }
		case("catchFail") { Faults.attempt("m") { error("bad") }.value }
		case("catchJava") { Faults.attempt<Int>("m", io.github.notenoughupdates.moulconfig.internal.InitUtil.ThrowingSupplier { 7 }).valueOrNull() }
		case("catchStatic") { Faults.Attempt.fail<Int>(RuntimeException("q")).let { listOf(it.value, it.failure?.message, it.valueOrNull(), it.orElse { e -> e.message!!.length }) } }
		case("catchSucceed") { Faults.Attempt.succeed("v").orElse { "no" } }
		case("notNullOk") { Faults.presentOr("a", "m") { "b" } }
		case("notNullNull") { Faults.presentOr<String>(null, "m") { "b" } }
		case("userError") { Faults.reportUserError("user did it"); "ok" }
		val islands = listOf(SkyBlockIsland.HUB, SkyBlockIsland.RIFT, SkyBlockIsland.PRIVATE_ISLAND, SkyBlockIsland.DUNGEON_HUB, SkyBlockIsland.JERRY_WORKSHOP,
			SkyBlockIsland.DUNGEON, SkyBlockIsland.KUUDRA, SkyBlockIsland.GOLD_MINE, SkyBlockIsland.DEEP_CAVERNS, SkyBlockIsland.DWARVEN_MINES,
			SkyBlockIsland.CRYSTAL_HOLLOWS, SkyBlockIsland.MINESHAFT, SkyBlockIsland.SPIDER, SkyBlockIsland.END, SkyBlockIsland.CRIMSON_ISLE,
			SkyBlockIsland.FARMING_ISLANDS, SkyBlockIsland.GARDEN, SkyBlockIsland.PARK, SkyBlockIsland.GALATEA, SkyBlockIsland.BACKWATER_BAYOU, SkyBlockIsland.NIL)
		case("islands") { islands.map { it.serverMode } }
		case("islandIntern") { listOf(SkyBlockIsland.ofMode("hub") === SkyBlockIsland.HUB, SkyBlockIsland.ofMode("xyz") === SkyBlockIsland.ofMode("xyz"), SkyBlockIsland.ofMode("Hub") === SkyBlockIsland.HUB) }
		case("islandSer") { Json.encodeToString(SkyBlockIsland.ModeSerializer, SkyBlockIsland.GARDEN) }
		case("islandDe") { Json.decodeFromString(SkyBlockIsland.ModeSerializer, "\"mining_3\"") === SkyBlockIsland.DWARVEN_MINES }
		case("islandDesc") { SkyBlockIsland.ModeSerializer.descriptor.let { "${it.serialName} ${it.kind}" } }
		case("islandEq") { SkyBlockIsland.HUB == SkyBlockIsland.ofMode("hub") && SkyBlockIsland.HUB != SkyBlockIsland.RIFT }
	}

	val dummy: Int = 0
}
