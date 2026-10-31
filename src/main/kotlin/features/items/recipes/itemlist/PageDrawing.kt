// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.items.recipes.itemlist

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.shedaniel.math.Rectangle
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.LivingEntity
import java.net.URI
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import dev.ashtonite365.apex.features.items.recipes.ScrollPage
import dev.ashtonite365.apex.gui.entity.EntityRenderer
import dev.ashtonite365.apex.gui.entity.MobRenderFailures
import dev.ashtonite365.apex.gui.entity.MobRenderLog
import dev.ashtonite365.apex.gui.entity.PreviewDraw
import dev.ashtonite365.apex.gui.entity.PreviewOutcome
import dev.ashtonite365.apex.repo.RepoDownloadManager
import dev.ashtonite365.apex.repo.mobs.MobEntry
import dev.ashtonite365.apex.repo.mobs.MobRenderPlanner
import dev.ashtonite365.apex.util.Client

private const val ROW = 10
private const val WHITE = 0xFFFFFFFF.toInt()

object PageText {

	fun rows(text: Component, width: Int): Int = wrap(text, width).size.coerceAtLeast(1)

	fun wrap(text: Component, width: Int): List<FormattedCharSequence> {
		val font = runCatching { Client.font }.getOrNull() ?: return listOf(text.visualOrderText)
		return font.split(text, width).ifEmpty { listOf(FormattedCharSequence.EMPTY) }
	}
}

object PageActions {

	fun confirmLink(url: String) {
		val target = runCatching { URI(url) }.getOrNull() ?: return
		val previous = Client.screen
		Client.screen = (ConfirmLinkScreen({ open ->
			if (open) Client.openLink(target.toString())
			Client.screen = previous
		}, target, true))
	}
}

object PageMobs {
	private val cache = HashMap<String, LivingEntity?>()
	private val fallbackCache = HashMap<String, LivingEntity?>()
	private val unknownFlags = HashMap<String, Boolean>()

	fun clear() { cache.clear(); fallbackCache.clear(); unknownFlags.clear() }

	private fun resolveRef(ref: String): JsonObject? = runCatching {
		val file = RepoDownloadManager.repoSavedLocation.resolve(ref.substringAfter(':').trimStart('/'))
		if (!file.isRegularFile()) null else JsonParser.parseString(file.readText()) as? JsonObject
	}.getOrNull()

	private fun isKnownEntity(id: String): Boolean =
		Identifier.tryParse(id.lowercase())?.let { BuiltInRegistries.ENTITY_TYPE.containsKey(it) } ?: false

	private fun planFor(mob: MobEntry) = MobRenderPlanner.plan(mob, ::isKnownEntity, ::resolveRef)
		.also { unknownFlags[mob.id] = it.unknown }

	fun entityFor(mob: MobEntry): LivingEntity? {
		if (mob.id in cache) return cache[mob.id]
		val built = runCatching { planFor(mob).let { EntityRenderer.applyModifiers(it.entity, it.modifiers) } }.getOrNull()
		cache[mob.id] = built
		return built
	}

	fun isUnknown(mob: MobEntry): Boolean = unknownFlags[mob.id] ?: planFor(mob).unknown

	fun fallbackFor(mob: MobEntry): LivingEntity? {
		if (mob.id in fallbackCache) return fallbackCache[mob.id]
		val type = (mob.fallbackEntity ?: MobRenderPlanner.hintFor(mob.name) ?: "zombie").takeIf(::isKnownEntity) ?: "zombie"
		val built = runCatching { EntityRenderer.applyModifiers(type, listOf()) }.getOrNull()
		fallbackCache[mob.id] = built
		return built
	}
}

class MobPreview(val mob: MobEntry, val box: Rectangle, val tooltip: List<Component>) {
	private var failed = false

	fun draw(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val primary = PageMobs.entityFor(mob)
		val outcome = PreviewDraw.drawWithFallback(
			primary, failed, { PageMobs.fallbackFor(mob) },
			draw = { EntityRenderer.renderEntity(it, g, box.x, box.y, box.width.toDouble(), box.height.toDouble(), mouseX.toDouble(), mouseY.toDouble()) },
			drawsNothing = { EntityRenderer.drawsNothingReason(it) },
			onFail = { failed = true; MobRenderFailures.report(mob.id, it, mob.name) },
		)
		MobRenderLog.report(mob.id, outcome, primary?.let { BuiltInRegistries.ENTITY_TYPE.getKey(it.type).toString() }, mob.name)
		if (outcome != PreviewOutcome.FAILED) return
		g.fill(box.x, box.y, box.maxX, box.maxY, 0xFFAA2222.toInt())
		if (mob.name.isNotEmpty()) {
			val name = Component.literal(mob.name)
			g.text(Client.font, name, box.x + (box.width - Client.font.width(name)) / 2, box.y, WHITE, true)
		}
	}

	fun tooltipAt(mouseX: Int, mouseY: Int): List<Component> =
		if (mouseX in box.minX..box.maxX && mouseY in box.minY..box.maxY) tooltip else listOf()
}

class WrappedTextBlock(val x: Int, val y: Int, val width: Int, val lines: List<Component>) {
	fun draw(g: GuiGraphicsExtractor) {
		var top = y
		for (line in lines) {
			if (line.string.isEmpty()) continue
			for (row in PageText.wrap(line, width)) { g.text(Client.font, row, x, top, WHITE, true); top += ROW }
		}
	}

	fun bounds(): Rectangle = Rectangle(x, y, width, lines.sumOf { PageText.rows(it, width) } * ROW)
}

class ScrollLinesBlock(val box: Rectangle, val lines: List<PageLine>) {
	private val textW = box.width - 4
	private val wrapped by lazy { lines.map { PageText.wrap(it.text, textW) } }
	private val page by lazy { ScrollPage(wrapped.map { it.size }, box.height) }
	private var offset = 0

	fun draw(g: GuiGraphicsExtractor) {
		g.enableScissor(box.minX, box.minY, box.maxX, box.maxY)
		for ((i, rows) in wrapped.withIndex()) {
			val top = box.y + page.tops[i] - offset
			val bottom = top + rows.size.coerceAtLeast(1) * ROW
			if (bottom < box.minY || top > box.maxY) continue
			rows.forEachIndexed { k, row -> g.text(Client.font, row, box.x, top + k * ROW, WHITE, true) }
		}
		g.disableScissor()
		val thumb = page.thumb(offset) ?: return
		g.fill(box.maxX - 3, box.minY, box.maxX, box.maxY, 0x40FFFFFF)
		g.fill(box.maxX - 3, box.minY + thumb.first, box.maxX, box.minY + thumb.first + thumb.second, 0xC0FFFFFF.toInt())
	}

	fun scroll(mouseX: Double, mouseY: Double, amount: Double): Boolean {
		if (!box.contains(mouseX, mouseY) || !page.overflows) return false
		offset = page.scrolled(offset, amount)
		return true
	}

	fun click(mouseX: Double, mouseY: Double): Boolean {
		if (!box.contains(mouseX, mouseY)) return false
		val i = page.lineAt(mouseY - box.y, offset) ?: return false
		val action = lines[i].action ?: return false
		action()
		return true
	}
}
