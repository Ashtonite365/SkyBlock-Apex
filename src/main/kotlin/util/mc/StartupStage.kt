package dev.ashtonite365.apex.util.mc

enum class StartupStage {
	STARTING,
	MC_INIT,
	RENDER_INIT,
	RENDER,
	MAIN_MENU,
	;

	companion object Progress {
		var current: StartupStage = STARTING
			private set

		@JvmStatic
		fun isAtLeast(wanted: StartupStage): Boolean = current >= wanted

		@JvmStatic
		fun advanceTo(next: StartupStage) {
			val expected = entries.getOrNull(current.ordinal + 1)
			if (next != expected) throw IllegalStateException("Cannot bump initLevel $next from $current")
			current = next
		}
	}
}
