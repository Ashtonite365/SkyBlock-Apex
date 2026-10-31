package dev.ashtonite365.apex.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import net.minecraft.client.Minecraft

val MinecraftDispatcher: CoroutineDispatcher by lazy { Minecraft.getInstance().asCoroutineDispatcher() }
