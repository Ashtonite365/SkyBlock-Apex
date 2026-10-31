/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: connected textures, which blocks connect and how. Our own small rule set: one rule per block (same
 * group, optionally the same axis, a plane restriction per texture). No game client classes, so it is unit-testable.
 */
package dev.ashtonite365.apex.features.render.connected

import java.util.IdentityHashMap
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties

enum class ConnectFamily { GLASS, BLOCKS }

class ConnectRule(
	val family: ConnectFamily,
	val group: Int,
	val sameAxis: Boolean,
	val textures: Map<String, Int>,
	val pane: Boolean,
)

object ConnectRules {
	val COLOURS = listOf(
		"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
		"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
	)

	private const val ALL = ConnectTiles.PLANE_ALL
	private const val ACROSS = ConnectTiles.PLANE_ACROSS

	private class Def(val family: ConnectFamily, val blocks: List<String>, val textures: Map<String, Int>, val sameAxis: Boolean = false, val pane: Boolean = false)

	private fun tex(name: String) = "minecraft:block/$name"

	private val defs: List<Def> = buildList {

		add(Def(ConnectFamily.GLASS, listOf("glass"), mapOf(tex("glass") to ALL)))
		add(Def(ConnectFamily.GLASS, listOf("tinted_glass"), mapOf(tex("tinted_glass") to ALL)))
		for (c in COLOURS) add(Def(ConnectFamily.GLASS, listOf("${c}_stained_glass"), mapOf(tex("${c}_stained_glass") to ALL)))
		add(Def(ConnectFamily.GLASS, listOf("glass_pane"), mapOf(tex("glass") to ALL), pane = true))
		for (c in COLOURS) add(Def(ConnectFamily.GLASS, listOf("${c}_stained_glass_pane"), mapOf(tex("${c}_stained_glass") to ALL), pane = true))

		for (stage in listOf("copper_block", "exposed_copper", "weathered_copper", "oxidized_copper"))
			add(Def(ConnectFamily.BLOCKS, listOf(stage, "waxed_$stage"), mapOf(tex(stage) to ALL)))
		for (b in listOf("iron_block", "gold_block", "diamond_block", "lapis_block", "netherite_block", "smooth_stone"))
			add(Def(ConnectFamily.BLOCKS, listOf(b), mapOf(tex(b) to ALL)))
		add(Def(ConnectFamily.BLOCKS, listOf("quartz_block"), mapOf(tex("quartz_block_side") to ALL, tex("quartz_block_top") to ALL)))
		for (p in listOf("andesite", "diorite", "granite", "deepslate", "blackstone", "tuff", "cinnabar", "sulfur"))
			add(Def(ConnectFamily.BLOCKS, listOf("polished_$p"), mapOf(tex("polished_$p") to ALL)))

		add(Def(ConnectFamily.BLOCKS, listOf("cut_sandstone"), mapOf(tex("cut_sandstone") to ACROSS)))
		add(Def(ConnectFamily.BLOCKS, listOf("cut_red_sandstone"), mapOf(tex("cut_red_sandstone") to ACROSS)))

		add(Def(ConnectFamily.BLOCKS, listOf("purpur_pillar"), mapOf(tex("purpur_pillar_top") to ALL, tex("purpur_pillar_side") to ACROSS), sameAxis = true))
		add(Def(ConnectFamily.BLOCKS, listOf("bookshelf"), mapOf(tex("bookshelf") to ACROSS)))
	}

	private class Table(val byBlock: IdentityHashMap<Block, ConnectRule>, val blockIds: Map<String, ConnectRule>)

	private val table: Table by lazy {
		val byBlock = IdentityHashMap<Block, ConnectRule>()
		val ids = LinkedHashMap<String, ConnectRule>()
		defs.forEachIndexed { group, d ->
			val rule = ConnectRule(d.family, group + 1, d.sameAxis, d.textures, d.pane)
			for (b in d.blocks) {
				val id = Identifier.withDefaultNamespace(b)
				val block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null) ?: continue
				byBlock[block] = rule
				ids[id.toString()] = rule
			}
		}
		Table(byBlock, ids)
	}

	@JvmStatic
	fun of(block: Block): ConnectRule? = table.byBlock[block]

	val blockIds: Map<String, ConnectRule> get() = table.blockIds

	val textures: Map<String, Int> by lazy {
		val m = LinkedHashMap<String, Int>()
		for (r in table.byBlock.values.toSet()) for ((t, p) in r.textures) m[t] = (m[t] ?: 0) or p
		m
	}

	val styles: Map<String, TileStyle> by lazy {
		val m = LinkedHashMap<String, TileStyle>()
		for (r in table.byBlock.values.toSet()) for (t in r.textures.keys) {
			val st = if (r.family == ConnectFamily.GLASS) TileStyle.FRAME else TileStyle.SURFACE
			if (m[t] != TileStyle.FRAME) m[t] = st
		}
		m
	}

	@JvmStatic
	fun styleOf(texture: String): TileStyle = styles[texture] ?: TileStyle.FRAME

	@JvmStatic
	fun matches(rule: ConnectRule, self: BlockState, other: BlockState): Boolean {
		val o = of(other.block) ?: return false
		if (o.group != rule.group) return false
		if (rule.sameAxis) {
			val a = if (self.hasProperty(BlockStateProperties.AXIS)) self.getValue(BlockStateProperties.AXIS) else null
			val b = if (other.hasProperty(BlockStateProperties.AXIS)) other.getValue(BlockStateProperties.AXIS) else null
			if (a != b) return false
		}
		return true
	}
}
