// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.texengine.ItemModelIndex
import dev.ashtonite365.apex.texengine.apex.ApexBlocks
import dev.ashtonite365.apex.texengine.apex.ApexItems
import dev.ashtonite365.apex.texengine.apex.HeadModel
import dev.ashtonite365.apex.texengine.apex.ScreenItemOverrides
import dev.ashtonite365.apex.texengine.apex.WornHeadPass
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.ItemModel
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.item.MissingItemModel
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@Tag("texengine-golden")
@ExtendWith(AutoBootstrapExtension::class)
class EngineHooksTest {
	private fun id(s: String) = Identifier.parse(s)

	@AfterEach
	fun tearDown() {
		WornHeadPass.close()
		ItemModelIndex.rebuild(mapOf())
		ScreenItemOverrides.use(listOf())
	}

	private class Recorder(val name: String, val log: MutableList<String>) : ItemModel {
		override fun update(
			state: ItemStackRenderState, stack: ItemStack, resolver: ItemModelResolver, context: ItemDisplayContext,
			level: ClientLevel?, owner: ItemOwner?, seed: Int,
		) {
			log.add(name)
		}
	}

	private class Boom : ItemModel {
		override fun update(
			state: ItemStackRenderState, stack: ItemStack, resolver: ItemModelResolver, context: ItemDisplayContext,
			level: ClientLevel?, owner: ItemOwner?, seed: Int,
		) = throw IllegalStateException("boom")
	}

	private fun ItemModel.updateOnce() =
		update(ItemStackRenderState(), ItemStack.EMPTY, Fakes.alloc(ItemModelResolver::class.java), ItemDisplayContext.HEAD, null, null, 0)

	@Test
	fun `outside a pass a head-aware model draws its regular variant and nothing is recorded`() {
		val log = mutableListOf<String>()
		HeadModel.Baked(Recorder("head", log), Recorder("regular", log)).updateOnce()
		assertEquals(listOf("regular"), log)
		assertFalse(ApexItems.closeWornHeadPass())
	}

	@Test
	fun `inside a pass a head-aware model draws its head variant and the pass reports it`() {
		val log = mutableListOf<String>()
		ApexItems.openWornHeadPass()
		HeadModel.Baked(Recorder("head", log), Recorder("regular", log)).updateOnce()
		assertTrue(ApexItems.closeWornHeadPass())
		assertEquals(listOf("head"), log)

		HeadModel.Baked(Recorder("head", log), Recorder("regular", log)).updateOnce()
		assertEquals(listOf("head", "regular"), log)
	}

	@Test
	fun `a pass with only plain models reports no head variant, and each pass starts clean`() {
		val log = mutableListOf<String>()
		ApexItems.openWornHeadPass()
		HeadModel.Baked(Recorder("head", log), Recorder("regular", log)).updateOnce()
		assertTrue(ApexItems.closeWornHeadPass())
		ApexItems.openWornHeadPass()
		Recorder("plain", log).updateOnce()
		assertFalse(ApexItems.closeWornHeadPass())
	}

	@Test
	fun `a failing update inside a pass still closes it`() {
		val log = mutableListOf<String>()
		ApexItems.openWornHeadPass()
		assertThrows(IllegalStateException::class.java) {
			try {
				HeadModel.Baked(Boom(), Recorder("regular", log)).updateOnce()
			} finally {
				assertTrue(ApexItems.closeWornHeadPass())
			}
		}
		HeadModel.Baked(Recorder("head", log), Recorder("regular", log)).updateOnce()
		assertEquals(listOf("regular"), log)
	}

	@Test
	fun `a pass is per thread`() {
		ApexItems.openWornHeadPass()
		val otherThreadWantsHead = CompletableFuture.supplyAsync { WornHeadPass.wantsHeadVariant() }.get(10, TimeUnit.SECONDS)
		assertFalse(otherThreadWantsHead)
		assertFalse(ApexItems.closeWornHeadPass(), "the other thread's question must not count for this pass")
	}

	@Test
	fun `the index holds the ids with a real baked model, not missing-model entries or unknown ids`() {
		val real = Fakes.alloc(HeadModel.Baked::class.java)
		val missing = Fakes.alloc(MissingItemModel::class.java)
		ItemModelIndex.rebuild(mapOf(id("firmskyblock:real") to real, id("firmskyblock:failed") to missing))
		assertTrue(ItemModelIndex.hasItemModel_Apex(id("firmskyblock:real")))
		assertFalse(ItemModelIndex.hasItemModel_Apex(id("firmskyblock:failed")))
		assertFalse(ItemModelIndex.hasItemModel_Apex(id("firmskyblock:unknown")))
	}

	@Test
	fun `each rebuild replaces the whole index, and before any model set nothing exists`() {
		assertFalse(ItemModelIndex.hasItemModel_Apex(id("firmskyblock:real")))
		val real = Fakes.alloc(HeadModel.Baked::class.java)
		ItemModelIndex.rebuild(mapOf(id("firmskyblock:real") to real))
		assertTrue(ItemModelIndex.hasItemModel_Apex(id("firmskyblock:real")))
		ItemModelIndex.rebuild(mapOf(id("firmskyblock:other") to real))
		assertFalse(ItemModelIndex.hasItemModel_Apex(id("firmskyblock:real")))
		assertTrue(ItemModelIndex.hasItemModel_Apex(id("firmskyblock:other")))
	}

	@Test
	fun `screen item overrides start reading during the reload set-up and prepare uses that result`() {
		val items = FixturePacks.manager("items")
		val empty = FixturePacks.manager()
		assertEquals(0, ScreenItemOverrides.preparePublic(empty).size, "without the set-up start, prepare reads its own manager")
		ScreenItemOverrides.prepareSharedState(PreparableReloadListener.SharedState(items))
		val prepared = ScreenItemOverrides.preparePublic(empty)
		assertEquals(ScreenItemOverrides.load(items).size, prepared.size)
		assertTrue(prepared.isNotEmpty())

		assertEquals(0, ScreenItemOverrides.preparePublic(empty).size)
	}

	@Test
	fun `block replacements start parsing during the reload set-up`() {
		val before = ApexBlocks.reload
		try {
			ApexBlocks.Listener.prepareSharedState(PreparableReloadListener.SharedState(FixturePacks.manager("blocks")))
			assertNotSame(before, ApexBlocks.reload)
			val parsed = ApexBlocks.reload.parsed.get(10, TimeUnit.SECONDS)
			assertTrue(parsed.entries.isNotEmpty())
		} finally {
			ApexBlocks.reload = ApexBlocks.Reload(CompletableFuture.completedFuture(ApexBlocks.Parsed.EMPTY))
				.also { it.baked.complete(ApexBlocks.Baked(mapOf(), mapOf())) }
		}
	}
}
