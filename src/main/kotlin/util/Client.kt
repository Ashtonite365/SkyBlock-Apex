package dev.ashtonite365.apex.util

import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.util.mc.LenientRegistryOps
import io.github.moulberry.repo.data.Coordinate
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import java.net.URI
import com.mojang.blaze3d.Blaze3D
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import net.minecraft.client.KeyboardHandler
import net.minecraft.client.Minecraft
import net.minecraft.client.Options
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.Hud
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.client.sounds.SoundManager
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.data.registries.VanillaRegistries
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import net.minecraft.resources.Identifier
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceKey
import net.minecraft.server.packs.resources.ReloadableResourceManager
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

object Client {

	inline val game: Minecraft get() = Minecraft.getInstance()
	inline val resources: ReloadableResourceManager get() = game.resourceManager as ReloadableResourceManager
	inline val levelRenderer: LevelRenderer get() = game.levelRenderer
	inline val gameRenderer: GameRenderer get() = game.gameRenderer
	inline val textures: TextureManager get() = game.textureManager
	inline val sounds: SoundManager get() = game.soundManager
	inline val keyboardHandler: KeyboardHandler get() = game.keyboardHandler
	inline val options: Options get() = game.options
	inline val window get() = game.window
	inline val font: Font get() = game.font
	inline val gui: Gui get() = game.gui
	inline val hud: Hud get() = gui.hud
	inline val gameMode get() = game.gameMode
	inline val cameraEntity: Entity? get() = game.cameraEntity

	inline val player: LocalPlayer? get() = TestUtil.unlessTesting { game.player }
	inline val level: ClientLevel? get() = TestUtil.unlessTesting { game.level }
	inline val connection: ClientPacketListener? get() = player?.connection
	inline val mainHandStack: ItemStack get() = player?.mainHandItem ?: ItemStack.EMPTY

	inline val playerName: String get() = player?.name?.bareText ?: game.user.name

	private var rememberedLevel: Level? = null
	val lastLevel: Level?
		get() {
			level?.let { rememberedLevel = it }
			return rememberedLevel
		}

	var tickCounter = 0

	var screen: Screen?
		get() = TestUtil.unlessTesting { gui.screen() }
		set(next) { gui.setScreen(next) }
	inline val containerScreen: AbstractContainerScreen<*>? get() = screen as? AbstractContainerScreen<*>

	val screenTitle: String? get() = screen?.title?.string?.trim()
	val moulConfigContext: GuiContext? get() = when (val open = screen) {
		is MoulConfigScreenComponent -> open.guiContext
		else -> null
	}

	inline val levelRegistries: HolderLookup.Provider? get() = level?.registryAccess()

	val builtinRegistries: HolderLookup.Provider by lazy { VanillaRegistries.createWorldLookup() }
	val builtinNbtOps: RegistryOps<Tag> by lazy { RegistryOps.create(NbtOps.INSTANCE, builtinRegistries) }
	inline val registries: HolderLookup.Provider get() = levelRegistries ?: builtinRegistries
	val registryNbtOps: LenientRegistryOps<Tag> get() = LenientRegistryOps(NbtOps.INSTANCE, registries)
	val builtinItems: HolderLookup.RegistryLookup<Item> by lazy { builtinRegistries.lookupOrThrow(Registries.ITEM) }

	fun <T : Any> registryValue(registry: ResourceKey<out Registry<T>>, identifier: Identifier): T? {
		val key: ResourceKey<T> = ResourceKey.create(registry, identifier)
		return registryValue(key)
	}

	fun <T : Any> registryValue(registryKey: ResourceKey<T>): T? {
		val table = registries.lookupOrThrow(registryKey.registryKey())
		val holder = table.get(registryKey).orElse(null) ?: return null
		return holder.value()
	}

	private val pendingChat: Queue<Component> = ConcurrentLinkedQueue()
	private val pendingTasks: Queue<() -> Unit> = ConcurrentLinkedQueue()

	private val canShowChatNow: Boolean get() = level != null && game.isSameThread

	fun showChat(text: Component) {
		Apex.logger.info("CHAT: ${text.string}")
		if (!canShowChatNow) {
			pendingChat.add(text)
			return
		}
		hud.getChat().addClientSystemMessage(text)
	}

	fun chatAsPlayer(text: String) {
		connection?.sendChat(text)
	}

	fun runCommand(command: String) {
		assert(!command.startsWith("/")) { "Server commands have an implied /" }
		connection?.sendCommand(command)
	}

	@Deprecated("Use checked method instead", replaceWith = ReplaceWith("runCommand(command)"))
	fun runCommandUnchecked(line: String) {
		connection?.send(ServerboundChatCommandPacket(line))
	}

	fun onClientThread(block: () -> Unit) {
		if (game.isSameThread) block() else game.execute { block() }
	}

	fun onNextTick(function: () -> Unit) {
		pendingTasks.add(function)
	}

	private fun flushPending() {
		while (true) (pendingTasks.poll() ?: break).invoke()
		if (!canShowChatNow) return
		generateSequence { pendingChat.poll() }.forEach { hud.getChat().addClientSystemMessage(it) }
	}

	init {
		TickEvent.subscribe("MC:push") {
			tickCounter++
			flushPending()
		}
		WorldReadyEvent.subscribe("MC:ready") { flushPending() }
	}

	fun openLink(address: String) {
		Blaze3D.openUri(URI(address))
	}
}

val Coordinate.blockPos: BlockPos get() = BlockPos(x, y, z)

fun BlockPos.center(): Vec3 = Vec3.atCenterOf(this)
