package dev.ashtonite365.apex.test.features.menus.m2

import com.mojang.blaze3d.platform.InputConstants
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.quests.QuestNoteEditor
import dev.ashtonite365.apex.features.menus.quests.QuestNoteUi
import dev.ashtonite365.apex.features.menus.quests.QuestNotes
import dev.ashtonite365.apex.features.menus.quests.QuestParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class QuestNotesTest {
	@Test fun keyStripsFormatting() {
		assertEquals("Gearing Up", QuestNotes.key("§a§lGearing Up  "))
		assertEquals("David's Hunting Lessons", QuestNotes.key(" §eDavid's Hunting Lessons§r"))
		assertEquals(QuestNotes.key("Gearing Up"), QuestNotes.key("§6Gearing Up"))
	}

	@Test fun sameNameSameNoteAndClear() {
		val s = QuestNotes.Store()
		assertTrue(QuestNotes.set(s, "§aGearing Up", "  buy hoe first "))
		assertEquals("buy hoe first", QuestNotes.get(s, "Gearing Up"))
		assertEquals("buy hoe first", QuestNotes.get(s, "§l§6Gearing Up "))
		assertEquals(1, s.notes.size)
		assertFalse(QuestNotes.set(s, "Gearing Up", "buy hoe first"))
		assertTrue(QuestNotes.set(s, "Gearing Up", "edited"))
		assertEquals("edited", QuestNotes.get(s, "§aGearing Up")); assertEquals(1, s.notes.size)
		assertNull(QuestNotes.get(s, "Finding the Engine"))

		assertTrue(QuestNotes.set(s, "Gearing Up", "   "))
		assertNull(QuestNotes.get(s, "Gearing Up")); assertTrue(s.notes.isEmpty())
		assertFalse(QuestNotes.set(s, "Gearing Up", ""))

		QuestNotes.set(s, "X", "§cred\nline" + "y".repeat(500))
		assertEquals(QuestNotes.MAX_LENGTH, QuestNotes.get(s, "X")!!.length)
		assertFalse(QuestNotes.get(s, "X")!!.contains('§') || QuestNotes.get(s, "X")!!.contains('\n'))
		assertFalse(QuestNotes.set(s, "§a  ", "no name"))
	}

	@Test fun persistenceRoundTrip() {
		val d = QuestNotes.Data
		d.explicitDefaultLoad()
		QuestNotes.set(d.data, "§aGearing Up", "level garden")
		QuestNotes.set(d.data, "Finding the Engine", "fish at the hut")
		val json = d.saveTo(Unit)
		d.clear()
		d.loadFrom(Unit, json)
		assertEquals("level garden", QuestNotes.get(d.data, "Gearing Up"))
		assertEquals("fish at the hut", QuestNotes.get(d.data, "§bFinding the Engine"))
		assertEquals(2, d.data.notes.size)

		d.loadFrom(Unit, kotlinx.serialization.json.JsonObject(emptyMap()))
		assertTrue(d.data.notes.isEmpty())
		d.explicitDefaultLoad()
	}

	@Test fun editorKeys() {
		val e = QuestNoteEditor("Gearing Up", "ab")
		"c §d".forEach { e.type(it.code) }
		assertEquals("abc d", e.text)
		e.type(10); e.type(0x7F); assertEquals("abc d", e.text)
		assertEquals(QuestNoteEditor.Result.NONE, e.key(InputConstants.KEY_BACKSPACE)); assertEquals("abc ", e.text)

		for (k in listOf(InputConstants.KEY_Q, InputConstants.KEY_1, InputConstants.KEY_9, InputConstants.KEY_E, InputConstants.KEY_TAB))
			assertEquals(QuestNoteEditor.Result.NONE, e.key(k))
		assertEquals("abc ", e.text)
		assertEquals(QuestNoteEditor.Result.SAVE, e.key(InputConstants.KEY_RETURN))
		assertEquals(QuestNoteEditor.Result.SAVE, e.key(InputConstants.KEY_NUMPADENTER))
		assertEquals(QuestNoteEditor.Result.CANCEL, e.key(InputConstants.KEY_ESCAPE))
		val long = QuestNoteEditor("X", "")
		repeat(QuestNotes.MAX_LENGTH + 20) { long.type('a'.code) }
		assertEquals(QuestNotes.MAX_LENGTH, long.text.length)
		long.clear(); assertEquals("", long.text)

		val s = QuestNotes.Store(); QuestNotes.set(s, "X", "old")
		QuestNotes.set(s, long.questName, long.text); assertNull(QuestNotes.get(s, "X"))
	}

	@Test fun clickToPopupMapping() {
		val tx = 50; val ty = 80; val tw = 100
		val r = QuestNoteUi.icon(tx, ty, tw)
		assertTrue(r[0] + r[2] <= tx + tw && r[1] >= ty && r[1] + r[3] <= ty + 34)
		val ix = r[0] + 2; val iy = r[1] + 2
		assertEquals(QuestNoteUi.Action.OPEN_POPUP, QuestNoteUi.click(ix, iy, tx, ty, tw, 0))
		assertEquals(QuestNoteUi.Action.NOTHING, QuestNoteUi.click(ix, iy, tx, ty, tw, 1))

		assertEquals(QuestNoteUi.Action.FORWARD, QuestNoteUi.click(tx + 10, ty + 20, tx, ty, tw, 0))
		assertEquals(QuestNoteUi.Action.FORWARD, QuestNoteUi.click(tx + 10, ty + 20, tx, ty, tw, 1))
		assertEquals(QuestNoteUi.Action.FORWARD, QuestNoteUi.click(r[0] - 1, iy, tx, ty, tw, 0))

		val d = RealDumpFixtures.load("quest-log")
		val groups = QuestParser.groups(QuestParser.parse(d.title, d.slots)!!)
		val withIcon = groups.filter { QuestNoteUi.hasIcon(it.name) }.flatMap { it.tiles }.map { it.label }
		assertTrue("Gearing Up" in withIcon && "Find all Fairy Souls" in withIcon)
		assertTrue("Completed Quests" !in withIcon && "Dungeon Journals" !in withIcon)
		assertEquals(8, withIcon.size)

		val p = QuestNoteUi.popup(10, 10, 480, 260)
		assertTrue(p[0] >= 10 && p[1] >= 10 && p[0] + p[2] <= 490 && p[1] + p[3] <= 270)
	}

	@Test fun tooltipShowsNoteAlongsideNormalInfo() {
		val normal = listOf("Gearing Up", "§70/1 steps")
		assertEquals(normal, QuestNoteUi.tooltip(normal, null) { listOf(it) })
		val t = QuestNoteUi.tooltip(normal, "level garden") { listOf(it) }
		assertEquals(normal, t.take(2))
		assertTrue(t.contains("§flevel garden"))
	}
}
