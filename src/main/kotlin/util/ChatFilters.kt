package dev.ashtonite365.apex.util

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.AllowChatEvent
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object ChatFilters {
	@Config
	object TConfig : ManagedConfig("chat-filters", Category.CHAT) {
		val enabled by toggle("enabled") { true }
	}

	data class Filter(val id: String, val isActive: () -> Boolean, val matches: (String) -> Boolean)

	private val filters = mutableMapOf<String, Filter>()

	fun register(filter: Filter) {
		require(filter.id !in filters) { "Duplicate chat filter id '${filter.id}'" }
		filters[filter.id] = filter
	}

	fun register(id: String, regex: Regex, isActive: () -> Boolean) =
		register(Filter(id, isActive) { regex.containsMatchIn(it) })

	val registered: Collection<Filter> get() = filters.values

	@Subscribe
	fun onChat(event: AllowChatEvent) {
		if (!TConfig.enabled || filters.isEmpty()) return
		val text = event.bareText
		if (filters.values.any { it.isActive() && it.matches(text) }) event.cancel()
	}
}
