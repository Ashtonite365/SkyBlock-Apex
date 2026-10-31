// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.itemlist

import dev.ashtonite365.apex.features.items.recipes.itemlist.MobPreview
import dev.ashtonite365.apex.features.items.recipes.itemlist.PageActions
import dev.ashtonite365.apex.features.items.recipes.itemlist.PageLine
import dev.ashtonite365.apex.features.items.recipes.itemlist.PageMobs
import dev.ashtonite365.apex.features.items.recipes.itemlist.PageText
import dev.ashtonite365.apex.features.items.recipes.itemlist.ScrollLinesBlock
import dev.ashtonite365.apex.features.items.recipes.itemlist.WrappedTextBlock
import dev.ashtonite365.apex.repo.mobs.MobEntry
import dev.ashtonite365.apex.test.golden.HeadlessGui
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import me.shedaniel.math.Rectangle
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class PageDrawingGoldenTest {
	private fun <T> a(f: () -> T): String = try { f().toString() } catch (t: Throwable) { "ERR:" + t.javaClass.simpleName }
	private fun lit(s: String): Component = Component.literal(s)

	@Test
	fun characterize() = HeadlessGui.withFakeGame {
		val out = mutableListOf<String>()
		val texts = listOf("", "short", "§lBold heading text that is long enough to wrap twice or more in a narrow box", "a b c d e f g h i j k l m n o p q r s t u v w x y z", "Averyveryverylongwordwithoutanyspacesatallthatmustbreak")
		for (t in texts) for (w in listOf(1, 20, 60, 120, 400)) {
			out += "rows '$t' $w -> ${a { PageText.rows(lit(t), w) }} wrap=${a { PageText.wrap(lit(t), w).size }}"
		}

		for ((x, y, w) in listOf(Triple(0, 0, 100), Triple(10, 20, 40), Triple(5, 5, 300))) {
			val lines = listOf(lit("first line"), lit("§7a second, rather long line that wraps"), Component.empty(), lit("§bLast"))
			val b = WrappedTextBlock(x, y, w, lines)
			out += "wrapped $x,$y,$w bounds=${a { b.bounds() }}"
			out += "  draw " + a { HeadlessGui.record { b.draw(it) }.joinToString(" | ") }
		}
		out += "wrapped empty bounds=" + a { WrappedTextBlock(3, 4, 50, emptyList()).bounds() } + " draw " + a { HeadlessGui.record { WrappedTextBlock(3, 4, 50, emptyList()).draw(it) }.joinToString(" | ") }

		val fired = mutableListOf<Int>()
		val lines = (0 until 30).map { i ->
			when {
				i % 7 == 3 -> PageLine(lit("§b> link number $i with a long label that needs wrapping in the box")) { fired += i }
				i % 5 == 0 -> PageLine(lit("§lHeading $i"))
				else -> PageLine(lit("§7note $i"))
			}
		}
		for (box in listOf(Rectangle(10, 10, 120, 80), Rectangle(0, 0, 300, 400), Rectangle(5, 5, 30, 30))) {
			val s = ScrollLinesBlock(box, lines)
			fired.clear()
			out += "scroll box $box"
			out += "  draw0 " + a { HeadlessGui.record { s.draw(it) }.joinToString(" | ") }
			for (amt in listOf(-1.0, -1.0, -3.0, -100.0, 2.0, 0.5, 100.0, -0.5)) {
				out += "  scroll inside $amt -> ${a { s.scroll(box.x + 1.0, box.y + 1.0, amt) }} outside -> ${a { s.scroll(box.maxX + 5.0, box.y + 1.0, amt) }}"
				out += "    draw " + a { HeadlessGui.record { s.draw(it) }.joinToString(" | ").hashCode() }
			}
			s.scroll(box.x + 1.0, box.y + 1.0, 100.0)
			for (yy in box.y - 2 until box.maxY + 3 step 3) {
				val r = a { s.click(box.x + 4.0, yy.toDouble()) }
				out += "  click y=$yy -> $r fired=$fired"
			}
			out += "  click outside x -> " + a { s.click(box.maxX + 1.0, box.y + 5.0) } + " fired=$fired"
			s.scroll(box.x + 1.0, box.y + 1.0, -4.0)
			for (yy in box.y until box.maxY step 5) out += "  after scroll click y=$yy -> ${a { s.click(box.x + 4.0, yy.toDouble()) }} fired=$fired"
			out += "  draw end " + a { HeadlessGui.record { s.draw(it) }.joinToString(" | ") }
		}
		out += "scroll empty " + a { ScrollLinesBlock(Rectangle(0, 0, 50, 50), emptyList()).let { s -> listOf(s.scroll(1.0, 1.0, -1.0), s.click(2.0, 2.0), HeadlessGui.record { g -> s.draw(g) }.size) } }

		val mobs = listOf(MobEntry("ZOMBIE_TEST", "Zombie", 5), MobEntry("UNKNOWN_THING_X", "Thing"), MobEntry("", ""))
		for (m in mobs) {
			out += "mob ${m.id} unknown=${a { PageMobs.isUnknown(m) }} entity=${a { PageMobs.entityFor(m)?.javaClass?.simpleName }} fallback=${a { PageMobs.fallbackFor(m)?.javaClass?.simpleName }}"
			val p = MobPreview(m, Rectangle(20, 30, 40, 60), listOf(lit("Zombie"), lit("§7Level 5")))
			for ((mx, my) in listOf(0 to 0, 20 to 30, 40 to 50, 59 to 89, 60 to 90, 61 to 91)) out += "  tooltipAt $mx,$my -> ${a { p.tooltipAt(mx, my).map { it.string } }}"
			out += "  draw " + a { HeadlessGui.record { p.draw(it, 30, 40) }.joinToString(" | ") }
			out += "  draw again " + a { HeadlessGui.record { p.draw(it, 0, 0) }.joinToString(" | ") }
			out += "  tooltip after draw " + a { p.tooltipAt(30, 40).map { it.string } }
		}
		out += "clear " + a { PageMobs.clear() }
		out += "after clear " + a { PageMobs.isUnknown(mobs[0]) }
		out += "confirmLink " + a { PageActions.confirmLink("https://example.invalid/wiki") }
		out += "confirmLink bad " + a { PageActions.confirmLink("not a url %%") }
		OursGolden.check("pagedrawing", out)
	}
}
