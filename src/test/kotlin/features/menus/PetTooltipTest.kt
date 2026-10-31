package dev.ashtonite365.apex.test.features.menus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import dev.ashtonite365.apex.features.menus.pets.PetTooltip

@ExtendWith(AutoBootstrapExtension::class)
class PetTooltipTest {
	@Test fun liveSlotStackIsTheTooltipSource() {
		val rabbit = ItemStack(Items.PLAYER_HEAD).apply {
			set(DataComponents.CUSTOM_NAME, Component.literal("§7[Lvl 47] §6Rabbit"))
			set(DataComponents.LORE, ItemLore(listOf(Component.literal("§8Farming Pet"), Component.literal("§7Held Item: §aCrochet Tiger Plushie"))))
		}
		val slots = mapOf(10 to rabbit)
		val r = PetTooltip.resolve(10) { slots[it] }
		assertTrue(r is PetTooltip.Resolved.Show && r.item === rabbit)
		assertTrue(PetTooltip.resolve(11) { slots[it] } is PetTooltip.Resolved.Missing)
		assertEquals("§8Farming Pet", rabbit.get(DataComponents.LORE)!!.lines()[0].string)
	}
}
