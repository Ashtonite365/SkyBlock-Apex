package dev.ashtonite365.apex.util

import me.shedaniel.math.Color
import net.minecraft.network.chat.TextColor
import net.minecraft.world.item.DyeColor

fun DyeColor.toShedaniel(): Color = Color.ofOpaque(textColor)

fun DyeColor.toTextColor(): TextColor = TextColor.fromRgb(textColor)
