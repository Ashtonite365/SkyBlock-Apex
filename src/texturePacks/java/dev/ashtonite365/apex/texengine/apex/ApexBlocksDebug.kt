/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: `/apex dev debugcbt range <n>|all|<block>|never` outlines the areas of the current
 * island's block replacements (SPEC §2.7.4). Rough boxes are orange, areas green; a box is drawn when it is near the
 * player and the player is not inside it.
 */
package dev.ashtonite365.apex.texengine.apex

import com.mojang.brigadier.arguments.IntegerArgumentType
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.debug.DeveloperFeatures
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.tr
import net.minecraft.commands.arguments.blocks.BlockStateArgument
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object ApexBlocksDebug {

	private var everything = false

	private var showing: Block? = null
	var range = 30
		private set

	private const val ROUGH_COLOUR = 0x50FF8050
	private const val AREA_COLOUR = 0x5050FF50

	fun shows(block: Block) = everything || showing === block

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!everything && showing == null) return
		val table = ApexBlocks.currentTable ?: return
		val player = Client.player?.position() ?: Vec3.ZERO
		WorldDraw.drawInWorld(event) {
			for (entry in table.entries) {
				if (!shows(entry.block)) continue
				entry.rough?.let { outline(this, player, it, 0, ROUGH_COLOUR) }
				val areas = entry.areas ?: continue
				for (o in areas.indices step 6) outline(this, player, areas, o, AREA_COLOUR)
			}
		}
	}

	fun boxOf(b: IntArray, o: Int) =
		AABB(b[o].toDouble(), b[o + 1].toDouble(), b[o + 2].toDouble(), b[o + 3] + 1.0, b[o + 4] + 1.0, b[o + 5] + 1.0)

	fun near(box: AABB, player: Vec3) =
		box.center.distanceTo(player) < range + maxOf(box.xsize, box.ysize, box.zsize) / 2 && !box.contains(player)

	private fun outline(ctx: WorldDraw, player: Vec3, b: IntArray, o: Int, colour: Int) {
		val box = boxOf(b, o)
		if (near(box, player)) ctx.box(box, colour)
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.addSubcommand(DeveloperFeatures.DEVELOPER_SUBCOMMAND) {
			onLiteral("debugcbt") {
				onLiteral("range") {
					onArgument("range", IntegerArgumentType.integer(0)) { arg ->
						onRun {
							this@ApexBlocksDebug.range = get(arg)
							Client.showChat(tr("apex.debugcbt.range", "Only outlining block replacement areas within ${this@ApexBlocksDebug.range} blocks"))
						}
					}
				}
				onLiteral("all") {
					onRun {
						everything = true
						showing = null
						Client.showChat(tr("apex.debugcbt.always", "Showing debug outlines for all custom block textures"))
					}
				}
				onArgument("block", BlockStateArgument.block(event.commandRegistryAccess)) { arg ->
					onRun {
						val block = get(arg).state.block
						everything = false
						showing = block
						Client.showChat(tr("apex.debugcbt.block", "Showing debug outlines for all custom ${block.name} textures"))
					}
				}
				onLiteral("never") {
					onRun {
						everything = false
						showing = null
						Client.showChat(tr("apex.debugcbt.disabled", "Disabled debug outlines for custom block textures"))
					}
				}
			}
		}
	}
}
