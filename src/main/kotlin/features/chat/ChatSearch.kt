// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.chat

import io.github.notenoughupdates.moulconfig.ChromaColour
import java.util.Collections
import java.util.WeakHashMap
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.StringRepresentable
import com.mojang.blaze3d.platform.InputConstants
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.hud.HudStyle
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.bareText

object ChatSearch {
	enum class Mode(val id: String) : StringRepresentable {
		FILTER("filter"), HIGHLIGHT("highlight");

		override fun getSerializedName() = id
	}

	@Config
	object TConfig : ManagedConfig("chat-search", Category.CHAT) {
		val enabled by toggle("enabled") { true }
		val mode by choice("mode") { Mode.FILTER }
		val pinned by toggle("pinned") { false }
		val highlightColour by colour("highlight-colour") { ChromaColour.fromStaticRGB(176, 128, 0, 128) }
	}

	var query: String = ""
		private set
	private var terms: List<String> = emptyList()
	private var box: EditBox? = null
	private var matches = 0

	val active get() = TConfig.enabled && terms.isNotEmpty()

	private val FORMAT = dev.ashtonite365.apex.util.SharedRegex.SECTION
	fun normalise(s: String) = s.replace(FORMAT, "").lowercase()

	fun matches(text: String): Boolean {
		if (terms.isEmpty()) return true
		val t = normalise(text)
		return terms.all { it in t }
	}

	fun matches(msg: GuiMessage) = matches(msg.content().bareText)

	private val lineOwner: MutableMap<FormattedCharSequence, GuiMessage> = Collections.synchronizedMap(WeakHashMap())

	@JvmStatic
	fun rememberLines(message: GuiMessage, lines: List<FormattedCharSequence>) {
		for (l in lines) lineOwner[l] = message
	}

	@JvmStatic
	fun lineBackground(line: FormattedCharSequence): Int? {
		if (!active || TConfig.mode != Mode.HIGHLIGHT) return null
		val msg = lineOwner[line] ?: return null
		return if (matches(msg)) TConfig.highlightColour.getEffectiveColourRGB() else null
	}

	fun setQuery(q: String) {
		query = q
		terms = normalise(q).split(' ').filter { it.isNotBlank() }
		applyFilter()
	}

	fun applyFilter() {
		val chat = Client.hud.chat ?: return
		val tab = ChatTabs.current
		val search = active && TConfig.mode == Mode.FILTER
		if (tab == ChatTabs.Tab.ALL && !search) {
			chat.setVisibleMessageFilter { true }
			return
		}
		chat.setVisibleMessageFilter { msg: GuiMessage ->
			tab.matches(msg.content().bareText) && (!search || matches(msg))
		}
	}

	private fun countMatches(): Int {
		val chat = Client.hud.chat ?: return 0
		return (chat as dev.ashtonite365.apex.mixins.accessor.AccessorChatHud).messages_apex.count { matches(it) }
	}

	private fun showBox(screen: ChatScreen, focus: Boolean) {
		val b = box ?: return
		b.visible = true
		if (focus) {
			screen.focused = b
			b.isFocused = true
		}
	}

	@Subscribe
	fun onInit(event: ClientInitEvent) {
		ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, width, height ->
			if (screen !is ChatScreen) return@AfterInit
			if (!TConfig.enabled) {
				if (query.isNotEmpty()) setQuery("")
				return@AfterInit
			}
			val w = 150
			val b = EditBox(Client.font, width - w - 4, height - 28, w, 12, tr("apex.chat-search.box", "Search chat"))
			b.setHint(tr("apex.chat-search.hint", "Search chat (Ctrl+F)"))
			b.setMaxLength(256)
			b.value = query
			b.setResponder { setQuery(it); matches = countMatches() }
			b.visible = TConfig.pinned || query.isNotEmpty()
			box = b
			Screens.getWidgets(screen).add(b)
			matches = countMatches()

			ScreenKeyboardEvents.allowKeyPress(screen).register(ScreenKeyboardEvents.AllowKeyPress { s, key ->
				val ctrl = key.modifiers() and InputConstants.MOD_CONTROL != 0
				if (ctrl && key.key() == InputConstants.KEY_F) {
					if (b.visible && b.isFocused && !TConfig.pinned) {
						b.visible = false; setQuery(""); b.value = ""
						(s as ChatScreen).focused = null
					} else showBox(s as ChatScreen, true)
					return@AllowKeyPress false
				}
				if (b.isFocused && (key.key() == InputConstants.KEY_RETURN || key.key() == InputConstants.KEY_NUMPADENTER)) {
					b.isFocused = false
					(s as ChatScreen).focused = null
					return@AllowKeyPress false
				}
				true
			})
			ScreenEvents.afterExtract(screen).register(ScreenEvents.AfterExtract { _, ctx, _, _, _ ->
				if (!b.visible || terms.isEmpty()) return@AfterExtract
				val label = "§e$matches §7match" + (if (matches == 1) "" else "es")
				val lw = Client.font.width(label)
				val x = b.x - lw - 8
				HudStyle.drawBackground(ctx, x - 2, b.y, x + lw + 2, b.y + 12)
				ctx.text(Client.font, label, x, b.y + 2, HudStyle.textColour(), HudStyle.TConfig.textShadow)
			})
			ScreenEvents.remove(screen).register(ScreenEvents.Remove {
				box = null
				if (!TConfig.pinned && query.isNotEmpty()) setQuery("")
			})
		})
	}
}
