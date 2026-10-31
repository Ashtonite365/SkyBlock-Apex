// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.collections

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

class InstanceList<T : Any>(val name: String) {
	val queue: ReferenceQueue<T> = object : ReferenceQueue<T>() {}
	val set: MutableSet<Ref> = LinkedHashSet()

	val size: Int get() = this.purgeCollected().let { set.size }

	fun purgeCollected(): Unit {
		generateSequence { queue.poll() }.forEach { stale -> set.remove(stale) }
	}

	fun getAll(): List<T> = this.purgeCollected().let { set.mapNotNull(WeakReference<T>::get) }

	fun add(t: T): Unit = set.plusAssign(Ref(t))

	init { if (registryReady) allInstances.add(this) }

	inner class Ref(referent: T)
		: WeakReference<T>(referent) {
		val hashCode: Int = System.identityHashCode(referent)

		override fun hashCode(): Int = hashCode

		override fun equals(other: Any?): Boolean =
			other is InstanceList<*>.Ref && other.hashCode == hashCode && other.get() === get()
	}

	companion object Registry {

		private var registryReady = false

		val allInstances: InstanceList<InstanceList<*>> = InstanceList<InstanceList<*>>("InstanceLists").also { registry ->
			registryReady = true
			registry.add(registry)
		}
	}
}
