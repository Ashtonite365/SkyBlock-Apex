// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.gametest

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.junit.jupiter.api.Assertions
import org.spongepowered.asm.mixin.MixinEnvironment
import dev.ashtonite365.apex.init.MixinPlugin
import dev.ashtonite365.apex.test.ApexTestBootstrap

class GameTest : FabricClientGameTest {
	override fun runTest(ctx: ClientGameTestContext) {
		ApexTestBootstrap.bootstrapMinecraft()
		MixinEnvironment.getCurrentEnvironment().audit()
		val plugin = MixinPlugin.instances.single()
		Assertions.assertTrue(plugin.mixins.isNotEmpty(), "the mixin plugin saw no mixins")
		Assertions.assertEquals(plugin.expectedFullPathMixins, plugin.appliedFullPathMixins, "expected vs applied mixins")
	}
}
