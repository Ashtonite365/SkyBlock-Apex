// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import java.util.Base64
import java.util.UUID
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.features.garden.PestHeads
import dev.ashtonite365.apex.features.garden.PestHeads.PestType
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.mc.applyEncodedSkin

@ExtendWith(AutoBootstrapExtension::class)
class PestHeadsTest {

	private fun texValue(hash: String, timestamp: Long = 1696945063281L, profile: String = "c7af1cd63b514cf38f85d6d5178ca8e4"): String =
		Base64.getEncoder().encodeToString(
			"""{"timestamp":$timestamp,"profileId":"$profile","profileName":"x","signatureRequired":true,"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/$hash"}}}""".toByteArray()
		)

	private fun head(value: String) = ItemStack(Items.PLAYER_HEAD).apply { applyEncodedSkin(UUID.randomUUID(), value) }

	private val realFly = "ewogICJ0aW1lc3RhbXAiIDogMTY5Njk0NTA2MzI4MSwKICAicHJvZmlsZUlkIiA6ICJjN2FmMWNkNjNiNTE0Y2YzOGY4NWQ2ZDUxNzhjYThlNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJtb25zdGVyZ2FtZXIzMTUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWQ5MGU3Nzc4MjZhNTI0NjEzNjhlMjZkMWIyZTE5YmZhMWJhNTgyZDYwMjQ4M2U1NDVmNDEyNGQwZjczMTg0MiIKICAgIH0KICB9Cn0"

	private val otherHead = texValue("538071721cc5b4cd406ce431a13f86083a8973e1064d2f8897869930ee6e5237")

	@Test fun everyPestTextureIsRecognisedWithItsType() {
		assertEquals(15, PestType.entries.size)
		assertEquals(PestType.entries.size, PestType.entries.map { it.textureHash }.toSet().size, "hashes unique")
		for (type in PestType.entries) {
			assertEquals(type, PestHeads.pestFromTexture(texValue(type.textureHash)), type.name)

			assertEquals(type, PestHeads.pestFromTexture(texValue(type.textureHash, 1L, "00000000000000000000000000000000")), type.name)
			assertEquals(type, PestHeads.pestFromTexture("http://textures.minecraft.net/texture/${type.textureHash}"))
			assertEquals(type, PestHeads.pestFromTexture(type.textureHash.uppercase()))

			assertEquals(type, PestHeads.pestFromTexture(PestHeads.headTexture(head(texValue(type.textureHash)))), type.name)
		}
		assertEquals(PestType.FLY, PestHeads.pestFromTexture(realFly))
		assertEquals(PestType.FLY, PestHeads.pestFromTexture("$realFly=="))
		assertEquals("Field Mouse", PestType.FIELD_MOUSE.displayName)
		assertEquals("Praying Mantis", PestType.PRAYING_MANTIS.displayName)
	}

	@Test fun nonPestHeadIsRejected() {
		assertNull(PestHeads.pestFromTexture(otherHead))
		assertNull(PestHeads.pestFromTexture(PestHeads.headTexture(head(otherHead))))
		assertNull(PestHeads.pestFromTexture(null))
		assertNull(PestHeads.pestFromTexture(""))
		assertNull(PestHeads.pestFromTexture("not base64 !!"))
		assertNull(PestHeads.headTexture(ItemStack(Items.PLAYER_HEAD)), "head without a profile yet")
		assertNull(PestHeads.headTexture(ItemStack(Items.DIAMOND_HELMET)))
		assertNull(PestHeads.headTexture(ItemStack.EMPTY))
		val lock = PestHeads.LockOn<Int>()
		repeat(30) { lock.update(mapOf(7 to otherHead)) }
		assertTrue(lock.pests.isEmpty())
	}

	@Test fun pestWithoutNameTagIsDetected() {

		val lock = PestHeads.LockOn<Int>()
		assertEquals(mapOf(1 to PestType.MOSQUITO), lock.update(mapOf(1 to texValue(PestType.MOSQUITO.textureHash))))
	}

	@Test fun nameTagOnlyStandIsNotDetected() {

		val lock = PestHeads.LockOn<Int>()
		repeat(PestHeads.DEFAULT_MAX_CHECKS + 10) { lock.update(mapOf(2 to null)) }
		assertTrue(lock.pests.isEmpty())

		val both = PestHeads.LockOn<Int>()
		assertEquals(mapOf(1 to PestType.FLY), both.update(mapOf(1 to realFly, 2 to null)))
	}

	@Test fun headLoadedLateIsPickedUpByRechecks() {
		val lock = PestHeads.LockOn<Int>(maxChecks = 5)
		val beetle = texValue(PestType.BEETLE.textureHash)
		repeat(3) { assertTrue(lock.update(mapOf(3 to null)).isEmpty()) }
		assertEquals(mapOf(3 to PestType.BEETLE), lock.update(mapOf(3 to beetle)))

		val asked = mutableListOf<Int>()
		val late = PestHeads.LockOn<Int>(maxChecks = 5)
		repeat(10) { late.update(listOf(4)) { asked += it; null } }
		assertEquals(5, asked.size)

		assertTrue(lock.update(emptyMap()).isEmpty())

		lock.update(mapOf(5 to beetle))
		assertTrue(lock.update(mapOf(5 to otherHead)).isEmpty())
	}
}
