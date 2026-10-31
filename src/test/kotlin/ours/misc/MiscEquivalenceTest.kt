
package dev.ashtonite365.apex.test.ours.misc

import io.github.notenoughupdates.moulconfig.observer.ObservableList
import io.github.notenoughupdates.moulconfig.xml.Bind
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.builtins.ListSerializer
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.features.misc.Hud
import dev.ashtonite365.apex.features.misc.LicenseViewer
import dev.ashtonite365.apex.features.misc.TimerFeature
import dev.ashtonite365.apex.test.ApexTestBootstrap
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.util.TimeMark

class MiscEquivalenceTest {
	@Test
	fun timersBehaveTheSame() {
		ApexTestBootstrap.bootstrapMinecraft()
		val out = mutableListOf<String>()
		val t = TimerFeature.Timer(TimeMark.ago(30.seconds), 1.minutes, "eggs", 7)
		out += "timer left~${(t.timeLeft().inWholeSeconds + 1) / 5 * 5}s done=${t.isDone()} ${t.message} ${t.timerId} ${t.duration}"
		val past = TimerFeature.Timer(TimeMark.ago(2.minutes), 1.minutes, "late", 8)
		out += "past left=${past.timeLeft()} done=${past.isDone()}"
		val far = TimerFeature.Timer(TimeMark.farPast(), 1.minutes, "far", 9)
		out += "far left=${far.timeLeft()} done=${far.isDone()}"
		out += "equal=${t == t.copy()} copyId=${t.copy(timerId = 1).timerId}"
		val saved = TimerFeature.timers.toList(); val savedId = TimerFeature.createTimerId
		try {
			TimerFeature.timers.clear(); TimerFeature.createTimerId = 40
			TimerFeature.startTimer(10.minutes, "pie")
			TimerFeature.startTimer(50.milliseconds, "toast")
			out += "started ${TimerFeature.timers.map { "${it.timerId}:${it.message}:${it.duration}" }} next=${TimerFeature.createTimerId}"
			TimerFeature.clearTimer(99)
			out += "clear missing ${TimerFeature.timers.map { it.timerId }}"
			TimerFeature.clearTimer(40)
			out += "clear 40 ${TimerFeature.timers.map { it.timerId }}"
			Thread.sleep(80)
			val outcome = runCatching { TimerFeature.tick(dev.ashtonite365.apex.events.TickEvent(1)) }.exceptionOrNull()?.javaClass?.simpleName ?: "ok"
			out += "tick $outcome ${TimerFeature.timers.map { it.timerId }}"
		} finally {
			TimerFeature.timers.clear(); TimerFeature.timers.addAll(saved); TimerFeature.createTimerId = savedId
		}
		OursGolden.check("misc-timers", out)
	}

	private fun binds(o: Any): String = o.javaClass.methods
		.mapNotNull { m -> m.getAnnotation(Bind::class.java)?.let { b -> (b.value.ifEmpty { m.name.removePrefix("get").replaceFirstChar(Char::lowercase) }) to m } }
		.sortedBy { it.first }
		.joinToString(" ") { (name, m) ->
			if (m.parameterCount > 0 || name == "open") "$name()"
			else when (val v = m.invoke(o)) {
				is Component -> "$name='${v.string}'"
				is ObservableList<*> -> "$name=[${v.size}]"
				else -> "$name=$v"
			}
		}

	@Test
	fun licenceBindingsAreTheSame() {
		ApexTestBootstrap.bootstrapMinecraft()
		val out = mutableListOf<String>()
		val json = """[{"projectName":"A"},{"projectName":"B","webPresence":"https://b.example","projectDescription":"bee","licenses":[{"licenseName":"MIT"},{"licenseName":"GPL","licenseUrl":"https://gpl.example"}],"developers":[{"name":"x"},{"name":"y","webPresence":"https://y.example"}]}]"""
		val list = Apex.json.decodeFromString(ListSerializer(LicenseViewer.Software.serializer()), json)
		for (s in list) {
			out += "software ${binds(s)}"
			s.licenses.forEach { out += "  license ${binds(it)}" }
			s.developers.forEach { out += "  developer ${binds(it)}" }
			out += "  encoded ${Apex.json.encodeToString(LicenseViewer.Software.serializer(), s)}"
		}
		out += "list ${binds(LicenseViewer.LicenseList(list))}"
		val bundled = LicenseViewer.licenses
		out += "bundled ${bundled?.softwares?.size?.let { it > 0 }}"
		OursGolden.check("misc-licences", out)
	}

	@Test
	fun hudConfigIsTheSame() {
		val out = mutableListOf<String>()
		out += "id ${Hud.identifier} ${Hud.TConfig.category} ${Hud.TConfig.sortedOptions.map { it.propertyName }}"
		OursGolden.check("misc-hud", out)
	}
}
