// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.wiki

import java.net.URI
import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object WikiHosts {

	const val HOST = "hypixelskyblock.minecraft.wiki"
	val HOSTS = setOf(HOST)

	fun userAgent(version: String) = "SkyBlockApex/$version (Minecraft mod; in-game Item Wiki viewer; read-only)"

	fun allowed(url: String): Boolean = runCatching {
		val u = URI(url)
		(u.scheme == "https" || u.scheme == "http") && u.host?.lowercase() in HOSTS
	}.getOrDefault(false)

	fun titleOf(url: String): String? = WikiTitles.ofUrl(url)?.first

	private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)

	fun apiUrl(title: String): String =
		"https://$HOST/api.php?action=parse&format=json&formatversion=2&redirects=1&prop=text%7Csections%7Cdisplaytitle&disableeditsection=1&disabletoc=1&page=" +
			enc(title.replace(' ', '_'))

	fun searchUrl(query: String): String =
		"https://$HOST/api.php?action=opensearch&format=json&formatversion=2&namespace=0&limit=5&redirects=resolve&search=" + enc(query)

	fun pageUrl(title: String, anchor: String? = null): String =
		"https://$HOST/w/" + enc(title.replace(' ', '_')).replace("+", "_").replace("%2F", "/").replace("%3A", ":") +
			(anchor?.let { "#" + enc(it.replace(' ', '_')).replace("+", "_") } ?: "")
}

object WikiLinks {
	sealed interface Kind {
		data class Internal(val title: String, val anchor: String? = null) : Kind
		data class External(val url: String?) : Kind
		data class Anchor(val anchor: String) : Kind
	}
	private val skipNs = listOf("File:", "Image:", "Special:", "Category:", "Template:", "Help:", "User:", "Talk:", "Media:", "Module:")

	fun classify(href: String?): Kind {
		val h = href?.trim().orEmpty()
		if (h.isEmpty()) return Kind.External(null)
		if (h.startsWith("#")) return Kind.Anchor(WikiTitles.percentDecode(h.drop(1)).replace('_', ' '))
		val path: String = when {
			h.startsWith("/w/") -> h.removePrefix("/w/")
			h.startsWith("./") -> h.removePrefix("./")
			h.startsWith("/index.php?title=") -> if ("redlink=1" in h || "action=edit" in h) return Kind.External(null) else h.removePrefix("/index.php?title=").substringBefore('&')
			h.startsWith("http://") || h.startsWith("https://") || h.startsWith("//") -> {
				val full = if (h.startsWith("//")) "https:$h" else h
				val u = runCatching { URI(full) }.getOrNull() ?: return Kind.External(null)
				if (u.host?.lowercase() !in WikiHosts.HOSTS || u.rawPath?.startsWith("/w/") != true) return Kind.External(full)
				u.rawPath.removePrefix("/w/") + (u.rawFragment?.let { "#$it" } ?: "")
			}
			else -> return Kind.External(null)
		}
		val title = WikiTitles.normalise(WikiTitles.percentDecode(path.substringBefore('#')))
		val anchor = path.substringAfter('#', "").takeIf { it.isNotEmpty() }?.let { WikiTitles.percentDecode(it).replace('_', ' ') }
		if (title.isEmpty() || skipNs.any { title.startsWith(it, true) }) return Kind.External(null)
		return Kind.Internal(title, anchor)
	}
}

class WikiHistory {
	data class Entry(val title: String, val anchor: String? = null, var scroll: Int = 0)
	private val back = ArrayDeque<Entry>()
	var current: Entry? = null
		private set
	val canGoBack get() = back.isNotEmpty()
	val depth get() = back.size

	fun open(title: String, anchor: String? = null) {
		val c = current
		if (c != null && WikiTitles.normalise(c.title) == WikiTitles.normalise(title) && anchor == null) return
		c?.let { back.addLast(it) }
		while (back.size > 50) back.removeFirst()
		current = Entry(title, anchor)
	}

	fun retitle(title: String) { current = current?.copy(title = title) }

	fun goBack(): Entry? {
		val t = back.removeLastOrNull() ?: return null
		current = t
		return t
	}
}

data class PanelRect(val x: Int, val y: Int, val w: Int, val h: Int) {
	companion object {
		const val MIN_W = 180
		const val MIN_H = 120

		fun centred(sw: Int, sh: Int, w: Int = (sw * 0.62).toInt().coerceIn(MIN_W, 420), h: Int = (sh * 0.8).toInt().coerceIn(MIN_H, 320)) =
			PanelRect((sw - w) / 2, (sh - h) / 2, w, h).clamp(sw, sh)
	}

	fun clamp(sw: Int, sh: Int): PanelRect {
		val cw = w.coerceIn(minOf(MIN_W, sw), sw.coerceAtLeast(1))
		val ch = h.coerceIn(minOf(MIN_H, sh), sh.coerceAtLeast(1))
		return PanelRect(x.coerceIn(0, (sw - cw).coerceAtLeast(0)), y.coerceIn(0, (sh - ch).coerceAtLeast(0)), cw, ch)
	}

	fun drag(dx: Int, dy: Int, sw: Int, sh: Int) = copy(x = x + dx, y = y + dy).clamp(sw, sh)

	fun resize(left: Boolean, right: Boolean, top: Boolean, bottom: Boolean, dx: Int, dy: Int, sw: Int, sh: Int): PanelRect {
		var x0 = x; var y0 = y; var x1 = x + w; var y1 = y + h
		if (left) x0 = (x0 + dx).coerceIn(0, x1 - MIN_W)
		if (right) x1 = (x1 + dx).coerceIn(x0 + MIN_W, sw)
		if (top) y0 = (y0 + dy).coerceIn(0, y1 - MIN_H)
		if (bottom) y1 = (y1 + dy).coerceIn(y0 + MIN_H, sh)
		return PanelRect(x0, y0, x1 - x0, y1 - y0).clamp(sw, sh)
	}

	data class Edges(val left: Boolean, val right: Boolean, val top: Boolean, val bottom: Boolean) {
		val any get() = left || right || top || bottom
	}

	fun edgesAt(mx: Double, my: Double, grip: Int = 4): Edges {
		val inX = mx >= x - grip && mx <= x + w + grip; val inY = my >= y - grip && my <= y + h + grip
		if (!inX || !inY) return Edges(false, false, false, false)
		return Edges(mx <= x + grip, mx >= x + w - grip, my <= y + grip, my >= y + h - grip)
	}

	fun contains(mx: Double, my: Double) = mx >= x && mx < x + w && my >= y && my < y + h
}

class WikiScroll(var contentH: Int = 0, var viewH: Int = 0) {
	var offset = 0
		private set
	val max get() = (contentH - viewH).coerceAtLeast(0)

	fun scrollTo(y: Int) { offset = y.coerceIn(0, max) }
	fun scrollBy(dy: Int) = scrollTo(offset + dy)
	fun wheel(amount: Double, lineH: Int) = scrollBy((-amount * lineH * 3).toInt())
	fun pageUp() = scrollBy(-(viewH - 12).coerceAtLeast(12))
	fun pageDown() = scrollBy((viewH - 12).coerceAtLeast(12))
	fun home() = scrollTo(0)
	fun end() = scrollTo(max)
	fun resize(contentH: Int, viewH: Int) { this.contentH = contentH; this.viewH = viewH; scrollTo(offset) }

	fun thumb(trackH: Int): Pair<Int, Int>? {
		if (max <= 0 || trackH <= 0) return null
		val h = (trackH.toLong() * viewH / contentH).toInt().coerceIn(minOf(12, trackH), trackH)
		val top = ((trackH - h).toLong() * offset / max).toInt()
		return top to h
	}

	fun dragThumbTo(thumbTop: Int, trackH: Int) {
		val (_, h) = thumb(trackH) ?: return
		val room = (trackH - h).coerceAtLeast(1)
		scrollTo((thumbTop.coerceIn(0, room).toLong() * max / room).toInt())
	}
}

object WikiApi {
	data class Section(val level: Int, val title: String, val anchor: String)

	sealed interface Outcome {

		data class Page(val title: String, val html: String, val sections: List<Section> = emptyList(), val anchor: String? = null) : Outcome
		data class Missing(val title: String) : Outcome
		data class Error(val message: String) : Outcome
	}

	fun errorFor(status: Int?, error: Throwable?, title: String): Outcome.Error = Outcome.Error(when {
		status == 502 || status == 503 || status == 504 -> "The wiki is unavailable right now (HTTP $status, likely maintenance). Try again later."
		status == 404 -> "No wiki page called \"$title\"."
		status == 429 -> "The wiki is rate-limiting requests. Try again in a minute."
		status != null && status >= 400 -> "The wiki answered with HTTP $status."
		error is java.net.http.HttpTimeoutException || error is java.util.concurrent.TimeoutException -> "The wiki did not respond in time."
		error is java.net.ConnectException || error is java.net.UnknownHostException || error is java.nio.channels.UnresolvedAddressException -> "You seem to be offline: the wiki could not be reached."
		error != null -> "Could not reach the wiki (${error.javaClass.simpleName})."
		else -> "Could not load the wiki page."
	})

	private fun str(e: kotlinx.serialization.json.JsonElement?) = (e as? JsonPrimitive)?.content

	fun fromResponse(status: Int, body: String?, title: String): Outcome {
		if (status == 404) return Outcome.Missing(title)
		if (status != 200) return errorFor(status, null, title)
		return runCatching {
			val root = Json.parseToJsonElement(body.orEmpty()) as JsonObject
			(root["error"] as? JsonObject)?.let { e ->
				val code = str(e["code"])
				return if (code == "missingtitle" || code == "invalidtitle") Outcome.Missing(title) else Outcome.Error("The wiki returned an error ($code).")
			}
			val parse = root["parse"] as JsonObject
			val t = str(parse["title"]) ?: title
			val html = when (val text = parse["text"]) {
				is JsonPrimitive -> text.content
				is JsonObject -> (text["*"] as JsonPrimitive).content
				else -> error("no text")
			}
			val sections = (parse["sections"] as? JsonArray).orEmpty().mapNotNull { s ->
				val o = s as? JsonObject ?: return@mapNotNull null
				val level = str(o["level"])?.toIntOrNull() ?: 2
				val line = WikiDoc.plain(str(o["line"]).orEmpty())
				val anchor = str(o["anchor"]) ?: return@mapNotNull null
				Section(level, line, anchor)
			}
			val frag = (parse["redirects"] as? JsonArray).orEmpty().firstNotNullOfOrNull { r -> str((r as? JsonObject)?.get("tofragment"))?.takeIf { it.isNotEmpty() } }
			Outcome.Page(t, html, sections, frag)
		}.getOrElse { Outcome.Error("The wiki sent a page Apex could not read.") }
	}

	fun searchTitles(body: String?): List<String> = runCatching {
		val arr = Json.parseToJsonElement(body.orEmpty()) as JsonArray
		(arr[1] as JsonArray).mapNotNull { str(it) }
	}.getOrDefault(emptyList())

	fun sectionAnchor(sections: List<Section>, anchor: String?): String? {
		if (anchor.isNullOrBlank()) return null
		val want = anchor.replace('_', ' ').trim().lowercase()
		return sections.firstOrNull { it.anchor.replace('_', ' ').lowercase() == want || it.title.lowercase() == want }?.anchor
	}
}
