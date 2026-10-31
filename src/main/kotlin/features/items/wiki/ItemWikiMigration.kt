// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.wiki

object ItemWikiMigration {

	fun migrate(data: com.google.gson.JsonObject) {
		val old = data.getAsJsonObject("auto-completions") ?: return
		val target = data.getAsJsonObject("item-wiki") ?: com.google.gson.JsonObject()
		old.remove("wiki-key")?.let { if (!target.has("key")) target.add("key", it) }
		old.remove("wiki-hold-tenths")?.let { v ->
			if (!target.has("hold-tenths")) {
				val n = runCatching { v.asInt }.getOrNull()
				if (n != null && n != 10) target.addProperty("hold-tenths", n)
			}
		}
		for (k in listOf("wiki-viewer-x", "wiki-viewer-y", "wiki-viewer-w", "wiki-viewer-h")) old.remove(k)
		if (target.size() > 0) data.add("item-wiki", target)
	}
}
