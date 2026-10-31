// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.render

import org.joml.Vector3f

object WireBox {

	const val EPSILON = 1f / 512f

	data class Edge(val from: Vector3f, val to: Vector3f) {
		val normal: Vector3f get() = Vector3f(to).sub(from).normalize()
	}

	fun edges(min: Vector3f, max: Vector3f): List<Edge> {
		val out = ArrayList<Edge>(12)
		val xs = floatArrayOf(min.x, max.x); val ys = floatArrayOf(min.y, max.y); val zs = floatArrayOf(min.z, max.z)
		for (a in 0..1) for (b in 0..1) {
			out += Edge(Vector3f(min.x, ys[a], zs[b]), Vector3f(max.x, ys[a], zs[b]))
			out += Edge(Vector3f(xs[a], min.y, zs[b]), Vector3f(xs[a], max.y, zs[b]))
			out += Edge(Vector3f(xs[a], ys[b], min.z), Vector3f(xs[a], ys[b], max.z))
		}
		return out
	}

	fun blockEdges(): List<Edge> = edges(Vector3f(-EPSILON), Vector3f(1 + EPSILON))
}
