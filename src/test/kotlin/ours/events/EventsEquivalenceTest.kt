// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.events

import java.io.File
import kotlin.reflect.KVisibility
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.events.ApexEvent
import dev.ashtonite365.apex.events.ApexEventBus
import dev.ashtonite365.apex.test.ours.OursGolden

class EventsEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	class Plain(val n: Int) : ApexEvent() {
		companion object : ApexEventBus<Plain>()
	}

	class Stoppable(val n: Int) : ApexEvent.Cancellable() {
		companion object : ApexEventBus<Stoppable>()
	}

	@Test
	fun busDeliversTheSame() {
		val log = mutableListOf<String>()
		log += "plainType ${Plain.eventType} ${Stoppable.eventType}"
		log += "registered ${ApexEventBus.allEventBuses.contains(Plain)} ${ApexEventBus.allEventBuses.contains(Stoppable)}"
		Plain.subscribe("a") { log += "a${it.n}" }
		Plain.subscribe(true, "b") { log += "b${it.n}"; if (it.n == 2) error("boom") }
		Plain.subscribe("c") { log += "c${it.n}" }
		val p = Plain(1)
		log += "same ${Plain.publish(p) === p}"
		for (i in 0..1) log += "throw " + runCatching { Plain.publish(Plain(2)) }.exceptionOrNull()?.let { "${it.javaClass.simpleName} ${it.cause?.message}" }
		log += "labels ${Plain.handlers.map { it.label }} ${Plain.handlers.map { it.receivesCancelled }} ${Plain.handlers.map { it.knownErrors.map { k -> k.simpleName } }}"

		Stoppable.subscribe("first") { log += "first${it.n} ${it.cancelled}"; if (it.n > 1) it.cancel() }
		Stoppable.subscribe("skipped") { log += "skipped${it.n}" }
		Stoppable.subscribe(true, "seesAll") { log += "seesAll${it.n} ${it.cancelled}" }
		Stoppable.subscribe("late") { log += "late${it.n}" }
		for (n in 1..3) log += "result$n ${Stoppable.publish(Stoppable(n)).cancelled}"
		val pre = Stoppable(9).also { it.cancel() }
		log += "pre ${Stoppable.publish(pre).cancelled}"
		val e = Stoppable(0)
		e.cancelled = true
		e.cancelled = false
		log += "toggle ${e.cancelled}"
		OursGolden.check("events-bus", log)
	}

	@Test
	fun eventClassesKeepTheirShape() {
		val root = File(ApexEvent::class.java.protectionDomain.codeSource.location.toURI()).resolve("dev/ashtonite365/apex/events")
		assertTrue(root.isDirectory, "classes dir $root")
		val names = root.walkTopDown().filter { it.isFile && it.name.endsWith(".class") && '$' !in it.name }
			.map { it.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.') }.sorted().toList()
		val out = mutableListOf<String>()
		for (n in names) {
			val jk = Class.forName("dev.ashtonite365.apex.events.$n", false, ApexEvent::class.java.classLoader)
			if (!ApexEvent::class.java.isAssignableFrom(jk)) continue
			val k = jk.kotlin
			out += "event $n data=${k.isData} abstract=${k.isAbstract} cancellable=${ApexEvent.Cancellable::class.java.isAssignableFrom(k.java)}" +
				" bus=${runCatching { (k.companionObjectInstance as? ApexEventBus<*>)?.eventType }.getOrElse { "ERR:" + it.javaClass.simpleName }}"
			out += "  ctor ${k.primaryConstructor?.parameters?.map { "${it.name}:${it.type}${if (it.isOptional) "=" else ""}" }}"
			out += "  props ${k.declaredMemberProperties.filter { it.visibility == KVisibility.PUBLIC }.map { "${it.name}:${it.returnType}${if (it is kotlin.reflect.KMutableProperty<*>) " var" else ""}" }.sorted()}"
		}
		OursGolden.check("events-shapes", out)
	}
}
