// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.fixes

import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object Fixes {
	val identifier: String get() = CONFIG_ID

	private const val CONFIG_ID = "fixes"

	@Config
	object TConfig : ManagedConfig(CONFIG_ID, Category.GENERAL) {
		val acceptUnsignedSkins by toggle("player-skins") { true }
		val chatPeekKey by keyBindingWithDefaultUnbound("peek-chat")
		val chatPeekScrolls by toggle("peek-chat-scroll") { false }
		val hideEffectIcons by toggle("hide-mob-effects") { false }
		val hideEffectsHud by toggle("hide-potion-effects-hud") { false }
		val suppressHurtTilt by toggle("disable-hurt-cam") { false }
		val noSlotHighlight by toggle("hide-slot-highlights") { false }
		val noRecipeBook by toggle("hide-recipe-book") { false }
		val noOffHandSlot by toggle("hide-off-hand") { false }
	}

	fun shouldPeekChat(): Boolean = TConfig.chatPeekKey.isPressed(atLeast = true)

	fun shouldScrollPeekedChat(): Boolean = TConfig.chatPeekScrolls
}
