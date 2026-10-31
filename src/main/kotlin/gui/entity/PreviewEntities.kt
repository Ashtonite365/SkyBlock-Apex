// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.entity

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import net.minecraft.world.entity.Entity
import dev.ashtonite365.apex.Apex

object PreviewEntityIds {
	private const val START = -1_000_000
	private val counter = AtomicInteger(START)
	fun next(): Int = counter.getAndDecrement()
	fun hasId(e: Entity): Boolean = try { e.id; true } catch (_: IllegalStateException) { false }
	fun <E : Entity> ensure(e: E): E { if (!hasId(e)) e.setId(next()); return e }
}

enum class PreviewOutcome { PRIMARY, FALLBACK, FAILED }

object PreviewDraw {

	fun <E : Any> drawWithFallback(
		primary: E?, primaryFailed: Boolean, fallback: () -> E?, draw: (E) -> Unit,

		drawsNothing: (E) -> String? = { null },
		onFail: (Throwable) -> Unit,
	): PreviewOutcome {
		if (primary != null && !primaryFailed) {
			val nothing = runCatching { drawsNothing(primary) }.getOrNull()
			if (nothing != null) onFail(IllegalStateException("draws nothing: $nothing"))
			else try { draw(primary); return PreviewOutcome.PRIMARY } catch (ex: Exception) { onFail(ex) }
		}
		val fb = runCatching { fallback() }.getOrNull() ?: return PreviewOutcome.FAILED
		return try { draw(fb); PreviewOutcome.FALLBACK } catch (_: Exception) { PreviewOutcome.FAILED }
	}
}

object MobRenderFailures {
	private val logged: MutableSet<String> = ConcurrentHashMap.newKeySet()
	var log: (String) -> Unit = { Apex.logger.warn(it) }
	fun report(mobId: String, t: Throwable, name: String? = null): Boolean {
		if (!logged.add(mobId)) return false
		val label = name?.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "")?.trim()?.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""
		log("[ItemList] mob render failed $mobId$label: ${t::class.java.simpleName}: ${t.message}")
		return true
	}
	fun resetForTests() { logged.clear(); log = { Apex.logger.warn(it) } }
}

object PreviewScreenBox {
	data class Box(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val size: Double, val mouseX: Double, val mouseY: Double)

	fun toScreen(m: org.joml.Matrix3x2fc, x0: Int, y0: Int, x1: Int, y1: Int, size: Double, mouseX: Double, mouseY: Double): Box {
		val a = m.transformPosition(org.joml.Vector2f(x0.toFloat(), y0.toFloat()))
		val b = m.transformPosition(org.joml.Vector2f(x1.toFloat(), y1.toFloat()))
		val mouse = m.transformPosition(org.joml.Vector2f(mouseX.toFloat(), mouseY.toFloat()))

		val scale = org.joml.Vector2f(m.m00(), m.m01()).length().toDouble()
		return Box(
			Math.round(minOf(a.x, b.x)), Math.round(minOf(a.y, b.y)), Math.round(maxOf(a.x, b.x)), Math.round(maxOf(a.y, b.y)),
			size * scale, mouse.x.toDouble(), mouse.y.toDouble(),
		)
	}
}

object MobRenderLog {
	private val logged: MutableSet<String> = ConcurrentHashMap.newKeySet()
	var log: (String) -> Unit = { Apex.logger.info(it) }
	fun report(mobId: String, outcome: PreviewOutcome, entityType: String?, name: String? = null): Boolean {
		if (!logged.add(mobId)) return false
		val label = name?.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "")?.trim()?.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""
		val what = when (outcome) {
			PreviewOutcome.PRIMARY -> "drew ${entityType ?: "?"}"
			PreviewOutcome.FALLBACK -> "drew the ? fallback"
			PreviewOutcome.FAILED -> "drew nothing (red box)"
		}
		log("[ItemList] mob render $mobId$label: $what")
		return true
	}
	fun resetForTests() { logged.clear(); log = { Apex.logger.info(it) } }
}

data class PreviewShape(
	val typeId: String, val invisible: Boolean, val width: Float, val height: Float, val scale: Float,

	val hasGear: Boolean,
)

object PreviewVisibility {

	val NO_MODEL_TYPES = setOf(
		"minecraft:marker", "minecraft:block_display", "minecraft:item_display", "minecraft:text_display",
		"minecraft:interaction", "minecraft:area_effect_cloud",
	)

	fun reason(s: PreviewShape): String? = when {
		s.typeId in NO_MODEL_TYPES -> "no model (${s.typeId})"
		!(s.width > 0f) || !(s.height > 0f) -> "zero bounding box (${s.width}x${s.height})"
		!(s.scale > 0f) -> "zero scale (${s.scale})"
		s.invisible && !s.hasGear -> "invisible with nothing worn or held"
		else -> null
	}

	fun reasonForChain(chain: List<PreviewShape>): String? {
		if (chain.isEmpty()) return "no entity"
		val reasons = chain.map(::reason)
		return if (reasons.all { it != null }) reasons.first() else null
	}
}
