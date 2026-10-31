// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.misc

import io.github.notenoughupdates.moulconfig.observer.ObservableList
import io.github.notenoughupdates.moulconfig.xml.Bind
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.decodeFromStream
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.GuiXml
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.tr

object LicenseViewer {
	private fun shown(text: String?, missing: String): Component = Component.literal(text ?: missing)
	private fun browse(url: String?) = url?.let(Client::openLink)

	@Serializable
	data class Software(val licenses: List<License> = listOf(), val webPresence: String? = null, val projectName: String, val projectDescription: String? = null, val developers: List<Developer> = listOf()) {
		@Bind("projectName") fun nameLine() = shown(projectName, "")
		@Bind("projectDescription") fun descriptionLine() = shown(projectDescription, "<no project description>")
		@Bind("hasWebPresence") fun linked() = webPresence != null
		@Bind("webPresence") fun linkLine() = shown(webPresence, "<no web presence>")
		@Bind("open") fun visit() { browse(webPresence) }

		@Transient @get:Bind("developers") val developerRows = ObservableList(developers)
		@Transient @get:Bind("licenses") val licenseRows = ObservableList(licenses)
	}

	@Serializable
	data class Developer(val name: String, val webPresence: String? = null) {
		@Bind("name") fun nameLine() = shown(name, "")
		@Bind("hasWebPresence") fun linked() = webPresence != null
		@Bind("webPresence") fun linkLine() = shown(webPresence, "<no web presence>")
		@Bind("open") fun visit() { browse(webPresence) }
	}

	@Serializable
	data class License(val licenseName: String, val licenseUrl: String? = null) {
		@Bind("name") fun nameLine() = shown(licenseName, "")
		@Bind("hasUrl") fun linked() = licenseUrl != null
		@Bind("url") fun linkLine() = shown(licenseUrl, "<no link to license text>")
		@Bind("open") fun visit() { browse(licenseUrl) }
	}

	data class LicenseList(val softwares: List<Software>) {
		@get:Bind("softwares") val rows = ObservableList(softwares)
	}

	val licenses: LicenseList? = Faults.attempt("Could not load licenses") { readBundled() }.valueOrNull()

	@OptIn(ExperimentalSerializationApi::class)
	private fun readBundled(): LicenseList? {
		val stream = javaClass.getResourceAsStream("/LICENSES-APEX.json") ?: error("Could not find LICENSES-APEX.json")
		return Apex.json.decodeFromStream<List<Software>?>(stream)?.let(::LicenseList)
	}

	fun showLicenses() {
		val opened = Faults.attempt("Could not display licenses") {
			ScreenUtil.setScreenLater(GuiXml.loadScreen("license_viewer/index", licenses!!, null))
		}
		opened.orElse {
			Client.showChat(tr("apex.licenses.notfound", "Could not load licenses. Please check the SkyBlock Apex source code for information directly."))
		}
	}

	@Subscribe
	fun licensesCommand(event: CommandEvent.SubCommand) {
		event.addSubcommand("licenses", ManagedConfig.Category.MISC) { onRun { showLicenses() } }
	}
}
