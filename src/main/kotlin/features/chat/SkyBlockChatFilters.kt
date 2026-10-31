// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.chat

import dev.ashtonite365.apex.util.ChatFilters
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object SkyBlockChatFilters {
	@Config
	object TConfig : ManagedConfig("skyblock-chat-filters", Category.CHAT) {
		val hideWarping by toggle("hide-warping") { false }
		val hideLobbyJoins by toggle("hide-lobby-joins") { false }
		val hideProfileInfo by toggle("hide-profile-info") { false }
		val hideAbilityCooldown by toggle("hide-ability-cooldown") { false }
		val hideNotEnoughMana by toggle("hide-not-enough-mana") { false }
		val hideBlocksInTheWay by toggle("hide-blocks-in-the-way") { false }
		val hideGuildPartyJoinLeave by toggle("hide-friend-guild-join-leave") { false }
		val hideFireSale by toggle("hide-fire-sale") { false }
		val hideStash by toggle("hide-stash") { false }
		val hideBazaarProgress by toggle("hide-bazaar-progress") { false }

		val hideKillCombo by toggle("hide-kill-combo") { false }
		val hideGuildExp by toggle("hide-guild-exp") { false }
		val hideSlayerMessages by toggle("hide-slayer-messages") { false }
		val hideUselessDrops by toggle("hide-useless-drops") { false }
		val hideUselessNotifications by toggle("hide-useless-notifications") { false }
		val hideBazaarOrders by toggle("hide-bazaar-orders") { false }
		val hideAnnoyingSpam by toggle("hide-annoying-spam") { false }
		val hideDailyBuffs by toggle("hide-daily-buffs") { false }
		val hideEventLevel by toggle("hide-event-level") { false }
		val hideFactoryUpgrades by toggle("hide-factory-upgrades") { false }
		val hideSacrifice by toggle("hide-sacrifice") { false }
		val hidePowderChest by toggle("hide-powder-chest") { false }
		val hideWinterIsland by toggle("hide-winter-island") { false }
		val hideMysteryBox by toggle("hide-mystery-box") { false }

		val hideWatchdog by toggle("hide-watchdog") { false }
		val hideArachne by toggle("hide-arachne") { false }

		init { registerFilters() }
	}

	private fun registerFilters() {
		ChatFilters.register("sb-warping", Regex("^(Warping\\.\\.\\.|Warping you to your SkyBlock island\\.\\.\\.|Sending to server .*|Request join for .*|Evacuating to Hub\\.\\.\\.)$"), { TConfig.hideWarping })
		ChatFilters.register("sb-lobby-joins", Regex("(^ >>> .* joined the lobby! <<<$)|(^.* joined the lobby!$)|(^\\[MVP\\+\\+?] .* slid into the lobby!$)"), { TConfig.hideLobbyJoins })
		ChatFilters.register("sb-profile-info", Regex("^(You are playing on profile: .*|Profile ID: .*)$"), { TConfig.hideProfileInfo })
		ChatFilters.register("sb-ability-cooldown", Regex("^(This ability is on cooldown for \\d+s\\.|This ability is currently on cooldown.*)$"), { TConfig.hideAbilityCooldown })
		ChatFilters.register("sb-not-enough-mana", Regex("^You do not have enough mana to do this!$"), { TConfig.hideNotEnoughMana })
		ChatFilters.register("sb-blocks-in-the-way", Regex("^There are blocks in the way!$"), { TConfig.hideBlocksInTheWay })
		ChatFilters.register("sb-friend-guild-join-leave", Regex("^(Friend|Guild) > \\S+ (joined|left)\\.$"), { TConfig.hideGuildPartyJoinLeave })
		ChatFilters.register("sb-fire-sale", Regex("^(♨ .*FIRE SALE.*|♨ Selling .* for a limited time!.*)$"), { TConfig.hideFireSale })
		ChatFilters.register("sb-stash", Regex("^(You have \\d+ items? stashed away!.*|>>> CLICK HERE to pick (them|it) up! <<<|Click here to pick (them|it) up!|One or more items didn't fit in your inventory and were added to your item stash!.*|You have [\\d,]+ materials? stashed in your sacks!.*)$"), { TConfig.hideStash })
		ChatFilters.register("sb-bazaar-progress", Regex("^\\[Bazaar] (Submitting|Executing|Claiming|Cancelling) .*\\.\\.\\.$"), { TConfig.hideBazaarProgress })

		ChatFilters.register("sh-kill-combo", Regex("^(\\+\\d+ Kill Combo.*|Your Kill Combo has expired! You reached a .* Kill Combo!)$"), { TConfig.hideKillCombo })
		ChatFilters.register("sh-guild-exp", Regex("^You earned [\\d,]+ (GEXP|Event EXP)( \\+ [\\d,]+ Event EXP)? from playing SkyBlock!$"), { TConfig.hideGuildExp })
		ChatFilters.register("sh-slayer-messages", Regex("^(\\s*SLAYER QUEST STARTED!|\\s*» Slay .* Combat XP worth of .*\\.|\\s*SLAYER QUEST COMPLETE!|\\s*» Talk to Maddox to claim your .* Slayer XP!|\\s*NICE! SLAYER BOSS SLAIN!|You received kill credit for assisting on a slayer miniboss!|✆ RING\\.\\.\\. .*)$"), { TConfig.hideSlayerMessages })
		ChatFilters.register("sh-useless-drops", Regex("^(RARE DROP! (Enchanted Ender Pearl|Enchanted End Stone|Carrot|Potato|Machine Gun Bow|Earth Shard|Zombie Lord Chestplate|Crystal Fragment)\\b.*|RARE DROP! \\((\\d+x )?(Revenant Viscera|Foul Flesh|Toxic Arrow Poison|Twilight Arrow Poison)\\).*)$"), { TConfig.hideUselessDrops })
		ChatFilters.register("sh-useless-notifications", Regex("^(You tipped \\d+ players? in \\d+( different)? games?!|Your previous Plasmaflux Power Orb was removed!|You used your Mining Speed Boost Pickaxe Ability!|Your Mining Speed Boost has expired!|Mining Speed Boost is now available!|(You have just received|Since you\\'ve been away you earned) 0 coins as interest in your (personal|co-op) bank account!)$"), { TConfig.hideUselessNotifications })
		ChatFilters.register("sh-bazaar-orders", Regex("^(Buy Order Setup! |Sell Offer Setup! |Cancelled! Refunded .* from cancelling (buy order|sell offer)!)"), { TConfig.hideBazaarOrders })
		ChatFilters.register("sh-annoying-spam", Regex("^(Your (Implosion|Molten Wave|Spirit Sceptre) hit .* for .* damage\\.|Your Blessing enchant got you double drops!|GOOD CATCH! You found an? (Fish|Blessed|Dark|Light|Hot|Spooky) Bait\\.|GOOD CATCH! You found a Grand Experience Bottle\\.)$"), { TConfig.hideAnnoyingSpam })
		ChatFilters.register("sh-daily-buffs", Regex("^(New day! Your (Sky Mall|Lottery) buff changed!|You can disable this messaging by toggling (Sky Mall|Lottery) in your /hot[mf]!)$"), { TConfig.hideDailyBuffs })
		ChatFilters.register("sh-event-level", Regex("^\\s*(You are now Event Level .*!|You earned .* Event Silver!)$"), { TConfig.hideEventLevel })
		ChatFilters.register("sh-factory-upgrades", Regex("^(.* has been promoted to \\[.*\\] .*!|Your Rabbit Barn capacity has been increased to .* Rabbits!|You will now produce .* Chocolate per click!)$"), { TConfig.hideFactoryUpgrades })
		ChatFilters.register("sh-sacrifice", Regex("^(SACRIFICE! .* turned .* into .* Dragon Essence!|BONUS LOOT! They also received .* from their sacrifice!)$"), { TConfig.hideSacrifice })
		ChatFilters.register("sh-powder-chest", Regex("^(You uncovered a treasure chest!|You have successfully picked the lock on this chest!|You received 1 (Wishing Compass|Ascension Rope|Oil Barrel)\\.)$"), { TConfig.hidePowderChest })
		ChatFilters.register("sh-winter-island", Regex("^☃ .* mounted a Snow Cannon!$"), { TConfig.hideWinterIsland })
		ChatFilters.register("sh-mystery-box", Regex("^(✦ .* found an? .* in an? (Holiday )?Mystery Box!|✦ .* found a .* Mystery Box!|✦ You earned \\d+ Mystery Dust!|✦ You earned \\d+ Pet Consumables items!)$"), { TConfig.hideMysteryBox })
		ChatFilters.register(ChatFilters.Filter("sh-watchdog", { TConfig.hideWatchdog }) { watchdog.swallow(it) })
		ChatFilters.register(ChatFilters.Filter("sh-arachne", { TConfig.hideArachne && SkyBlockSession.skyblockIsland == SkyBlockIsland.SPIDER }) { arachne.quieten(it) })
	}

	internal val watchdog = AnnouncementSwallower.watchdog()
	internal val arachne = ArachneQuieter()
}
