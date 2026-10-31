// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.compat.jade

import snownee.jade.api.IWailaClientRegistration
import snownee.jade.api.IWailaCommonRegistration
import snownee.jade.api.IWailaPlugin
import snownee.jade.api.WailaPlugin
import net.minecraft.world.level.block.Block
import dev.ashtonite365.apex.Apex

@WailaPlugin
class ApexJadePlugin : IWailaPlugin {
	override fun register(registration: IWailaCommonRegistration) = Apex.logger.debug("Block info tooltip: Apex plugin found (nothing to register server side)")

	override fun registerClient(registration: IWailaClientRegistration) = with(registration) {
		val anyBlock = Block::class.java
		registerBlockComponent(CustomMiningHardnessProvider, anyBlock)
		registerBlockComponent(DrillToolProvider(), anyBlock)
		addRayTraceCallback(CustomFakeBlockProvider(this))
	}
}
