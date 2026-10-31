package dev.ashtonite365.apex.util

import io.github.moulberry.repo.constants.Islands
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.core.Position
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.repo.SafeRepoConstants
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder

object WarpTravel {

	val neverTargets: Set<String> = setOf("carnival", "")

	val warps: Sequence<Islands.Warp>
		get() = SafeRepoConstants.warps().asSequence().filterNot { it.warp in neverTargets }

	@Serializable
	data class LockedWarps(@SerialName("excludedWarps") val ids: MutableSet<String> = mutableSetOf())

	@Config
	object LockedStore : ProfileSpecificDataHolder<LockedWarps>(serializer(), "warp-util", { LockedWarps() })

	private const val LOCKED_REPLY = "You haven't unlocked this fast travel destination!"
	private const val UNLOCKED_PREFIX = "You may now fast travel to"

	private var lastSent: String? = null
	private var lastSentAt: TimeMark = TimeMark.farPast()

	private fun Islands.Warp.distanceSq(p: Position): Double {
		val dx = x - p.x()
		val dy = y - p.y()
		val dz = z - p.z()
		return dx * dx + dy * dy + dz * dz
	}

	private fun locked(): Set<String> = LockedStore.data.ids

	fun nearestWarp(island: SkyBlockIsland, pos: Position): Islands.Warp? {
		val here = warps.filter { it.mode == island.serverMode }.toList()
		if (here.isEmpty()) return null
		val lockedIds = locked()
		val open = here.filter { it.warp !in lockedIds }
		if (open.isEmpty()) return here.first()
		return open.minBy { it.distanceSq(pos) }
	}

	fun warpNearest(island: SkyBlockIsland, pos: Position) {
		val target = nearestWarp(island, pos)
		if (target == null) {
			Client.showChat(tr("apex.warp-util.no-warp-found", "Could not find an unlocked warp in ${island.userFriendlyName}"))
			return
		}
		val player = Client.player
		if (player != null && SkyBlockSession.skyblockIsland == island) {
			val mine = kotlin.math.sqrt(target.distanceSq(player.position()))
			val theirs = kotlin.math.sqrt(target.distanceSq(pos))
			if (mine * 1.1 < theirs) {
				Client.showChat(tr("apex.warp-util.already-close", "Already closer to destination than /warp ${target.warp}"))
				return
			}
		}
		Client.showChat(tr("apex.warp-util.attempting-to-warp", "Trying to warp to /warp ${target.warp}"))
		lastSent = target.warp
		lastSentAt = TimeMark.now()
		Client.runCommand("warp ${target.warp}")
	}

	private fun clearLocked() {
		LockedStore.data.ids.clear()
		LockedStore.markDirty()
	}

	@Subscribe
	fun resetLockedWarps(event: CommandEvent.SubCommand) {
		event.addSubcommand("clearwarps", ManagedConfig.Category.MISC) {
			onRun {
				clearLocked()
				source.sendFeedback(tr("apex.warp-util.clear-excluded", "Marked all /warp commands as potentially available."))
			}
		}
	}

	private fun onServerLine(line: String) {
		if (line.startsWith(UNLOCKED_PREFIX)) {
			clearLocked()
			return
		}
		if (line != LOCKED_REPLY) return
		val id = lastSent ?: return
		if (lastSentAt.passedTime() > 2.seconds) return
		lastSent = null
		LockedStore.data.ids.add(id)
		LockedStore.markDirty()
		Client.showChat(tr("apex.warp-util.mark-excluded", "SkyBlock Apex: Tried to warp to $id, but it was not unlocked. I will avoid warping there again."))
	}

	init {
		ProcessChatEvent.subscribe("WarpTravel:processChat") { onServerLine(it.bareText) }
	}
}
