// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.compatloader

import java.util.ServiceLoader
import dev.ashtonite365.apex.events.subscription.SubscriptionList
import dev.ashtonite365.apex.init.AutoDiscoveryPlugin
import dev.ashtonite365.apex.util.Faults

annotation class CompatEntry

interface CompatOwnership {

	fun ownsClass(className: String): Boolean

	val switchObject: CompatSwitch
}

interface CompatSwitch {
	fun enabledHere(): Boolean

	companion object {

		val everyOwner: List<CompatOwnership> = loadOwners()

		private fun loadOwners(): List<CompatOwnership> =
			ServiceLoader.load(CompatOwnership::class.java, CompatOwnership::class.java.classLoader).toList()

		fun mayLoad(className: String): Boolean {
			val owners = everyOwner.filter { it.ownsClass(className) }
			if (Faults.strictFaults) {
				require(owners.size == 1) {
					"Orphaned or duplicate owned class $className (${owners.map { it.switchObject }}). Consider adding a @CompatEntry object."
				}
			}
			val first = owners.firstOrNull() ?: return true
			return first.switchObject.enabledHere()
		}
	}
}

object PackageOwnership {

	fun inPackages(className: String, vararg packages: String): Boolean =
		className.substringBeforeLast('.') in packages
}
