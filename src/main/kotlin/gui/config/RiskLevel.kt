package dev.ashtonite365.apex.gui.config

enum class RiskLevel {
	NONE,

	GREY_AREA,

	RISKY,
	;

	val isRisky get() = this != NONE
}
