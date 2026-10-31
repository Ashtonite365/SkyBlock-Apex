package dev.ashtonite365.apex.features.profileviewer

import com.mojang.brigadier.arguments.StringArgumentType
import java.io.ByteArrayInputStream
import java.net.http.HttpResponse
import java.util.Base64
import java.util.UUID
import com.mojang.authlib.GameProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.jvm.optionals.getOrNull
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.DyedItemColor
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.placeholderHeadUuid
import dev.ashtonite365.apex.util.mc.applyEncodedSkin
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.apis.Routes
import dev.ashtonite365.apex.apis.UrsaManager
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.ModifyChatEvent
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.runsOnClick
import dev.ashtonite365.apex.util.hover
import net.minecraft.ChatFormatting
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.MinecraftDispatcher
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.tr

object ProfileViewer {
	val identifier: String get() = "profile-viewer"

	@Config
	object TConfig : ManagedConfig(category = Category.PROFILE_VIEWER, name = identifier) {
		val registerPv by toggle("register-pv") { true }
		val chatButton by toggle("chat-button") { true }
		val partyFinderButton by toggle("party-finder-button") { true }
		val profileMenuButton by toggle("profile-menu-button") { true }
		val showDungeonPbs by toggle("show-dungeon-pbs") { true }
		val registerStalk by toggle("register-stalk") { true }
		val partyFinderStats by toggle("party-finder-stats") { true }
		val lookedAtKeybind by keyBindingWithDefaultUnbound("looked-at-keybind")
	}

	@Subscribe
	fun onCommands(event: CommandEvent) {
		if (TConfig.registerPv) {
			event.deleteCommand("pv")
			event.register("pv") {
				onArgument("name", StringArgumentType.word()) { name ->
					onRun { open(this[name]) }
				}
				onRun { open(Client.game.user.name) }
			}
		}
	}

	@Subscribe
	fun onSubCommand(event: CommandEvent.SubCommand) {
		event.addSubcommand("pv", dev.ashtonite365.apex.util.data.ManagedConfig.Category.PROFILE_VIEWER) {
			onArgument("name", StringArgumentType.word()) { name ->
				onRun { open(this[name]) }
			}
			onRun { open(Client.game.user.name) }
		}
	}

	private val chatNamePatterns = listOf(
		"^(?:Party|Guild|Co-op|Officer) > (?:\\[[^\\]]+] )?(\\w{3,16})(?: \\[[^\\]]+])?: .*",
		"^(?:From|To) (?:\\[[^\\]]+] )?(\\w{3,16}): .*",
		"^(?:\\[[^\\]]+] )?(\\w{3,16}) (?:joined|left) the party\\.$",
		"^(?:\\[[^\\]]+] )?(\\w{3,16}) has invited you to join (?:their|\\w+'s) (?:party|co-op)!.*",
		"^(?:\\[[^\\]]+] )?(\\w{3,16}) invited (?:\\[[^\\]]+] )?\\w{3,16} to the party!.*",
	).map { it.toRegex() }

	@Subscribe
	fun addChatButton(event: ModifyChatEvent) {
		if (!TConfig.chatButton || !SkyBlockSession.inSkyBlock) return
		val text = event.bareText
		val name = chatNamePatterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) } ?: return
		if (name.equals(Client.game.user.name, ignoreCase = true)) return
		event.replaceWith = event.replaceWith.copy().append(
			Component.literal(" [PV]").withStyle(ChatFormatting.AQUA)
				.runsOnClick("/apex pv $name")
				.hover(tr("apex.pv.chat-button.hover", "Open the profile viewer for $name"))
		)
	}

	fun open(name: String) {
		val screen = ProfileViewerScreen(name)
		ScreenUtil.setScreenLater(screen)
		Apex.coroutineScope.launch {
			val result = try {
				load(name)
			} catch (e: Exception) {
				Apex.logger.error("Could not load profile viewer data for $name", e)
				LoadResult.Error(tr("apex.pv.error", "Could not load profile data: ${e.message}"))
			}
			withContext(MinecraftDispatcher) {
				screen.setResult(result)
			}
		}
	}

	sealed interface LoadResult {
		data class Error(val message: Component) : LoadResult
		data class Success(val data: PlayerProfiles) : LoadResult
	}

	data class PlayerProfiles(
		val uuid: UUID,
		val name: String,
		val displayName: String,
		val profiles: List<SkyblockProfile>,

		val gameProfile: GameProfile?,
	)

	data class SkyblockProfile(
		val profileId: String,
		val cuteName: String,
		val gameMode: String?,
		val selected: Boolean,
		val bank: Double?,
		val member: JsonObject,
		val memberCount: Int,
	)

	suspend fun ursaJson(path: String): JsonObject {
		val body = UrsaManager.request(path.split("/"), HttpResponse.BodyHandlers.ofString())
		return Apex.json.parseToJsonElement(body).jsonObject
	}

	suspend fun load(name: String): LoadResult {
		val uuid = Routes.getUUIDForPlayerName(name)
			?: return LoadResult.Error(tr("apex.pv.unknown-player", "Unknown player: $name"))
		val realName = Routes.getPlayerNameForUUID(uuid) ?: name
		val profilesJson = ursaJson("v1/hypixel/v2/profiles/$uuid")
		if (profilesJson["success"].bool() == false) {
			return LoadResult.Error(
				tr("apex.pv.api-error", "Hypixel API error: ${profilesJson["cause"].str() ?: "unknown"}")
			)
		}
		val dashless = uuid.toString().replace("-", "")
		val profiles = profilesJson.arr("profiles").mapNotNull { profileEl ->
			val profile = profileEl as? JsonObject ?: return@mapNotNull null
			val members = profile.obj("members") ?: return@mapNotNull null
			val member = members.obj(dashless) ?: return@mapNotNull null
			SkyblockProfile(
				profileId = profile["profile_id"].str() ?: "",
				cuteName = profile["cute_name"].str() ?: "?",
				gameMode = profile["game_mode"].str(),
				selected = profile["selected"].bool() ?: false,
				bank = profile.path("banking", "balance").num(),
				member = member,
				memberCount = members.size,
			)
		}
		val playerJson = runCatching { ursaJson("v1/hypixel/v2/player/$uuid") }.getOrNull()
		val displayName = playerJson?.obj("player")?.let { rankedName(it, realName) } ?: "§7$realName"
		val gameProfile = runCatching {
			withContext(Dispatchers.IO) { Client.game.services().sessionService.fetchProfile(uuid, false)?.profile() }
		}.getOrNull()
		return LoadResult.Success(PlayerProfiles(uuid, realName, displayName, profiles, gameProfile))
	}

	private fun rankedName(player: JsonObject, name: String): String {
		val prefix = player["prefix"].str()
		if (prefix != null) return "$prefix $name"
		val monthly = player["monthlyPackageRank"].str()?.takeIf { it != "NONE" }
		val rank = player["rank"].str()?.takeIf { it != "NORMAL" }
		val pkg = monthly ?: rank ?: player["newPackageRank"].str() ?: player["packageRank"].str()
		val plusColour = player["rankPlusColor"].str()?.let { colourCode(it) } ?: "c"
		return when (pkg) {
			"SUPERSTAR" -> "§6[MVP§$plusColour++§6] $name"
			"MVP_PLUS" -> "§b[MVP§$plusColour+§b] $name"
			"MVP" -> "§b[MVP] $name"
			"VIP_PLUS" -> "§a[VIP§6+§a] $name"
			"VIP" -> "§a[VIP] $name"
			"YOUTUBER" -> "§c[§fYOUTUBE§c] $name"
			"ADMIN" -> "§c[ADMIN] $name"
			"GAME_MASTER" -> "§2[GM] $name"
			else -> "§7$name"
		}
	}

	private fun colourCode(name: String) = when (name) {
		"BLACK" -> "0"; "DARK_BLUE" -> "1"; "DARK_GREEN" -> "2"; "DARK_AQUA" -> "3"
		"DARK_RED" -> "4"; "DARK_PURPLE" -> "5"; "GOLD" -> "6"; "GRAY" -> "7"
		"DARK_GRAY" -> "8"; "BLUE" -> "9"; "GREEN" -> "a"; "AQUA" -> "b"
		"RED" -> "c"; "LIGHT_PURPLE" -> "d"; "YELLOW" -> "e"; "WHITE" -> "f"
		else -> null
	}

	data class ProfileItem(
		val catalogItemId: ItemId?,
		val count: Int,
		val displayName: String?,
		val lore: List<String>,

		val tag: CompoundTag?,
	) {

		@OptIn(ExpensiveItemCacheApi::class, NeedsComponents::class)
		fun toItemStack(): ItemStack {
			var stack = catalogItemId?.let { id ->
				if (RepoManager.getCoreItem(id) != null) SBItemStack(id, count).asImmutableItemStack().current().copy()
				else null
			} ?: ItemStack(Items.PAPER)
			stack.count = count.coerceIn(1, 99)
			val tag = tag ?: return stack
			val extra = tag.getCompound("ExtraAttributes").getOrNull()
			val hasEnchants = tag.getList("ench").getOrNull()?.isEmpty() == false ||
				extra?.getCompound("enchantments")?.getOrNull()?.isEmpty == false
			if (hasEnchants) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
			tag.getCompound("display").getOrNull()?.getInt("color")?.getOrNull()?.let {
				stack.set(DataComponents.DYED_COLOR, DyedItemColor(it))
			}
			val skullOwner = tag.getCompound("SkullOwner").getOrNull()
			val texture = skullOwner?.getCompound("Properties")?.getOrNull()
				?.getList("textures")?.getOrNull()?.firstOrNull()
				?.let { it as? CompoundTag }?.getString("Value")?.getOrNull()
			if (texture != null) {
				if (stack.item != Items.PLAYER_HEAD) stack = ItemStack(Items.PLAYER_HEAD, stack.count)
				val uuid = skullOwner.getString("Id")?.getOrNull()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
					?: placeholderHeadUuid
				stack.applyEncodedSkin(uuid, texture)
			}
			return stack
		}
	}

	fun decodeInventory(blob: JsonElement?): List<ProfileItem?> {
		val data = (blob as? JsonObject)?.get("data").str() ?: return listOf()
		if (data.isEmpty()) return listOf()
		return try {
			val nbt = NbtIo.readCompressed(
				ByteArrayInputStream(Base64.getDecoder().decode(data)),
				NbtAccounter.create(64L * 1024 * 1024)
			)
			val list = nbt.getList("i").getOrNull() ?: return listOf()
			list.map { el ->
				val tag = el as? CompoundTag ?: return@map null
				if (tag.isEmpty) return@map null
				val extra = tag.getCompound("tag").getOrNull()
				val customDataTag = extra?.getCompound("ExtraAttributes")?.getOrNull()
				val display = extra?.getCompound("display")?.getOrNull()
				ProfileItem(
					catalogItemId = customDataTag?.getString("id")?.getOrNull()?.let { ItemId(it) },
					count = tag.getByte("Count").getOrNull()?.toInt() ?: 1,
					displayName = display?.getString("Name")?.getOrNull(),
					lore = display?.getList("Lore")?.getOrNull()?.mapNotNull { it.asString().getOrNull() } ?: listOf(),
					tag = extra,
				)
			}
		} catch (e: Exception) {
			Apex.logger.warn("Could not decode profile viewer inventory", e)
			listOf()
		}
	}
}

fun JsonObject.allProfileItems(): List<ProfileViewer.ProfileItem> {
	val inv = obj("inventory") ?: return listOf()
	val blobs = listOfNotNull(
		inv["inv_contents"], inv["inv_armor"], inv["equipment_contents"], inv["ender_chest_contents"],
		inv["wardrobe_contents"], inv.path("bag_contents", "talisman_bag"),
	) + (inv.obj("backpack_contents")?.values ?: listOf())
	return blobs.flatMap { ProfileViewer.decodeInventory(it) }.filterNotNull()
}

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): List<JsonElement> = (this[key] as? kotlinx.serialization.json.JsonArray) ?: listOf()
internal fun JsonObject.path(vararg keys: String): JsonElement? {
	var current: JsonElement? = this
	for (key in keys) current = (current as? JsonObject)?.get(key) ?: return null
	return current
}

internal fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.doubleOrNull
internal fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.longOrNull ?: this.num()?.toLong()
internal fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull
