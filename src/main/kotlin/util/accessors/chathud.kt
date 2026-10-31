// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.accessors

import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessage
import dev.ashtonite365.apex.mixins.accessor.AccessorChatHud

val ChatComponent.messages: MutableList<GuiMessage> get() = AccessorChatHud::class.java.cast(this).messages_apex
