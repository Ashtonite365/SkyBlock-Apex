package dev.ashtonite365.apex.features.items.recipes

object ViewerStartup {

	const val SUPPRESS_RECIPE_SYNC_NOTICE = true

	const val START_DELAY_TICKS = 20

	fun shouldStart(
		running: Boolean,
		inWorld: Boolean,
		reloading: Boolean,
		containerOpen: Boolean,
		ticksInLevel: Int,
		attempted: Boolean,
	): Boolean = !running && inWorld && !reloading && !containerOpen && !attempted && ticksInLevel >= START_DELAY_TICKS
}
