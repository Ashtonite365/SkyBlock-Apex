package dev.ashtonite365.apex.util.mc

import me.shedaniel.math.Rectangle
import net.minecraft.client.gui.navigation.ScreenRectangle

fun Rectangle.asScreenRectangle(): ScreenRectangle = ScreenRectangle(x, y, width, height)
