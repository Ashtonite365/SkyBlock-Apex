/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: F5/M5 Livid finder rules (pure, no Minecraft types, unit-tested in LividLogicTest).
 * LividSolver.kt reads the indicator blocks and entity snapshots each re-check and renders the result.
 *
 * Reference positions and colour table checked against established mods (no code copied; see CREDITS.md):
 * - wool at (5, 108, 43), player named "<Name> Livid";
 * - wool at (6, 109, 43), armour-stand tag "§<c>﴾ §<c>§lLivid ...", starts at red (Hockey) when the boss room is entered;
 * - pre-rework 1.8 arena: stained glass at (13, 107, 25), wait for blindness to end before reading, box the area under
 *    the matching tag.
 */
package dev.ashtonite365.apex.features.dungeon

import kotlin.math.abs

object LividLogic {

	enum class Livid(val displayName: String, val dye: String, val chatCode: Char, val rgb: Int) {
		VENDETTA("Vendetta", "white", 'f', 0xFFFFFF),
		CROSSED("Crossed", "magenta", 'd', 0xFF55FF),
		ARCADE("Arcade", "yellow", 'e', 0xFFFF55),
		SMILE("Smile", "lime", 'a', 0x55FF55),
		DOCTOR("Doctor", "gray", '7', 0xAAAAAA),
		PURPLE("Purple", "purple", '5', 0xAA00AA),
		SCREAM("Scream", "blue", '9', 0x5555FF),
		FROG("Frog", "green", '2', 0x00AA00),
		HOCKEY("Hockey", "red", 'c', 0xFF5555);

		val entityName get() = "$displayName Livid"
	}

	data class Pos(val x: Int, val y: Int, val z: Int) {
		override fun toString() = "($x,$y,$z)"
	}

	val KIT_POS = Pos(5, 108, 43)

	val PLUS_POS = Pos(6, 109, 43)

	val SBTOOLS_LEGACY_POS = Pos(13, 107, 25)

	val OLD_WRONG_POS = Pos(5, 108, 42)

	fun indicatorPositions(legacyGlass: Boolean): List<Pos> =
		if (legacyGlass) listOf(KIT_POS, PLUS_POS, SBTOOLS_LEGACY_POS) else listOf(KIT_POS, PLUS_POS)

	private val aliasDyes = mapOf("pink" to Livid.CROSSED, "light_gray" to Livid.DOCTOR)

	fun fromBlockId(id: String?): Livid? {
		if (id == null) return null
		val path = id.substringAfter(':')
		val dye = when {
			path.endsWith("_stained_glass") -> path.removeSuffix("_stained_glass")
			path.endsWith("_wool") -> path.removeSuffix("_wool")
			else -> return null
		}
		return Livid.entries.firstOrNull { it.dye == dye } ?: aliasDyes[dye]
	}

	fun pickIndicator(reads: List<Pair<Pos, String?>>): Pair<Pos, Livid>? {
		for ((pos, id) in reads) fromBlockId(id)?.let { return pos to it }
		return null
	}

	private val strip = dev.ashtonite365.apex.util.SharedRegex.SECTION

	fun fromEntityName(name: String): Livid? {
		val plain = name.replace(strip, "").trim()
		return Livid.entries.firstOrNull { it.entityName == plain }
	}

	fun isLividEntityName(name: String) = fromEntityName(name) != null

	fun nametagColour(legacy: String): Char? {
		val plain = legacy.replace(strip, "")
		if (!plain.contains("Livid") || fromEntityName(plain) != null) return null
		val end = legacy.indexOf('﴾').takeIf { it >= 0 } ?: legacy.indexOf("Livid")
		var code: Char? = null
		var i = 0
		while (i < end - 1) {
			if (legacy[i] == '§') {
				val c = legacy[i + 1].lowercaseChar()
				if (c in '0'..'9' || c in 'a'..'f') code = c
				i += 2
			} else i++
		}
		return code
	}

	fun fromNametag(legacy: String): Livid? = nametagColour(legacy)?.let { c -> Livid.entries.firstOrNull { it.chatCode == c } }

	data class Body(val id: Int, val name: String, val x: Double, val y: Double, val z: Double)
	data class Tag(val id: Int, val legacyName: String, val x: Double, val y: Double, val z: Double)

	enum class Via { PLAYER_NAME, NAMETAG }

	data class Match(val livid: Livid, val via: Via, val bodyId: Int?, val tagId: Int?)

	fun isUnder(tag: Tag, body: Body) =
		abs(body.x - tag.x) <= 1.0 && abs(body.z - tag.z) <= 1.0 && body.y >= tag.y - 3.5 && body.y <= tag.y + 0.5

	fun match(target: Livid, bodies: List<Body>, tags: List<Tag>): Match? {
		bodies.firstOrNull { fromEntityName(it.name) == target }?.let { return Match(target, Via.PLAYER_NAME, it.id, null) }
		val tag = tags.firstOrNull { fromNametag(it.legacyName) == target } ?: return null
		val body = bodies.filter { isLividEntityName(it.name) && isUnder(tag, it) }
			.minByOrNull { (it.x - tag.x) * (it.x - tag.x) + (it.z - tag.z) * (it.z - tag.z) }
		return Match(target, Via.NAMETAG, body?.id, tag.id)
	}

	fun shouldDraw(blind: Boolean, showWhileBlind: Boolean) = !blind || showWhileBlind

	const val RECHECK_TICKS = 10
}
