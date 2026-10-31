// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test

import net.minecraft.server.Bootstrap
import net.minecraft.SharedConstants
import net.minecraft.core.registries.BuiltInRegistries
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.TimeMark

object ApexTestBootstrap {
	val loadStart: TimeMark = TimeMark.now().also { println("Bootstrap started at $it") }

	val loadEnd: TimeMark = bootVanilla()

	val loadDuration = loadStart.passedAt(loadEnd).also { println("Bootstrap completed at $loadEnd after $it") }

	private fun bootVanilla(): TimeMark {
		SharedConstants.tryDetectVersion().also { Bootstrap.bootStrap() }
		for (initializer in BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(Client.registries)) initializer.apply()
		return TimeMark.now()
	}

	@JvmStatic
	fun bootstrapMinecraft() = Unit
}
