// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.kit.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.kit.render.GyroWandRing
import dev.ashtonite365.apex.features.kit.render.KitCamera
import dev.ashtonite365.apex.features.kit.render.RenderHides

class KitRenderLogicTest {

	private val tentacleB64 = "ewogICJ0aW1lc3RhbXAiIDogMTcxOTg1NzI3NzI0OSwKICAicHJvZmlsZUlkIiA6ICIxODA1Y2E2MmM0ZDI0M2NiOWQxYmY4YmM5N2E1YjgyNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJSdWxsZWQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdkODM2NzQ5MjZiODk3MTRlNmI1YTU1NDcwNTAxYzA0YjA2NmRkODdiZjZjMzM1Y2RkYzZlNjBhMWExYTVmNSIKICAgIH0KICB9Cn0="

	@Test
	fun skinHash() {
		assertEquals(RenderHides.Logic.TENTACLE, RenderHides.Logic.skinHash(tentacleB64))
		assertNull(RenderHides.Logic.skinHash("not base64!"))
	}

	@Test
	fun hiddenHeads() {
		assertTrue(RenderHides.Logic.hiddenHead(RenderHides.Logic.HEALER_FAIRY, true, false, false, mainHand = true))
		assertFalse(RenderHides.Logic.hiddenHead(RenderHides.Logic.HEALER_FAIRY, true, false, false, mainHand = false))
		assertTrue(RenderHides.Logic.hiddenHead(RenderHides.Logic.SOUL_WEAVER, false, true, false, mainHand = false))
		assertFalse(RenderHides.Logic.hiddenHead(RenderHides.Logic.TENTACLE, true, true, false, mainHand = false))
		assertFalse(RenderHides.Logic.hiddenHead(null, true, true, true, mainHand = false))
	}

	@Test
	fun cameraDistance() {
		assertEquals(4f, KitCamera.Logic.scaledDistance(4f, false, 12))
		assertEquals(12f, KitCamera.Logic.scaledDistance(4f, true, 12))
		assertEquals(4f, KitCamera.Logic.scaledDistance(4f, true, 4))
	}

	@Test
	fun gyro() {
		assertTrue(GyroWandRing.Logic.isCast("§b1000/1000✎ Mana     -1200 Mana (Gravity Storm)"))
		assertFalse(GyroWandRing.Logic.isCast("-50 Mana (Instant Transmission)"))
		assertTrue(GyroWandRing.Logic.onCooldown(1000, 20_000))
		assertFalse(GyroWandRing.Logic.onCooldown(1000, 40_000))
		assertFalse(GyroWandRing.Logic.onCooldown(0, 10))
		val ring = GyroWandRing.Logic.ring(net.minecraft.world.phys.Vec3.ZERO)
		assertEquals(65, ring.size)
		assertEquals(10.0, ring[0].x, 1e-9)
	}
}
