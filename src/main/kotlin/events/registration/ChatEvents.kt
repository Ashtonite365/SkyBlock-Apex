// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events.registration

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import dev.ashtonite365.apex.events.ActionBarEvent
import dev.ashtonite365.apex.events.AllowChatEvent
import dev.ashtonite365.apex.events.AttackBlockEvent
import dev.ashtonite365.apex.events.JoinServerEvent
import dev.ashtonite365.apex.events.ModifyChatEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.UseBlockEvent
import dev.ashtonite365.apex.events.UseItemEvent

private object FabricBridge {

	var seenLine: Component? = null

	fun passesChatFilters(line: Component): Boolean {
		seenLine = line
		if (ProcessChatEvent.publish(ProcessChatEvent(line, false)).cancelled) return false
		return AllowChatEvent.publish(AllowChatEvent(line)).cancelled.not()
	}

	fun hiddenElsewhere(line: Component) {
		if (line === seenLine) return
		ProcessChatEvent.publish(ProcessChatEvent(line, true))
	}

	fun rewrite(line: Component, actionBar: Boolean): Component {
		if (!actionBar) return ModifyChatEvent.publish(ModifyChatEvent(line)).replaceWith
		val bar = ActionBarEvent.publish(ActionBarEvent(line))
		return if (bar.cancelled) Component.empty() else bar.text
	}

	fun verdict(cancelled: Boolean): InteractionResult = if (cancelled) InteractionResult.FAIL else InteractionResult.PASS
}

fun registerApexEvents(): Unit {
	val bridge = FabricBridge
	ClientReceiveMessageEvents.ALLOW_CHAT.register(ClientReceiveMessageEvents.AllowChat { line, _, _, _, _ -> bridge.passesChatFilters(line) })
	ClientReceiveMessageEvents.ALLOW_GAME.register(ClientReceiveMessageEvents.AllowGame { line, actionBar ->
		if (actionBar) {
			bridge.seenLine = line
			true
		} else bridge.passesChatFilters(line)
	})
	ClientReceiveMessageEvents.MODIFY_GAME.register(ClientReceiveMessageEvents.ModifyGame(bridge::rewrite))
	ClientReceiveMessageEvents.GAME_CANCELED.register(ClientReceiveMessageEvents.GameCanceled { line, actionBar ->
		if (!actionBar) bridge.hiddenElsewhere(line)
	})
	ClientReceiveMessageEvents.CHAT_CANCELED.register(ClientReceiveMessageEvents.ChatCanceled { line, _, _, _, _ -> bridge.hiddenElsewhere(line) })

	AttackBlockCallback.EVENT.register(AttackBlockCallback { player, level, hand, pos, face ->
		bridge.verdict(AttackBlockEvent.publish(AttackBlockEvent(player, level, hand, pos, face)).cancelled)
	})
	UseBlockCallback.EVENT.register(UseBlockCallback { player, level, hand, hit ->
		bridge.verdict(UseBlockEvent.publish(UseBlockEvent(player, level, hand, hit)).cancelled)
	})

	UseBlockCallback.EVENT.register(UseBlockCallback { player, level, hand, _ ->
		bridge.verdict(UseItemEvent.publish(UseItemEvent(player, level, hand)).cancelled)
	})
	UseItemCallback.EVENT.register(UseItemCallback { player, level, hand ->
		bridge.verdict(UseItemEvent.publish(UseItemEvent(player, level, hand)).cancelled)
	})
	ClientPlayConnectionEvents.JOIN.register { listener, sender, _ -> JoinServerEvent.publish(JoinServerEvent(listener, sender)) }
}
