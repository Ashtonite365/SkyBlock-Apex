/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Dungeons-category options for room-clear alerts, the mage beam trail, Wither/Blood key highlight
 * and the nearest-door highlight/path.
 */
package dev.ashtonite365.apex.features.kit.dungeon

import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import io.github.notenoughupdates.moulconfig.ChromaColour
import net.minecraft.util.StringRepresentable

object DungeonExtras {

	enum class ClearMode(val id: String) : StringRepresentable {
		BOTH("both"),
		GREEN("green"),
		WHITE("white"),
		;

		override fun getSerializedName() = id
	}

	@Config
	object TConfig : ManagedConfig("dungeon-extras", Category.DUNGEONS) {

		val roomClear by toggle("room-clear") { false }
		val roomClearMode by choice("room-clear-mode") { ClearMode.BOTH }

		val mageBeam by toggle("mage-beam") { false }
		val mageBeamDuration by integer("mage-beam-duration", 1, 100) { 40 }
		val mageBeamColour by colour("mage-beam-colour") { ChromaColour.fromStaticRGB(170, 0, 0, 255) }
		val mageBeamHideParticles by toggle("mage-beam-hide-particles") { true }
		val mageBeamMinPoints by integer("mage-beam-min-points", 2, 10) { 3 }

		val keyHighlight by riskyToggle("key-highlight", RiskLevel.GREY_AREA)
		val keySpawnAlert by toggle("key-spawn-alert") { false }
		val witherKeyColour by colour("wither-key-colour") { ChromaColour.fromStaticRGB(32, 32, 32, 204) }
		val bloodKeyColour by colour("blood-key-colour") { ChromaColour.fromStaticRGB(255, 85, 85, 204) }

		val nearestDoorHighlight by riskyToggle("nearest-door-highlight", RiskLevel.RISKY)
		val nearestDoorPath by riskyToggle("nearest-door-path", RiskLevel.RISKY)
		val witherDoorColour by colour("wither-door-colour") { ChromaColour.fromStaticRGB(32, 32, 32, 128) }
		val bloodDoorColour by colour("blood-door-colour") { ChromaColour.fromStaticRGB(170, 0, 0, 128) }
		val doorPathColour by colour("door-path-colour") { ChromaColour.fromStaticRGB(85, 255, 255, 255) }
	}
}
