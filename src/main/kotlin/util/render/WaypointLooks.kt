// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.joml.Quaternionfc
import org.joml.Vector3f
import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.gui.config.DecimalHandler
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

enum class MarkerShape(private val id: String, val flat: Boolean, val filled: Boolean) : StringRepresentable {
	CUBE_FILLED("cube_filled", false, true),
	CUBE_OUTLINE("cube_outline", false, false),
	SQUARE_FILLED("square_filled", true, true),
	SQUARE_OUTLINE("square_outline", true, false),
	CIRCLE_FILLED("circle_filled", true, true),
	CIRCLE_OUTLINE("circle_outline", true, false),
	SPHERE_FILLED("sphere_filled", false, true),
	SPHERE_OUTLINE("sphere_outline", false, false),
	;

	override fun getSerializedName() = id
}

enum class WaypointType(val id: String) {
	PERSONAL("personal"), ROUTES("routes"), TEMPORARY("temporary"), NPCS("npcs"), FAIRY_SOULS("fairy-souls"),
	ENIGMA_SOULS("enigma-souls"), DIANA("diana"), DWARVEN("dwarven"), CRYSTAL_HOLLOWS("crystal-hollows"),
	MINING_SOLVERS("mining-solvers"), GLACITE("glacite"), CORPSES_LADDERS("corpses-ladders"), PESTS("pests"),
	KUUDRA("kuudra"), TERMINALS("terminals"), SECRET_WAYFINDER("secret-wayfinder"), FISHING("fishing"),
	FORAGING("foraging"), RIFT("rift"),
}

object WaypointLooks {
	class TypeLook(val useGeneral: ManagedOption<Boolean>, val shape: ManagedOption<MarkerShape>, val opacity: ManagedOption<Int>, val size: ManagedOption<Double>)

	@Config
	object TConfig : ManagedConfig("waypoint-looks", Category.GENERAL) {

		val types: Map<WaypointType, TypeLook> = WaypointType.entries.associateWith { t ->
			TypeLook(
				toggle("${t.id}-use-general") { true },
				choice("${t.id}-shape", MarkerShape::class.java) { MarkerShape.CUBE_FILLED },
				integer("${t.id}-opacity", 0, 100) { 100 },
				option("${t.id}-size", { 1.0 }, DecimalHandler(this, 0.25, 4.0, 0.05)),
			)
		}
	}

	data class Look(val shape: MarkerShape, val opacity: Int, val size: Double)

	const val MIN_SIZE = 0.25
	const val MAX_SIZE = 16.0

	fun resolve(general: Look, useGeneral: Boolean, shape: MarkerShape, opacity: Int, size: Double): Look =
		if (useGeneral) general.copy(opacity = general.opacity.coerceIn(0, 100), size = general.size.coerceIn(MIN_SIZE, MAX_SIZE))
		else Look(shape, percentOf(opacity, general.opacity), (general.size * size).coerceIn(MIN_SIZE, MAX_SIZE))

	fun percentOf(a: Int, b: Int): Int = Math.round(a.coerceIn(0, 100) * b.coerceIn(0, 100) / 100F)

	fun general(): Look = with(WaypointStyle.TConfig) { resolve(Look(shape, totalOpacity, markerSize), true, shape, 100, 1.0) }

	private val cache = arrayOfNulls<Look>(WaypointType.entries.size)
	private var cachedGeneral: Look? = null

	fun of(type: WaypointType): Look {
		val t = TConfig.types.getValue(type)
		val s = WaypointStyle.TConfig
		var g = cachedGeneral
		if (g == null || g.shape != s.shape || g.opacity != s.totalOpacity || g.size != s.markerSize) {
			g = general(); cachedGeneral = g; cache.fill(null)
		}
		val hit = cache[type.ordinal]
		val useGeneral = t.useGeneral.value
		if (hit != null && (if (useGeneral) hit === g else hit.shape == t.shape.value && hit.opacity == percentOf(t.opacity.value, g.opacity) &&
				hit.size == (g.size * t.size.value).coerceIn(MIN_SIZE, MAX_SIZE))) return hit
		val made = if (useGeneral) g else resolve(g, false, t.shape.value, t.opacity.value, t.size.value)
		cache[type.ordinal] = made
		return made
	}

	fun fade(colour: Int, percent: Int): Int {
		if (percent >= 100) return colour
		val a = Math.round((colour ushr 24) * percent.coerceAtLeast(0) / 100F).coerceIn(0, 255)
		return (a shl 24) or (colour and 0xFFFFFF)
	}

	fun isPlainCube(look: Look) = look.shape == MarkerShape.CUBE_FILLED && look.size == 1.0
}

object MarkerGeometry {
	const val CIRCLE_SEGMENTS = 32
	const val CIRCLE_RADIUS = 0.5F
	const val SPHERE_RINGS = 12
	const val SPHERE_SEGMENTS = 24

	val COS = FloatArray(CIRCLE_SEGMENTS + 1) { cos(2 * PI * it / CIRCLE_SEGMENTS).toFloat() }
	val SIN = FloatArray(CIRCLE_SEGMENTS + 1) { sin(2 * PI * it / CIRCLE_SEGMENTS).toFloat() }

	val SPHERE: FloatArray = FloatArray((SPHERE_RINGS + 1) * (SPHERE_SEGMENTS + 1) * 3).also { a ->
		var i = 0
		for (r in 0..SPHERE_RINGS) {
			val phi = PI * r / SPHERE_RINGS
			for (s in 0..SPHERE_SEGMENTS) {
				val theta = 2 * PI * s / SPHERE_SEGMENTS
				a[i++] = (0.5 * sin(phi) * cos(theta)).toFloat()
				a[i++] = (0.5 * cos(phi)).toFloat()
				a[i++] = (0.5 * sin(phi) * sin(theta)).toFloat()
			}
		}
	}

	fun sphereIndex(ring: Int, segment: Int) = (ring * (SPHERE_SEGMENTS + 1) + segment) * 3

	fun flatAxes(billboard: Boolean, orientation: Quaternionfc, right: Vector3f, up: Vector3f) {
		if (billboard) {
			right.set(1F, 0F, 0F).rotate(orientation)
			up.set(0F, 1F, 0F).rotate(orientation)
		} else {
			right.set(1F, 0F, 0F)
			up.set(0F, 0F, 1F)
		}
	}

	fun squareCorners(cx: Float, cy: Float, cz: Float, size: Float, right: Vector3f, up: Vector3f, out: FloatArray) {
		val h = size / 2
		val signs = SQUARE_SIGNS
		for (k in 0 until 4) {
			val sr = signs[k * 2] * h; val su = signs[k * 2 + 1] * h
			out[k * 3] = cx + right.x * sr + up.x * su
			out[k * 3 + 1] = cy + right.y * sr + up.y * su
			out[k * 3 + 2] = cz + right.z * sr + up.z * su
		}
	}

	private val SQUARE_SIGNS = floatArrayOf(-1F, -1F, 1F, -1F, 1F, 1F, -1F, 1F)
}
