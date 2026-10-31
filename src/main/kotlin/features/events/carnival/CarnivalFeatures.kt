package dev.ashtonite365.apex.features.events.carnival

import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object CarnivalFeatures {
	val identifier: String get() = "carnival"

	@Config
	object TConfig :
		ManagedConfig(category = Category.EVENTS, name = identifier) {

		val enableBombSolver: Boolean by riskyToggle("bombs-solver", RiskLevel.GREY_AREA)

		val displayTutorials: Boolean by toggle("tutorials") { true }
	}
}
