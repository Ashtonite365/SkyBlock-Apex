package dev.ashtonite365.apex.features.mining

import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.bareText

object CommissionFeatures {
	@Config
	object TConfig :
		ManagedConfig("commissions", Category.MINING) {
		val highlightCompletedCommissions: Boolean by toggle("highlight-completed") { true }
	}

	private val completedBackground = Apex.identifier("completed_commission_background")

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.Before): Unit {
		val wanted = TConfig.highlightCompletedCommissions && Client.screenTitle == "Commissions"
		if (wanted && event.slot.item.view().loreAccordingToNbt.any { line -> line.bareText == "COMPLETED" })
			event.highlight(completedBackground)
	}
}
