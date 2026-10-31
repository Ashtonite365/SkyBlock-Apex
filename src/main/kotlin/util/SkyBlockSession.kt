package dev.ashtonite365.apex.util

import dev.ashtonite365.apex.events.AllowChatEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.ProfileSwitchEvent
import dev.ashtonite365.apex.events.SkyblockServerUpdateEvent
import java.time.ZoneId
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket

object SkyBlockSession {
	private val profileIdLine = Regex("Profile ID: ([a-z0-9\\-]+)")
	private val askCooldown = 10.seconds

	val profileSuggestTexts: List<String> = listOf("[DASHES]", "[NO DASHES]").map { "CLICK THIS TO SUGGEST IT IN CHAT $it" }

	val NULL_UUID: UUID = UUID(0L, 0L)

	val hypixelTimeZone: ZoneId = ZoneId.of("US/Eastern")

	private var knownProfile: UUID? = null
	private var lastAsked: TimeMark = TimeMark.farPast()
	private var helloSeen = false

	var profileId: UUID?
		get() = if (inSkyBlock) knownProfile else null
		set(value) {
			knownProfile = value
		}

	val profileIdOrZero: UUID get() = profileId ?: NULL_UUID

	var serverLocation: ServerLocation? = null

	val skyblockIsland: SkyBlockIsland? get() = serverLocation?.skyblockIsland

	val hasLiveServer: Boolean
		get() = serverLocation?.server.let { it != null && it != "limbo" }

	val inSkyBlock: Boolean get() = serverLocation?.gametype == "SKYBLOCK"

	val inHypixel: Boolean get() = helloSeen

	private fun moveTo(location: ServerLocation?) {
		val old = serverLocation
		serverLocation = location
		SkyblockServerUpdateEvent.publish(SkyblockServerUpdateEvent(old, location))
	}

	private fun locationOf(packet: ClientboundLocationPacket) = ServerLocation(
		packet.serverName,
		packet.serverType.map { it.name() }.orElse(null),
		packet.mode.orElse(null),
		packet.map.orElse(null),
	)

	private fun askForProfile() {
		if (!inSkyBlock || knownProfile != null) return
		if (lastAsked.passedTime() < askCooldown) return
		lastAsked = TimeMark.now()
		Client.runCommand("profileid")
	}

	private fun readProfileLine(text: String) {
		val raw = profileIdLine.find(text)?.groupValues?.get(1) ?: return
		val id = runCatching { UUID.fromString(raw) }.getOrNull() ?: return
		val old = knownProfile
		knownProfile = id
		if (old != id) ProfileSwitchEvent.publish(ProfileSwitchEvent(old, id))
	}

	fun init() {
		val api = HypixelModAPI.getInstance()
		api.subscribeToEventPacket(ClientboundLocationPacket::class.java)
		api.createHandler(ClientboundHelloPacket::class.java) { helloSeen = true }
		api.createHandler(ClientboundLocationPacket::class.java) { moveTo(locationOf(it)) }
		ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
			helloSeen = false
			if (serverLocation != null) moveTo(null)
		}
		SkyblockServerUpdateEvent.subscribe("SkyBlockSession:sendProfileId") { askForProfile() }
		AllowChatEvent.subscribe("SkyBlockSession:hideProfileSuggest") { event ->
			if (event.bareText.trim() in profileSuggestTexts) event.cancel()
		}
		ProcessChatEvent.subscribe(receivesCancelled = true, "SkyBlockSession:loadProfile") { event ->
			readProfileLine(event.bareText)
		}
	}
}
