package dev.ashtonite365.apex.features.inventory

import com.mojang.blaze3d.platform.InputConstants
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.skyblock.SBItemUtil.getSearchName

object JunkHighlighter {
	@Config
	object TConfig : ManagedConfig("junk-highlighter", Category.INVENTORY) {
		val pattern by string("regex") { "" }
		val revealKey by keyBinding("highlight") { InputConstants.KEY_LCONTROL }
	}

	private const val MARK = 0xFFFF0000.toInt()

	private var cache: Pair<String, Regex?> = "" to null

	private fun compiled(text: String): Regex? {
		if (cache.first != text) cache = text to runCatching { Regex(text) }.getOrNull()
		return cache.second
	}

	fun isJunk(name: String, pattern: String): Boolean =
		pattern.isNotEmpty() && compiled(pattern)?.matches(name) == true

	@OptIn(NeedsComponents::class)
	@Subscribe
	fun markJunk(event: SlotRenderEvents.After) {
		if (!TConfig.revealKey.isPressed()) return
		event.slot.takeIf { isJunk(it.item.view().getSearchName(), TConfig.pattern) }
			?.let { event.context.fill(it.x, it.y, it.x + 16, it.y + 16, MARK) }
	}
}
