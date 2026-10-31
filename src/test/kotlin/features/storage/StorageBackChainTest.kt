package dev.ashtonite365.apex.test.features.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageBackChain
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageBackChain.Item

class StorageBackChainTest {
	private fun chest(backAt: Int?, name: String = "§a← Go Back", lore: List<String> = listOf("To Storage")) =
		List(54) { i -> if (i == backAt) Item(name, lore) else if (i % 2 == 0) Item("Ender Chest Page") else null }

	@Test fun pageBackIsOneClick() {
		val clicks = mutableListOf<Int>()
		assertTrue(StorageBackChain.onBack(chest(45)) { clicks += it })
		assertEquals(listOf(45), clicks)
		assertEquals(3, StorageBackChain.findBack(List(9) { if (it == 3) Item("Back ➜", listOf("To Profile")) else Item("Glass") }))
	}

	@Test fun noGoBackSendsNothing() {
		assertFalse(StorageBackChain.onBack(chest(null)) { fail() })
	}

	@Test fun noSecondClickMachineryLeft() {
		val names = StorageBackChain::class.java.declaredMethods.map { it.name }.toSet()
		assertFalse("poll" in names || "onTick" in names || "step" in names, names.toString())
	}
}
