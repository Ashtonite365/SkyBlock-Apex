// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.modules

object ModuleGates {

	@JvmField @Volatile var chatPeek = true

	@JvmField @Volatile var chatSearch = true

	@JvmField @Volatile var compactChat = true

	@JvmField @Volatile var compactTabList = true

	@JvmField @Volatile var copyChat = true

	@JvmField @Volatile var customScoreboard = true

	@JvmField @Volatile var fishingHelper = true

	@JvmField @Volatile var fixes = true

	@JvmField @Volatile var hotbarStorageSelector = true

	@JvmField @Volatile var hypixelChatLayout = true

	@JvmField @Volatile var lavaReplacement = true

	@JvmField @Volatile var skillOverlay = true

	@JvmField @Volatile var statBarsVanilla = true

	@JvmField @Volatile var streamerMode = true

	val ids: List<String> = listOf("chat-peek", "chat-search", "compact-chat", "compact-tab-list", "copy-chat", "custom-scoreboard", "fishing-helper", "fixes", "hotbar-storage-selector", "hypixel-chat-layout", "lava-replacement", "skill-overlay", "stat-bars-vanilla", "streamer-mode")

	fun set(id: String, active: Boolean): Boolean {
		when (id) {
			"chat-peek" -> chatPeek = active
			"chat-search" -> chatSearch = active
			"compact-chat" -> compactChat = active
			"compact-tab-list" -> compactTabList = active
			"copy-chat" -> copyChat = active
			"custom-scoreboard" -> customScoreboard = active
			"fishing-helper" -> fishingHelper = active
			"fixes" -> fixes = active
			"hotbar-storage-selector" -> hotbarStorageSelector = active
			"hypixel-chat-layout" -> hypixelChatLayout = active
			"lava-replacement" -> lavaReplacement = active
			"skill-overlay" -> skillOverlay = active
			"stat-bars-vanilla" -> statBarsVanilla = active
			"streamer-mode" -> streamerMode = active
			else -> return false
		}
		return true
	}

	fun get(id: String): Boolean? = when (id) {
		"chat-peek" -> chatPeek
		"chat-search" -> chatSearch
		"compact-chat" -> compactChat
		"compact-tab-list" -> compactTabList
		"copy-chat" -> copyChat
		"custom-scoreboard" -> customScoreboard
		"fishing-helper" -> fishingHelper
		"fixes" -> fixes
		"hotbar-storage-selector" -> hotbarStorageSelector
		"hypixel-chat-layout" -> hypixelChatLayout
		"lava-replacement" -> lavaReplacement
		"skill-overlay" -> skillOverlay
		"stat-bars-vanilla" -> statBarsVanilla
		"streamer-mode" -> streamerMode
		else -> null
	}
}
