// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.skyblock

import java.util.UUID
import java.util.regex.Matcher
import java.util.regex.Pattern
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket.PartyRole
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket
import org.intellij.lang.annotations.Language
import kotlinx.coroutines.launch
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.apis.Routes
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.SkyBlockSession

object PartyUtil {

	var party: Roster? = null
		set(value) {
			field = value
			version++
		}

	@Volatile
	var version: Int = 0
		private set

	data class Roster(val members: List<RosterEntry>)

	data class RosterEntry(val name: String, val role: PartyRole, val uuid: UUID? = null)

	object Internal {
		private val modApi: HypixelModAPI = HypixelModAPI.getInstance()

		val packetHandler = modApi.createHandler(ClientboundPartyInfoPacket::class.java) { onPartyPacket(it) }

		private fun onPartyPacket(packet: ClientboundPartyInfoPacket) {
			if (!packet.isInParty) {
				Client.onClientThread { party = Roster(emptyList()) }
				return
			}
			val members = packet.memberMap.values.toList()
			Apex.coroutineScope.launch {
				val resolved = members.map { entryFor(it.uuid, it.role) }
				Client.onClientThread { party = Roster(resolved) }
			}
		}

		private suspend fun entryFor(uuid: UUID, role: PartyRole): RosterEntry {
			val found = Routes.getPlayerNameForUUID(uuid)
			if (found == null) Faults.logFault("Could not find username for player $uuid")
			return RosterEntry(found ?: "Ghost", role, uuid)
		}

		fun requestRoster() {
			if (!SkyBlockSession.inHypixel) return
			modApi.sendPacket(ServerboundPartyInfoPacket())
		}

		object ChatLines {
			@Language("RegExp")
			val WHO: String = "(\\[[^\\]]+\\] )?(?<name>[a-zA-Z0-9_]{2,16})"

			@Language("RegExp")
			val WHO_ELSE: String = WHO.replace("<name>", "<name2>")

			private fun line(@Language("RegExp") regex: String): Pattern = Pattern.compile(regex)

			val youJoined: Pattern = line("You have joined $WHO's? party!")
			val someoneJoined: Pattern = line("$WHO joined the party\\.")
			val youLeft: Pattern = line("You left the party\\.")
			val expiredEmpty: Pattern = line("The party was disbanded because all invites expired and the party was empty\\.")
			val someoneLeft: Pattern = line("$WHO has left the party\\.")
			val someoneRemoved: Pattern = line("$WHO has been removed from the party\\.")
			val offlineKicked: Pattern = line("Kicked $WHO because they were offline\\.")
			val disconnected: Pattern = line("$WHO was removed from your party because they disconnected\\.")
			val leaderLeft: Pattern = line("The party was transferred to $WHO because $WHO_ELSE left\\.?")
			val handedOver: Pattern = line("The party was transferred to $WHO by $WHO_ELSE\\.?")
			val disbandedBy: Pattern = line("$WHO has disbanded the party!")
			val youKicked: Pattern = line("You have been kicked from the party by $WHO ?\\.?")
			val finderJoin: Pattern = line("Party Finder > $WHO joined the .* group!.*")
		}

		fun editRoster(allowEmpty: Boolean = false, edit: (MutableList<RosterEntry>) -> Unit) {
			val current = party?.members.orEmpty()
			if (!allowEmpty && current.isEmpty()) return
			val working = current.toMutableList()
			edit(working)
			party = Roster(working)
		}

		fun MutableList<RosterEntry>.replaceMember(name: String, change: (RosterEntry) -> RosterEntry) {
			val index = indexOfFirst { it.name == name }
			val existing = if (index >= 0) removeAt(index) else RosterEntry(name, PartyRole.MEMBER)
			add(change(existing))
		}

		fun addMember(name: String) = editRoster(allowEmpty = true) { members ->
			if (members.isEmpty()) members += RosterEntry(Client.playerName, PartyRole.LEADER)
			members.replaceMember(name) { it }
		}

		private fun dropNamed(members: MutableList<RosterEntry>, name: String) {
			members.removeAll { it.name == name }
		}

		private fun forgetParty() {
			if (party != null) party = Roster(emptyList())
		}

		private fun drop(match: Matcher) = editRoster { dropNamed(it, match.group("name")) }

		private val reactions: List<Pair<Pattern, (Matcher) -> Unit>> by lazy {
			listOf(
				ChatLines.youJoined to { m ->
					val leader = m.group("name")
					party = Roster(listOf(RosterEntry(leader, PartyRole.LEADER), RosterEntry(Client.playerName, PartyRole.MEMBER)))
					requestRoster()
				},
				ChatLines.someoneJoined to { m -> addMember(m.group("name")) },
				ChatLines.finderJoin to { m -> addMember(m.group("name")) },
				ChatLines.youLeft to { _ -> forgetParty() },
				ChatLines.expiredEmpty to { _ -> forgetParty() },
				ChatLines.disbandedBy to { _ -> forgetParty() },
				ChatLines.youKicked to { _ -> forgetParty() },
				ChatLines.someoneLeft to ::drop,
				ChatLines.someoneRemoved to ::drop,
				ChatLines.offlineKicked to ::drop,
				ChatLines.disconnected to ::drop,
				ChatLines.leaderLeft to { m ->
					val newLeader = m.group("name")
					val gone = m.group("name2")
					editRoster {
						dropNamed(it, gone)
						it.replaceMember(newLeader) { e -> e.copy(role = PartyRole.LEADER) }
					}
				},
				ChatLines.handedOver to { m ->
					val newLeader = m.group("name")
					val previous = m.group("name2")
					editRoster {
						it.replaceMember(newLeader) { e -> e.copy(role = PartyRole.LEADER) }
						it.replaceMember(previous) { e -> e.copy(role = PartyRole.MOD) }
					}
				},
			)
		}

		@Subscribe
		fun onJoinServer(event: WorldReadyEvent) {
			if (party == null) requestRoster()
		}

		@Subscribe
		fun readPartyChat(event: ProcessChatEvent) {
			val text = event.bareText
			for ((pattern, react) in reactions) {
				val match = pattern.matcher(text)
				if (match.matches()) {
					react(match)
					return
				}
			}
		}
	}
}
