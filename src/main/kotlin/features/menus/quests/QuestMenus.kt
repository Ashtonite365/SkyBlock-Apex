// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.quests

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTitles
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.DynamicPage
import dev.ashtonite365.apex.features.menus.support.DynamicParse
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.menus.support.Tile
import dev.ashtonite365.apex.features.menus.support.TileGroup
import dev.ashtonite365.apex.features.menus.support.TileParse
import dev.ashtonite365.apex.features.menus.support.TileViewBase
import dev.ashtonite365.apex.util.data.Config

object QuestParser {

	val TITLE = MenuTitles.anyOf(Regex("^Quest Log(?: \\(\\d+/\\d+\\))?$"))
	const val CHEST_SIZE = 54
	const val ACTIVE = "Active quests"
	const val DONE = "Completed"
	const val ONGOING = "Ongoing"
	const val MORE = "More"

	fun parse(title: String, slots: List<SlotText>) = DynamicParse.parse(title, slots, TITLE, CHEST_SIZE)

	fun status(s: SlotText): String {
		val (done, total) = DynamicParse.objectives(s)
		return when {
			total == 0 -> MORE
			s.cleanLore.any { it.contains("ongoing", true) } -> ONGOING
			done == total -> DONE
			else -> ACTIVE
		}
	}

	fun info(s: SlotText): List<String> {
		val (done, total) = DynamicParse.objectives(s)
		if (total == 0) return listOfNotNull(TileParse.buttonTile(s).info.firstOrNull())
		return listOfNotNull("§7$done/$total steps", DynamicParse.nextObjective(s)?.let { "§e$it" })
	}

	fun groups(p: DynamicPage, highlightActive: Boolean = true): List<TileGroup> {
		val order = listOf(ACTIVE, ONGOING, DONE, MORE)
		return DynamicParse.groupBy(p.items, ::status).sortedBy { order.indexOf(it.first) }
			.map { (n, l) -> TileGroup(n, l.map { Tile(it.slot, it.cleanName, info(it), active = n == ACTIVE && highlightActive) }) }
	}
}

object ChapterParser {
	val TITLE = MenuTitles.anyOf(Regex("^Chapters(?: \\(\\d+/\\d+\\))?$"), Regex("^Chapters ➜ .+$"))
	const val CHEST_SIZE = 54
	const val IN_PROGRESS = "In progress"
	const val COMPLETE = "Complete"
	const val NOT_STARTED = "Not started"
	const val OTHER = "Other"
	const val LOCKED = "Locked"
	const val TODO = "To do"

	fun parse(title: String, slots: List<SlotText>) = DynamicParse.parse(title, slots, TITLE, CHEST_SIZE)
		?: if (matchesBySlots(title, slots)) DynamicParse.parse(title, slots, ANY, CHEST_SIZE) else null

	private val ANY = Regex(".+")

	fun isAreaPage(slots: List<SlotText>) = goBack(slots)?.equals("To Chapters", true) == true

	fun goBack(slots: List<SlotText>) = slots.firstOrNull { it.slot < CHEST_SIZE && it.cleanName == "Go Back" }?.cleanLore?.firstOrNull { it.isNotBlank() }

	fun isDetailPage(title: String, slots: List<SlotText>): Boolean {
		val t = MenuText.clean(title)
		val sep = t.indexOf('➜').takeIf { it > 0 } ?: return false
		val area = t.substring(0, sep).trim(); val chapter = t.substring(sep + 1).trim()
		if (area.isEmpty() || chapter.isEmpty()) return false
		if (goBack(slots)?.equals("To $area", true) != true) return false
		return slots.any { it.slot < CHEST_SIZE && it.cleanLore.any { l -> l.startsWith("$chapter Progress:", true) } }
	}

	fun matchesBySlots(title: String, slots: List<SlotText>) = isAreaPage(slots) || isDetailPage(title, slots)

	fun status(s: SlotText): String {
		val pct = DynamicParse.progressPct(s)
		val (done, total) = DynamicParse.objectives(s)
		return when {
			pct != null && pct >= 100.0 || total > 0 && done == total || TileParse.loreHas(s, "COMPLETED", "Chapter complete") -> COMPLETE
			s.cleanLore.any { it.equals("CLAIMED", true) || it.equals("COMPLETED", true) } -> COMPLETE
			TileParse.loreHas(s, "haven't unlocked", "Locked") -> LOCKED
			pct != null || total > 0 -> IN_PROGRESS
			s.cleanLore.any { it.startsWith("Reward", true) } -> TODO
			s.cleanLore.any { it.startsWith("Talk to", true) } -> NOT_STARTED
			else -> OTHER
		}
	}

	fun info(s: SlotText): List<String> {
		val pct = DynamicParse.progressPct(s)
		val (done, total) = DynamicParse.objectives(s)
		return when {
			pct != null -> listOfNotNull("§a$pct%" + (DynamicParse.fraction(s)?.let { " §8$it" } ?: ""), DynamicParse.nextObjective(s)?.let { "§e$it" })
			total > 0 -> listOfNotNull("§7$done/$total", DynamicParse.nextObjective(s)?.let { "§e$it" })
			s.cleanLore.any { it.startsWith("Reward", true) } -> listOfNotNull(
				s.cleanLore.dropWhile { !it.startsWith("Reward", true) }.drop(1).firstOrNull { it.isNotBlank() }?.let { "§b$it" },
				if (status(s) == COMPLETE) "§aClaimed" else null)
			else -> listOfNotNull(DynamicParse.action(s)?.let { "§7$it" } ?: TileParse.buttonTile(s).info.firstOrNull())
		}
	}

	fun groups(p: DynamicPage): List<TileGroup> {
		val order = listOf(IN_PROGRESS, TODO, NOT_STARTED, COMPLETE, LOCKED, OTHER)
		return DynamicParse.groupBy(p.items, ::status).sortedBy { order.indexOf(it.first) }
			.map { (n, l) -> TileGroup(n, l.map { Tile(it.slot, it.cleanName, info(it), active = n == IN_PROGRESS) }) }
	}
}

@Config
object QuestSettings : MenuSettings("quests") {
	val highlightActive by toggle("highlight-active") { true }
	val reset by button("reset") { resetToDefaults() }
}

@Config
object ChapterSettings : MenuSettings("chapters") {
	val reset by button("reset") { resetToDefaults() }
}

object QuestMenu : CustomMenu {
	override val id = "quests"
	override val displayName = "Quest Log"
	override val title = QuestParser.TITLE
	override val settings get() = QuestSettings
	override val defaultSize = 480 to 260
	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = QuestView(screen)
}

object ChapterMenu : CustomMenu {
	override val id = "chapters"
	override val displayName = "Chapters"
	override val title = ChapterParser.TITLE
	override val slotPredicate: (String, List<SlotText>) -> Boolean = { t, slots -> ChapterParser.matchesBySlots(t, slots) }
	override val settings get() = ChapterSettings
	override val defaultSize = 480 to 260
	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = ChapterView(screen)
}

class QuestView(screen: AbstractContainerScreen<*>) : TileViewBase<DynamicPage>(screen) {
	override fun palette() = QuestSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = QuestParser.parse(title, slots)
	override fun title(m: DynamicPage) = m.title
	override fun nav(m: DynamicPage) = m.nav
	override fun groups(m: DynamicPage) = QuestParser.groups(m, QuestSettings.highlightActive)

	private var editor: QuestNoteEditor? = null
	override val textFocused get() = editor != null

	override fun labelReserve(t: Tile, group: String) = if (QuestNoteUi.hasIcon(group)) QuestNoteUi.LABEL_RESERVE else 0

	override fun tileOverlay(ctx: GuiGraphicsExtractor, mx: Int, my: Int, tx: Int, ty: Int, t: Tile, group: String) {
		if (!QuestNoteUi.hasIcon(group)) return
		val r = QuestNoteUi.icon(tx, ty, TILE_W)
		val has = QuestNotes.note(t.label) != null
		val hov = editor == null && hovered(mx, my, r[0], r[1], r[2], r[3])
		MenuTheme.frame(ctx, r[0], r[1], r[0] + r[2], r[1] + r[3], if (hov) palette().fillHover else palette().fill,
			if (has) palette().accent else if (hov) palette().borderHover else palette().border)
		ctx.text(font, if (has) "§6✎" else "§7✎", r[0] + 2, r[1] + 1, MenuTheme.WHITE)

		area(r[0], r[1], r[2], r[3]) { b -> if (QuestNoteUi.iconAction(b) == QuestNoteUi.Action.OPEN_POPUP) open(t.label) }
	}

	override fun tileTooltip(t: Tile, group: String): List<Component>? {
		if (editor != null || !QuestNoteUi.hasIcon(group)) return null
		val note = QuestNotes.note(t.label) ?: return null
		val normal = stacks[t.slot]?.let { st -> Screen.getTooltipFromItem(Client.game, st) }
			?: (listOf(Component.literal(t.label)) + t.info.map { Component.literal(it) })
		val wrap = { s: String -> BzMenuLayout.wrapText(s, 200, font::width) }
		return normal + QuestNoteUi.tooltip(emptyList(), note, wrap).map { Component.literal(it) }
	}

	private fun open(questName: String) { editor = QuestNoteEditor(questName, QuestNotes.note(questName) ?: "") }

	private fun finish(save: Boolean) {
		val e = editor ?: return
		if (save) QuestNotes.save(e.questName, e.text)
		editor = null
	}

	override fun draw(ctx: GuiGraphicsExtractor, m: DynamicPage, mx: Int, my: Int) {
		val e = editor
		super.draw(ctx, m, if (e != null) -1 else mx, if (e != null) -1 else my)
		if (e != null) drawPopup(ctx, e, mx, my)
	}

	private fun drawPopup(ctx: GuiGraphicsExtractor, e: QuestNoteEditor, mx: Int, my: Int) {

		area(x, y, w, h) {}
		ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x90000000.toInt())
		val (px, py, pw, ph) = QuestNoteUi.popup(x, y, w, h).toList()
		MenuTheme.panel(ctx, px, py, pw, ph, palette())
		ctx.text(font, MenuTheme.fit("§6Note: §f" + e.questName, pw - 12, font::width), px + 6, py + 6, MenuTheme.WHITE)
		MenuTheme.frame(ctx, px + 6, py + 19, px + pw - 6, py + 33, MenuTheme.FILL, palette().accent)
		val shown = e.text.let { t -> var s = t; while (s.isNotEmpty() && font.width(s + "_") > pw - 18) s = s.drop(1); s }
		ctx.text(font, if (e.text.isEmpty()) "§8Type a note… (empty = clear)" else "§f${shown}_", px + 10, py + 22, MenuTheme.WHITE)
		val by = py + ph - 20
		var bx = px + 6
		for ((label, act) in listOf<Pair<String, () -> Unit>>("Save" to { finish(true) }, "Clear" to { e.clear(); finish(true) }, "Cancel" to { finish(false) })) {
			val bw = font.width(label) + 12
			button(ctx, mx, my, bx, by, bw, 14, label) { b -> if (b == 0) act() }; bx += bw + 4
		}
		ctx.text(font, "§8Enter saves · Esc cancels", bx + 4, by + 3, MenuTheme.DIM)
	}

	override fun keyPressed(input: KeyEvent): Boolean {
		val e = editor ?: return super.keyPressed(input)
		when (e.key(input.input())) {
			QuestNoteEditor.Result.SAVE -> finish(true)
			QuestNoteEditor.Result.CANCEL -> finish(false)
			QuestNoteEditor.Result.NONE -> {}
		}
		return true
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean =
		if (editor != null) true else super.mouseScrolled(mouseX, mouseY, amount)

	override fun charTyped(input: CharacterEvent): Boolean {
		editor?.type(input.codepoint())
		return true
	}
}

class ChapterView(screen: AbstractContainerScreen<*>) : TileViewBase<DynamicPage>(screen) {
	override fun palette() = ChapterSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = ChapterParser.parse(title, slots)
	override fun title(m: DynamicPage) = m.title
	override fun nav(m: DynamicPage) = m.nav
	override fun groups(m: DynamicPage) = ChapterParser.groups(m)
}
