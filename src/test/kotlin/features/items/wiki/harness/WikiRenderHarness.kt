// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items.wiki.harness

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import dev.ashtonite365.apex.features.items.wiki.WikiApi
import dev.ashtonite365.apex.features.items.wiki.WikiDoc
import dev.ashtonite365.apex.features.items.wiki.WikiLayout
import dev.ashtonite365.apex.features.items.wiki.WikiLayout.Face

@EnabledIfEnvironmentVariable(named = "WIKI_HARNESS", matches = "1")
class WikiRenderHarness {
	private val REF = File("build/wiki-look-ref")
	private val GS = 2
	private val PANEL_W = 560; private val PANEL_H = 420

	private val ascii: BufferedImage = ImageIO.read(File(REF, "harness/ascii.png"))
	private val asciiW = IntArray(256) { c ->
		if (c == 32) return@IntArray 4
		val gx = (c % 16) * 8; val gy = (c / 16) * 8
		var last = -1
		for (x in 0 until 8) for (y in 0 until 8) if ((ascii.getRGB(gx + x, gy + y) ushr 24) > 0) last = maxOf(last, x)
		if (last < 0) 0 else last + 2
	}
	private val fallbackMain = Font("DejaVu Sans", Font.PLAIN, 8)
	private val fallbackSym = Font("Cardo", Font.PLAIN, 8)

	private fun fb(c: Char) = if (fallbackMain.canDisplay(c) || !fallbackSym.canDisplay(c)) fallbackMain else fallbackSym
	private fun ttf(name: String) = Font.createFont(Font.TRUETYPE_FONT, javaClass.getResourceAsStream("/assets/apex/font/wiki/$name")!!)
	private val sans = ttf("notosans-regular.ttf"); private val sansB = ttf("notosans-bold.ttf")
	private val serif = ttf("notoserif-regular.ttf"); private val serifB = ttf("notoserif-bold.ttf")
	private val frc = FontRenderContext(null, true, true)

	private fun awtFont(st: WikiLayout.TStyle): Font? = when (st.face) {
		Face.SANS -> if (st.bold) sansB else sans
		Face.SERIF -> if (st.bold) serifB else serif
		Face.GAME -> null
	}?.deriveFont(Font.PLAIN, 10f)

	private fun gameW(c: Char, bold: Boolean): Float =
		(if (c.code < 256 && (asciiW[c.code] > 0 || c == ' ')) asciiW[c.code].toFloat() else fb(c).deriveFont(9f).getStringBounds(c.toString(), frc).width.toFloat() + 1) + (if (bold && c != ' ') 1 else 0)

	private val measure = WikiLayout.Measure { t, st ->
		val f = awtFont(st)
		if (f == null) t.fold(0f) { a, c -> a + gameW(c, st.bold) } else f.getStringBounds(t, frc).width.toFloat()
	}

	private fun drawGame(g: Graphics2D, text: String, x: Float, y: Float, argb: Int, bold: Boolean, shadow: Boolean = false) {
		if (shadow) drawGame(g, text, x + 1, y + 1, (argb and 0xFCFCFC) shr 2 or (argb and 0xFF000000.toInt()), bold)
		var cx = x
		for (c in text) {
			if (c.code < 256 && asciiW[c.code] > 0) {
				val gx = (c.code % 16) * 8; val gy = (c.code / 16) * 8
				for (py in 0 until 8) for (px in 0 until 8) {
					val a = ascii.getRGB(gx + px, gy + py) ushr 24
					if (a == 0) continue
					g.color = Color(argb, true)
					g.fill(java.awt.geom.Rectangle2D.Float(cx + px, y + py, 1f, 1f))
					if (bold) g.fill(java.awt.geom.Rectangle2D.Float(cx + px + 1, y + py, 1f, 1f))
				}
			} else if (c != ' ') {
				g.color = Color(argb, true); g.font = fb(c).deriveFont(if (bold) Font.BOLD else Font.PLAIN, 8.5f)
				g.drawString(c.toString(), cx, y + 7.5f)
			}
			cx += gameW(c, bold)
		}
	}

	private fun drawText(g: Graphics2D, t: String, st: WikiLayout.TStyle, x: Float, y: Float, argb: Int) {
		val old = g.transform
		g.translate(x.toDouble(), y.toDouble()); g.scale(st.scale.toDouble(), st.scale.toDouble())
		val f = awtFont(st)
		if (f == null) drawGame(g, t, 0f, 0f, argb, st.bold)
		else { g.color = Color(argb, true); g.font = f; if (st.italic) g.shear(-0.2, 0.0); g.drawString(t, if (st.italic) 1.5f else 0f, 7.6f) }
		g.transform = old
	}

	private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build()
	private val imgDir = File(REF, "harness/imgcache").also { it.mkdirs() }
	private val images = ConcurrentHashMap<String, BufferedImage>()

	private fun key(u: String) = MessageDigest.getInstance("SHA-1").digest(u.toByteArray()).joinToString("") { "%02x".format(it) }

	private fun fetchAll(urls: Collection<String>) {
		val pool = Executors.newFixedThreadPool(6)
		for (u in urls.distinct()) pool.submit {
			runCatching {
				val f = File(imgDir, key(u))
				if (!f.exists()) {
					val r = http.send(HttpRequest.newBuilder(URI(u)).header("User-Agent", "SkyBlockApex-harness/1 (offscreen preview)").timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofByteArray())
					if (r.statusCode() == 200) f.writeBytes(r.body())
				}
				if (f.exists()) ImageIO.read(f)?.let { images[u] = it }
			}
		}
		pool.shutdown(); pool.awaitTermination(5, TimeUnit.MINUTES)
	}

	private fun canvas(w: Int, h: Int): Pair<BufferedImage, Graphics2D> {
		val img = BufferedImage(w * GS, h * GS, BufferedImage.TYPE_INT_ARGB)
		val g = img.createGraphics()
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
		g.scale(GS.toDouble(), GS.toDouble())
		return img to g
	}

	private fun fill(g: Graphics2D, x: Int, y: Int, w: Int, h: Int, argb: Int) { g.color = Color(argb, true); g.fillRect(x, y, w, h) }

	private fun drawOps(g: Graphics2D, laid: WikiLayout.Laid, ox: Int, oy: Int, top: Int, viewH: Int, th: WikiLayout.Theme) {
		for (o in laid.ops) {
			if (o.y + o.h < top || o.y > top + viewH) continue
			val x = ox + o.x; val y = oy + o.y - top
			when (o) {
				is WikiLayout.Rect -> fill(g, x, y, o.w, o.h, o.argb)
				is WikiLayout.Text -> drawText(g, o.text, o.st, x + o.sub, y.toFloat(), o.argb)
				is WikiLayout.Image -> {
					var ix = x; var iy = y; var iw = o.w; var ih = o.h
					if (o.slot) {
						fill(g, x, y, o.w, o.h, th.slotLight); fill(g, x, y, o.w - 1, o.h - 1, th.slotDark); fill(g, x + 1, y + 1, o.w - 2, o.h - 2, th.slot)
						ix++; iy++; iw -= 2; ih -= 2
					}
					val src = o.img?.src ?: continue
					val bi = images[src] ?: continue
					val s = minOf(iw.toFloat() / bi.width, ih.toFloat() / bi.height)
					val dw = maxOf(1, (bi.width * s).toInt()); val dh = maxOf(1, (bi.height * s).toInt())
					g.drawImage(bi, ix + (iw - dw) / 2, iy + (ih - dh) / 2, dw, dh, null)
					o.img?.count?.let { n -> if (o.slot) drawGame(g, n, (x + o.w - 1 - n.sumOf { gameW(it, false).toDouble() }).toFloat(), (y + o.h - 9).toFloat(), -1, false, shadow = true) }
				}
				is WikiLayout.Toggle -> {}
			}
		}
	}

	private fun chrome(g: Graphics2D, w: Int, h: Int, th: WikiLayout.Theme, back: Boolean, newLook: Boolean) {
		fill(g, 0, 0, w, h, 0xFF2B2B2B.toInt())
		val px = 1; val py = 1; val pw = w - 2; val ph = h - 2
		if (newLook) {
			fill(g, 0, 0, w, h, 0xFFA2A9B1.toInt()); fill(g, px, py, pw, ph, th.page); fill(g, px, py, pw, 16, 0xFF3790BB.toInt())
			drawGame(g, "Hypixel SkyBlock Wiki", px + 5f, py + 4f, -1, false)
			var right = px + pw - 3
			for ((label, bg) in listOf("x" to 0xFF8E2A20.toInt(), "Open in browser" to 0xFF2B7499.toInt(), "Sections \u25BE" to 0xFF2B7499.toInt())) {
				val bw = label.sumOf { gameW(it, false).toDouble() }.toInt() + 6; right -= bw
				fill(g, right, py + 3, bw, 11, bg); drawGame(g, label, right + 3f, py + 5f, -1, false); right -= 3
			}
		} else {
			fill(g, 0, 0, w, h, 0xFF4A4A4A.toInt()); fill(g, px, py, pw, ph, 0xF2121212.toInt()); fill(g, px, py, pw, 16, 0xFF262626.toInt())
		}
	}

	private fun page(file: String, title: String): Pair<String, String> {
		val raw = java.util.zip.GZIPInputStream(javaClass.getResourceAsStream("/apex-fixtures/item-wiki/$file")!!).readBytes().toString(Charsets.UTF_8)
		val p = WikiApi.fromResponse(200, raw, title) as WikiApi.Outcome.Page
		return p.title to p.html
	}

	private fun after(name: String, title: String, html: String, theme: WikiLayout.Theme = WikiLayout.Theme.LIGHT, smooth: Boolean = true, suffix: String = "") {
		val p = WikiDoc.parse(title, html)
		val pad = 6
		val bodyW = PANEL_W - 2 - pad * 2 - 6
		val laid = WikiLayout.layout(p, bodyW, measure, theme, WikiLayout.Prices.NONE, WikiLayout.State(), smooth)
		fetchAll(laid.ops.mapNotNull { (it as? WikiLayout.Image)?.img?.src })

		run {
			val (img, g) = canvas(PANEL_W, PANEL_H)
			chrome(g, PANEL_W, PANEL_H, theme, false, true)
			val bodyH = PANEL_H - 2 - 16 - 5 - 3
			g.clipRect(1 + pad, 1 + 16 + 5, bodyW, bodyH)
			drawOps(g, laid, 1 + pad, 1 + 16 + 5, 0, bodyH, theme)
			ImageIO.write(img, "png", File(REF, "after-$name$suffix-screen.png"))
		}

		run {
			val h = laid.height + 12
			val (img, g) = canvas(bodyW + 12, h)
			fill(g, 0, 0, bodyW + 12, h, theme.page)
			drawOps(g, laid, 6, 6, 0, h, theme)
			ImageIO.write(img, "png", File(REF, "after-$name$suffix-full.png"))
		}
	}

	private fun before(name: String, title: String, html: String) {
		val p = OldWikiDoc.parse(title, html)
		val w = 470; val h = 400; val pad = 6
		val bodyW = w - pad * 2 - 6; val bodyH = h - 16 - 4 - pad
		val m = OldWikiLayout.Measure { t, b -> t.sumOf { gameW(it, b).toDouble() }.toInt() }
		val laid = OldWikiLayout.layout(p, bodyW, m)
		val (img, g) = canvas(w, h)
		chrome(g, w, h, WikiLayout.Theme.DARK, false, false)
		drawGame(g, title, 5f, 4f, -1, false)
		val bx = pad; val by = 16 + 4
		g.clipRect(bx, by, bodyW, bodyH)
		for (line in laid.lines) {
			if (line.y > bodyH) break
			val y = by + line.y
			when (line.kind) {
				OldWikiLayout.Kind.GAP -> { if (line.rule) fill(g, bx, y, bodyW, 1, 0x40FFFFFF); continue }
				OldWikiLayout.Kind.TABLE_HEADER -> fill(g, bx, y - 1, bodyW, line.h + 1, 0x30FFFFFF)
				else -> {}
			}
			val base = when (line.kind) { OldWikiLayout.Kind.HEADING -> 0xFFFFD75E.toInt(); OldWikiLayout.Kind.SUBHEADING -> 0xFFFFE9A6.toInt(); else -> 0xFFDDDDDD.toInt() }
			for (s in line.segs) {
				val link = s.href != null && (s.href.startsWith("/w/") || s.href.startsWith("#") || s.href.startsWith("https://hypixelskyblock"))
				val colour = when { link -> 0xFF6FC3FF.toInt(); s.color != null -> 0xFF000000.toInt() or s.color; else -> base }
				drawGame(g, s.text, (bx + s.x).toFloat(), y.toFloat(), colour, s.bold)
				if (link) fill(g, bx + s.x, y + 9, m.width(s.text.trimEnd(), s.bold), 1, (colour and 0x80FFFFFF.toInt()))
			}
			if (line.rule && line.kind == OldWikiLayout.Kind.HEADING) fill(g, bx, y + line.h, bodyW, 1, 0x60FFD75E)
		}
		ImageIO.write(img, "png", File(REF, "before-$name-screen.png"))
	}

	@Test fun render() {
		for ((file, name, title) in listOf(
			Triple("parse-decoy.json.gz", "Decoy", "Decoy"), Triple("parse-hyperion.json.gz", "Hyperion", "Hyperion"),
			Triple("parse-golden-dragon-pet.json.gz", "Golden_Dragon_Pet", "Golden Dragon Pet"), Triple("parse-hegemony-artifact.json.gz", "Hegemony_Artifact", "Hegemony Artifact"),
		)) {
			val (t, html) = page(file, title)
			before(name, t, html)
			after(name, t, html)
		}
		val (t, html) = page("parse-decoy.json.gz", "Decoy")
		after("Decoy", t, html, WikiLayout.Theme.DARK, smooth = false, suffix = "-mcfont-dark")
	}
}
