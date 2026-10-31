// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.craft

import io.github.moulberry.repo.data.NEUCraftingRecipe
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.util.Client

data class CraftRequest(val outputId: String, val outputCount: Int, val cells: List<Pair<String, Int>?>) {
	init { require(cells.size == 9) { "a crafting layout has 9 cells" } }

	companion object {
		fun of(outputId: String, outputCount: Int, inputs: List<Pair<String, Double>>): CraftRequest {
			val c = Supercraft.cells(inputs).take(9)
			return CraftRequest(outputId, outputCount.coerceAtLeast(1), c + List(9 - c.size) { null })
		}

		fun of(recipe: NEUCraftingRecipe) = of(recipe.output.itemId, recipe.outputCount, recipe.inputs.map { it.itemId to it.amount })
	}
}

object CraftHandoff {

	const val TIMEOUT_MS = 8_000L

	const val RESEND_MS = 1_500L

	enum class Outcome { LOADED, OPENING }

	var now: () -> Long = { System.currentTimeMillis() }
	var sendCommand: (String) -> Unit = { Client.runCommand(it) }
	var craftWindowOpen: () -> Boolean = {
		(Client.screen as? AbstractContainerScreen<*>)?.let { isCraftWindow(it.title.string, it.menu.slots.size) }

			?: stillOpen(craftMenuId, Client.player?.containerMenu?.containerId)
	}

	@Volatile var craftMenuId: Int? = null

	fun stillOpen(seenId: Int?, currentId: Int?): Boolean = seenId != null && currentId != null && currentId != 0 && seenId == currentId

	private var pending: Pair<CraftRequest, Long>? = null
	private var lastCommandAt = Long.MIN_VALUE / 2

	fun isCraftWindow(title: String, slots: Int) =
		title.trim() == CraftSlotMap.TITLE && slots == CraftSlotMap.CHEST_SLOTS + 36

	fun request(r: CraftRequest): Outcome {
		val t = now()
		pending = r to t
		if (craftWindowOpen()) return Outcome.LOADED
		if (t - lastCommandAt >= RESEND_MS) { lastCommandAt = t; sendCommand("craft") }
		return Outcome.OPENING
	}

	fun take(): CraftRequest? {
		val (r, at) = pending ?: return null
		pending = null
		return r.takeIf { now() - at <= TIMEOUT_MS }
	}

	fun peek(): CraftRequest? = pending?.first

	val jeiLoaded: Boolean by lazy { runCatching { net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jei") }.getOrDefault(false) }

	fun resetForTests() { pending = null; lastCommandAt = Long.MIN_VALUE / 2 }
}
