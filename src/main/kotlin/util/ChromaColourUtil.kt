package dev.ashtonite365.apex.util

import io.github.notenoughupdates.moulconfig.ChromaColour
import java.awt.Color

fun ChromaColour.getRGBAWithoutAnimation(): Color {
	val argb = ChromaColour.specialToSimpleRGB(toLegacyString())
	return Color(argb, true)
}

fun Color.toChromaWithoutAnimation(timeForFullRotationInMillis: Int = 0): ChromaColour =
	ChromaColour.fromRGB(red, green, blue, timeForFullRotationInMillis, alpha)
