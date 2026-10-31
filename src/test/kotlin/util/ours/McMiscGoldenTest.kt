// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.ours

import io.github.notenoughupdates.moulconfig.common.MyResourceLocation
import io.github.notenoughupdates.moulconfig.observer.GetSetter
import java.nio.file.Files
import me.shedaniel.math.Color
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.test.util.ours.Golden.Companion.golden
import dev.ashtonite365.apex.util.BazaarPriceStrategy
import dev.ashtonite365.apex.util.HoverStackSource
import dev.ashtonite365.apex.util.SelfAttach
import dev.ashtonite365.apex.util.GuiXml
import dev.ashtonite365.apex.util.GuiXml.xmap
import dev.ashtonite365.apex.util.Timer
import dev.ashtonite365.apex.util.WarpTravel
import dev.ashtonite365.apex.util.XmlColours

@ExtendWith(AutoBootstrapExtension::class)
class McMiscGoldenTest {
	@Test
	fun misc() = golden("misc-small") {
		case("timer") { val t = Timer(); val a = t.timePassed(); t.markNow(); val b = t.timePassed() < kotlin.time.Duration.parse("10s"); t.markFarPast(); listOf(a, b, t.timePassed()) }
		case("pid") { SelfAttach.ownPid().all { it.isDigit() } }
		case("bazaar") { BazaarPriceStrategy.entries.toList() }
		case("ignoredWarps") { WarpTravel.neverTargets }
		val colours = listOf("#ff0000", "#FF00FF80", "#80ff00ff", "#fff", "#1234567", "#", "#zzzzzz", "123", "-1", "0", "4294967295", "-16777216", "abc", "", "#00000000", "#ffffffff", " 12", "#+12345", "#-12345", "2147483647", "2147483648")
		for (c in colours) case("colour $c") { colourUnderTest(c).let { listOf(it.red, it.green, it.blue, it.alpha, it.rgb) } }
		for (c in colours) case("shedColour $c") { shedColourUnderTest(c).let { listOf(it.red, it.green, it.blue, it.alpha, it.color) } }
		val f = Fuzz(77)
		repeat(200) { val c = (if (f.r.nextBoolean()) "#" else "") + f.str("0123456789abcdefABCDEF-xg", 9); case("colourFuzz $c") { colourUnderTest(c).rgb } }
		case("warpData") { dev.ashtonite365.apex.Apex.json.encodeToString(WarpTravel.LockedWarps.serializer(), WarpTravel.LockedWarps(mutableSetOf("a", "b"))) }
	}
}

private fun colourUnderTest(text: String): java.awt.Color = XmlColours.awt(text)

private fun shedColourUnderTest(text: String): Color = XmlColours.shedaniel(text)
