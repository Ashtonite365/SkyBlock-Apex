// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.modules

data class ModuleDescriptor(
	val id: String,
	val category: String,
	val tag: ModuleTag,
	val dependsOn: List<String> = emptyList(),
)

class ModuleGraphException(message: String) : IllegalArgumentException(message)

class ModuleGraph(modules: List<ModuleDescriptor>) {
	val byId: Map<String, ModuleDescriptor>

	val order: List<String>

	private val dependents: Map<String, List<String>>

	init {
		val dup = modules.groupBy { it.id }.filterValues { it.size > 1 }.keys
		if (dup.isNotEmpty()) throw ModuleGraphException("duplicate module ids: ${dup.sorted()}")
		byId = modules.associateBy { it.id }
		for (m in modules) for (d in m.dependsOn) {
			if (d !in byId) throw ModuleGraphException("module ${m.id} depends on unknown module $d")
			if (d == m.id) throw ModuleGraphException("module ${m.id} depends on itself")
		}
		val out = mutableListOf<String>()
		val state = HashMap<String, Int>()
		fun visit(id: String, path: List<String>) {
			when (state[id]) {
				2 -> return
				1 -> throw ModuleGraphException("dependency cycle: ${(path.dropWhile { it != id } + id).joinToString(" -> ")}")
			}
			state[id] = 1
			for (d in byId.getValue(id).dependsOn.sorted()) visit(d, path + id)
			state[id] = 2
			out += id
		}
		for (id in byId.keys.sorted()) visit(id, emptyList())
		order = out
		dependents = modules.flatMap { m -> m.dependsOn.map { it to m.id } }.groupBy({ it.first }, { it.second })
	}

	fun dependenciesOf(id: String): List<String> {
		val need = HashSet<String>()
		fun walk(x: String) { for (d in byId.getValue(x).dependsOn) if (need.add(d)) walk(d) }
		walk(id)
		return order.filter { it in need }
	}

	fun dependentsOf(id: String): List<String> {
		val seen = HashSet<String>()
		fun walk(x: String) { for (d in dependents[x].orEmpty()) if (seen.add(d)) walk(d) }
		walk(id)
		return order.filter { it in seen }
	}
}
