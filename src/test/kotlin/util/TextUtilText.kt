package dev.ashtonite365.apex.test.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.test.testutil.ItemResources
import dev.ashtonite365.apex.util.getLegacyFormatString

class TextUtilText {

	private fun nestedGreeting(): Component {
		val greeting = Component.literal("Hi ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
		greeting.append(Component.literal("there").withStyle { it.withItalic(true) })
		return Component.literal("")
			.withStyle { it.withItalic(false) }
			.append(greeting)
			.append(Component.literal("!"))
	}

	@Test
	fun `recorded chat message keeps every piece and reset`() {
		val message = ItemResources.loadText("all-chat")
		assertEquals(
			"§r§r§8[§r§9302§r§8] §r§6♫ §r§b[MVP§r§d+§r§b] lrg89§r§f: test§r",
			message.getLegacyFormatString()
		)
	}

	@Test
	fun `untrimmed output resets after every piece`() {
		assertEquals("§r§r§6§lHi §r§6§l§othere§r§r!§r", nestedGreeting().getLegacyFormatString())
	}

	@Test
	fun `trimmed output writes inherited prefixes only on change`() {
		assertEquals("§6§lHi §6§l§othere§r!", nestedGreeting().getLegacyFormatString(trimmed = true))
	}

	@Test
	fun `trimmed output skips the initial reset`() {
		val text = Component.literal("plain").append(Component.literal("red").withStyle(ChatFormatting.RED))
		assertEquals("plain§cred", text.getLegacyFormatString(trimmed = true))
	}

	@Test
	fun `obfuscated child inherits the parent colour`() {
		val text = Component.literal("")
			.append(Component.literal("tag ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal("?").withStyle { it.withObfuscated(true) }))
		assertEquals("§btag §b§k?", text.getLegacyFormatString(trimmed = true))
	}
}
