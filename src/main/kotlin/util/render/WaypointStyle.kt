package dev.ashtonite365.apex.util.render

import io.github.notenoughupdates.moulconfig.ChromaColour
import dev.ashtonite365.apex.gui.config.DecimalHandler
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object WaypointStyle {
	@Config
	object TConfig : ManagedConfig("waypoint-style", Category.GENERAL) {
		val showDistance by toggle("show-distance") { true }
		val showBox by toggle("show-box") { false }
		val boxColour by colour("box-colour") { ChromaColour.fromStaticRGB(0, 200, 255, 80) }
		val shape by choice("shape") { MarkerShape.CUBE_FILLED }
		val totalOpacity by integer("total-opacity", 0, 100) { 100 }
		val markerSize by option("marker-size", { 1.0 }, DecimalHandler(this, 0.25, 4.0, 0.05))
		val flatFacePlayer by toggle("flat-face-player") { true }
	}
}
