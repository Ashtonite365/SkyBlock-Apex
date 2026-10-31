// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.config

object HudEnabled {

	data class Rule(val any: List<String>, val parent: String?)

	val overrides: Map<String, List<String>> = mapOf(
		"chat-links:position" to listOf("image-enabled"),
		"terminal-extras:hud" to listOf("splits", "status-hud"),
		"anniversary:pig-hud" to listOf("shiny-pigs"),
		"fishing-helper:hud" to listOf("cast-timer", "hook-timer", "bait-display", "no-bait-warning", "totem-overlay"),
		"galatea:hud" to listOf("tree-progress", "sweep-display", "lasso-alert", "shard-tracker"),
		"garden-tools:visitor-list-position" to listOf("visitor-shopping-list"),
		"garden-tools:milestone-hud-position" to listOf("crop-milestones", "best-crop", "contest-time-needed", "milestone-time-left"),
		"garden-tools:visitor-stats-position" to listOf("visitor-drop-stats"),
		"garden-tools:garden-misc-position" to listOf("garden-level-display", "hoe-level-display", "crop-fever-tracker", "rare-crop-tracker"),
		"garden-tools:crop-ranking-position" to listOf("crop-money-ranking", "best-crop-time"),
		"wither-dragons:hud" to listOf("timer", "health"),
		"kuudra-info:hud" to listOf("hp-hud"),
		"dragon-tracker:hud" to listOf("dragons", "zealots"),
		"gap-misc:custom-text-hud" to listOf("nonblank:custom-text"),
		"gap-misc:players-hud" to listOf("players-in-range"),
		"gap-extras:thunder-hud" to listOf("thunder-bottle"),
		"great-spook:timer-hud" to listOf("primal-fear-timer"),
		"plus-spray:position" to listOf("display"),
		"hunting-extras:hud" to listOf("pure-reptile-counter"),
		"rift-areas:hud" to listOf("vermin-tracker"),
		"rift-extras:hud" to listOf("ubik-reminder", "sun-gecko-combo", "bacte-tentacles"),
		"rift-living-cave:hud" to listOf("defense-blocks", "living-metal", "snakes", "suit-progress"),
		"rift-motes:hud" to listOf("motes-session"),
		"rift-puzzles:crux-hud" to listOf("crux-talisman"),
		"slayer-extras:blaze-dagger-hud" to listOf("blaze-dagger-helper"),
		"stat-bars:hud" to listOf("show-health"),
		"stat-bars:mana-hud" to listOf("show-mana"),
		"stat-bars:xp-hud" to listOf("show-xp"),
		"stat-bars:defence-hud" to listOf("show-defence"),
		"stat-bars:vitality-hud" to listOf("show-vitality"),
	)

	val parents: Map<String, String> = mapOf("chat-links" to "links-enabled")

	fun candidates(prop: String): List<String> {
		val out = mutableListOf<String>()
		for (suf in listOf("-position", "-pos")) if (prop.endsWith(suf)) out += prop.removeSuffix(suf)
		var b = prop
		for (suf in listOf("-hud-position", "-hud-pos", "-position", "-pos", "-hud")) if (b.endsWith(suf)) { b = b.removeSuffix(suf); break }
		if (b in setOf("hud", "position", "gui")) b = ""
		if (b.isNotEmpty()) out += listOf(b, "$b-tracker", "$b-display", "$b-overlay", "$b-hud", "$b-timer", "$b-toggle", "$b-hud-toggle")
		out += prop
		return out.distinct()
	}

	fun rule(config: String, prop: String, toggles: Set<String>, options: Set<String> = toggles): Rule? {
		val parent = (parents[config] ?: "enabled").takeIf { it in toggles }
		overrides["$config:$prop"]?.let { o ->
			val usable = o.filter { it in toggles || (it.startsWith("nonblank:") && it.removePrefix("nonblank:") in options) }
			if (usable.isNotEmpty()) return Rule(usable, parent?.takeIf { it !in usable })
		}
		val own = candidates(prop).firstOrNull { it in toggles }
		return when {
			own != null -> Rule(listOf(own), parent?.takeIf { it != own })
			parent != null -> Rule(listOf(parent), null)
			else -> null
		}
	}

	fun evaluate(rule: Rule?, value: (String) -> Any?): Boolean {
		if (rule == null) return true
		fun on(n: String): Boolean = if (n.startsWith("nonblank:")) (value(n.removePrefix("nonblank:")) as? String)?.isNotBlank() == true else value(n) == true
		if (rule.parent != null && !on(rule.parent)) return false
		return rule.any.any(::on)
	}

	@JvmStatic
	var lookup: (String) -> Boolean = ::fromConfigs

	@JvmStatic
	fun isEnabled(key: String?): Boolean = isEnabledLogged(key)

	private val rules = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<Rule>>()

	private val owners = java.util.concurrent.ConcurrentHashMap<String, dev.ashtonite365.apex.util.data.ManagedConfig>()

	@JvmStatic
	fun register(key: String, config: dev.ashtonite365.apex.util.data.ManagedConfig) {
		owners[key] = config
		rules.remove(key)
	}

	private val warned = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

	fun fromConfigs(key: String): Boolean {
		val config = owners[key] ?: return true
		val prop = key.removePrefix("apex.config.${config.name}.")
		val opts = config.sortedOptions.associateBy { it.propertyName }
		val rule = rules.getOrPut(key) {
			java.util.Optional.ofNullable(rule(config.name, prop,
				opts.filterValues { it.handler is BooleanHandler }.keys, opts.keys))
		}.orElse(null)

		return evaluate(rule) { n -> opts[n]?.let { it._actualValue ?: it.default() } }
	}

	@JvmStatic
	fun isEnabledLogged(key: String?): Boolean {
		if (key == null) return true
		return try { lookup(key) } catch (t: Throwable) {
			if (warned.add(key)) dev.ashtonite365.apex.util.Faults.logFault("HUD editor: enabled check failed for $key", t)
			true
		}
	}
}
