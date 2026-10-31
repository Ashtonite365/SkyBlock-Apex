
package dev.ashtonite365.apex.test.ours.compatloader

import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.ApexConfigScreenProvider
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.HoverStackSource
import dev.ashtonite365.apex.util.compatloader.PackageOwnership
import dev.ashtonite365.apex.util.compatloader.CompatLoader
import dev.ashtonite365.apex.util.compatloader.CompatSwitch
import dev.ashtonite365.apex.util.data.IConfigProvider

class CompatLoaderEquivalenceTest {
	@CompatLoader.RequireMod("minecraft") class NeedsMinecraft
	@CompatLoader.RequireMod("minecraft") @CompatLoader.RequireMod("no-such-mod-xyz") class NeedsTwo
	@CompatLoader.RequireMod("no-such-mod-xyz") class NeedsMissing
	class NeedsNothing

	private fun <T> attempt(f: () -> T): String = runCatching { f().toString() }.getOrElse { "ERR:${it.javaClass.simpleName} ${it.message}" }

	@Test
	fun discoveryAndGatingAreTheSame() {
		val out = mutableListOf<String>()
		out += "aggressive=${Faults.strictFaults}"
		for (k in listOf(IConfigProvider::class, ApexConfigScreenProvider::class, HoverStackSource::class)) {
			@Suppress("UNCHECKED_CAST") val l = CompatLoader(k.java as Class<Any>)
			out += "${k.simpleName} kClass=${l.serviceType.name} all=${attempt { l.allValidInstances.map { it.javaClass.name }.sorted() }} single=${attempt { l.singleInstance?.javaClass?.name }}"
		}
		val gate = CompatLoader(IConfigProvider::class)
		for (c in listOf(NeedsMinecraft::class.java, NeedsTwo::class.java, NeedsMissing::class.java, NeedsNothing::class.java))
			out += "requires ${c.simpleName} ${gate.checkRequiredModsPresent(c)}"
		val rejecting = object : CompatLoader<IConfigProvider>(IConfigProvider::class.java) {
			override fun shouldLoad(type: Class<out IConfigProvider>): Boolean = if (type.name.hashCode() % 2 == 0) error("boom") else super.shouldLoad(type)
		}
		out += "rejecting all=${attempt { rejecting.allValidInstances.map { it.javaClass.name }.sorted() }}"
		val nobody = object : CompatLoader<IConfigProvider>(IConfigProvider::class) {
			override fun shouldLoad(type: Class<out IConfigProvider>) = false
		}
		out += "nobody all=${attempt { nobody.allValidInstances }} single=${attempt { nobody.singleInstance }}"
		out += "metas=${CompatSwitch.everyOwner.map { it.javaClass.name }.sorted()}"
		for (name in listOf("dev.ashtonite365.apex.Apex", "dev.ashtonite365.apex.compat.jade.Compat", "dev.ashtonite365.apex.compat.jei.X", "org.example.Unowned", "NoPackage", ""))
			out += "shouldLoad '$name' ${attempt { CompatSwitch.mayLoad(name) }} owners=${CompatSwitch.everyOwner.filter { it.ownsClass(name) }.size}"
		val pk = arrayOf("a.b", "a.b.c", "")
		for (name in listOf("a.b.C", "a.b.c.D", "a.bc.D", "a.b", "C", "a.b.C\$Inner", ".x"))
			out += "owned '$name' ${PackageOwnership.inPackages(name, *pk)} none=${PackageOwnership.inPackages(name)}"
		OursGolden.check("compatloader", out)
	}
}
