/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: the texture engine every hook calls. There is one engine (Apex's own); the adapter stays so hooks and
 * tests depend on the [TextureEngine] interfaces only.
 */
package dev.ashtonite365.apex.texengine

import dev.ashtonite365.apex.texengine.apex.ApexEngine
import dev.ashtonite365.apex.texengine.apex.Settings
import dev.ashtonite365.apex.texengine.apex.TextureConfig

object ApexTextureEngines {

	@JvmStatic
	val active: TextureEngine = ApexEngine.also { Settings.source = TextureConfig }
}

object BlockHooks {

	@JvmStatic
	fun beforeRendererGet(state: net.minecraft.world.level.block.state.BlockState): net.minecraft.world.level.block.state.BlockState {
		ApexTextureEngines.active.blocks.enterFallback()
		return state
	}

	@JvmStatic
	fun afterRendererGet(
		chosen: net.minecraft.client.renderer.block.dispatch.BlockStateModel,
		state: net.minecraft.world.level.block.state.BlockState,
		pos: net.minecraft.core.BlockPos?,
	): net.minecraft.client.renderer.block.dispatch.BlockStateModel {
		val blocks = ApexTextureEngines.active.blocks
		blocks.exitFallback()
		return blocks.modelFor(state, pos) ?: chosen
	}

	@JvmStatic
	fun positionless(
		chosen: net.minecraft.client.renderer.block.dispatch.BlockStateModel,
		state: net.minecraft.world.level.block.state.BlockState,
	): net.minecraft.client.renderer.block.dispatch.BlockStateModel =
		ApexTextureEngines.active.blocks.modelFor(state, null) ?: chosen
}
