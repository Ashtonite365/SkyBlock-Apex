package dev.ashtonite365.apex.util.mc

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.permissions.PermissionSet

private class ClientPlayerOutput(private val client: FabricClientCommandSource) : CommandSource {
	override fun sendSystemMessage(message: Component) = client.player.sendSystemMessage(message)
	override fun acceptsSuccess(): Boolean = true
	override fun acceptsFailure(): Boolean = true
	override fun shouldInformAdmins(): Boolean = true
}

fun FabricClientCommandSource.asFakeServer(): CommandSourceStack = CommandSourceStack(
	ClientPlayerOutput(this),
	position,
	rotation,
	smuggleNull<ServerLevel>(),
	PermissionSet.NO_PERMISSIONS,
	smuggleNull<MinecraftServer>(),
	player,
)

fun <T : Any> smuggleNull(): T = reinterpret<T>(null)

@Suppress("UNCHECKED_CAST")
private fun <U> reinterpret(value: Any?): U = value as U
