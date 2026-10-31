// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex

import com.google.gson.Gson
import com.mojang.brigadier.CommandDispatcher
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.json.decodeFromStream
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import net.fabricmc.loader.api.Version
import net.fabricmc.loader.api.metadata.ModMetadata
import net.minecraft.commands.CommandBuildContext
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import dev.ashtonite365.apex.commands.registerApexCommand
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.events.ClientStartedEvent
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.registration.registerApexEvents
import dev.ashtonite365.apex.features.misc.DownloadedHypixelPack
import dev.ashtonite365.apex.gui.config.storage.ApexConfigLoader
import dev.ashtonite365.apex.gui.hud.editor.HudEditors
import dev.ashtonite365.apex.impl.v1.ApexAPIImpl
import dev.ashtonite365.apex.modules.ModuleManager
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.LegacyDataMigration
import dev.ashtonite365.apex.util.mc.StartupStage
import dev.ashtonite365.apex.util.render.ApexRenderSystem
import dev.ashtonite365.apex.util.render.SidePanel
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.trail.TrailInstall

object Apex {
	const val MOD_ID = "apex"

	val DEBUG = System.getProperty("apex.debug") == "true"

	val DATA_DIR: Path = prepareDataDir(Path.of(".apex"))

	val logger: Logger = LogManager.getLogger("Apex")

	val modContainer: ModContainer by lazy { FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow() }
	private val ownMetadata: ModMetadata get() = modContainer.metadata
	val version: Version by lazy { ownMetadata.version }

	private const val LIBRARY_INDENT = "    "

	@OptIn(ExperimentalSerializationApi::class)
	val json: Json = flavour(null) {
		isLenient = true
		allowComments = true
		allowTrailingComma = true
		ignoreUnknownKeys = true
		encodeDefaults = true
		prettyPrint = DEBUG
		prettyPrintIndent = if (DEBUG) "\t" else LIBRARY_INDENT
	}

	@OptIn(ExperimentalSerializationApi::class)
	val twoSpaceJson: Json = flavour(json) {
		prettyPrint = true
		prettyPrintIndent = "  "
	}

	val gson = Gson()

	@OptIn(ExperimentalSerializationApi::class)
	val tightJson: Json = flavour(json) {
		prettyPrint = false
		prettyPrintIndent = LIBRARY_INDENT
		explicitNulls = false
	}

	val globalJob: Job = Job()

	val coroutineScope = CoroutineScope(
		SupervisorJob(globalJob) + CoroutineName("Apex") + CoroutineExceptionHandler { context, error ->
			val name = context[CoroutineName]?.name ?: "(unnamed)"
			logger.warn("Exception in a coroutine $name: ${error.message}", error)
		},
	)

	private fun prepareDataDir(dir: Path): Path {

		LegacyDataMigration.runOnce(log = { line -> LogManager.getLogger("Apex").info(line) })
		Files.createDirectories(dir)
		return dir
	}

	private fun flavour(base: Json?, setup: JsonBuilder.() -> Unit): Json =
		if (base == null) Json { setup() } else Json(base) { setup() }

	private fun onClientCommands(dispatcher: CommandDispatcher<FabricClientCommandSource>, context: CommandBuildContext) {
		registerApexCommand(dispatcher, context)
		CommandEvent.publish(CommandEvent(dispatcher, context, Client.connection?.commands))
	}

	private fun hookScreenRender() {
		ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
			runCatching { dev.ashtonite365.apex.features.menus.ApexInventoryButton.install(screen) }
			runCatching { dev.ashtonite365.apex.features.menus.VanillaStyleDropdown.install(screen) }
			ScreenEvents.remove(screen).register { _ -> dev.ashtonite365.apex.features.menus.style.ScreenTransitions.removed() }
			ScreenEvents.beforeExtract(screen).register { _, _, _, _, _ -> dev.ashtonite365.apex.features.debug.FrameProfilerHooks.screenStart() }

			val after = ScreenEvents.afterExtract(screen)
			after.addPhaseOrdering(dev.ashtonite365.apex.features.menus.DropdownLayer.PHASE, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE)
			after.register(dev.ashtonite365.apex.features.menus.DropdownLayer.PHASE, ScreenEvents.AfterExtract { shown, graphics, mouseX, mouseY, delta ->
				dev.ashtonite365.apex.features.menus.DropdownLayer.runApexPass({
					SidePanel.beginFrame(shown)
					ScreenRenderPostEvent.publish(ScreenRenderPostEvent(shown, mouseX, mouseY, delta, graphics))
					SidePanel.endFrame()
				}, {

					dev.ashtonite365.apex.features.menus.DropdownLayer.draw(shown, graphics, mouseX, mouseY)
				})
				dev.ashtonite365.apex.features.debug.FrameProfilerHooks.screenEnd(graphics)
			})
		}
	}

	private fun bridgeTicks() {
		var ticks = 0
		ClientTickEvents.END_CLIENT_TICK.register { TickEvent.publish(TickEvent(ticks++)) }
	}

	@JvmStatic
	fun onInitialize() {
		val shown = runCatching { version.friendlyString }.getOrDefault("?")
		logger.info("SkyBlock Apex $shown initialising")
	}

	private val clientBoot: List<BootStep> by lazy {
		listOf(
			BootStep("error trail") { TrailInstall.install(Path.of("logs", "apex-trail")) },
			BootStep("init level") { StartupStage.advanceTo(StartupStage.MC_INIT) },
			BootStep("feature subscriptions") { ModuleManager.boot() },
			BootStep("config") { ApexConfigLoader.loadConfig() },
			BootStep("modules") { ModuleManager.afterConfigLoad() },
			BootStep("tick bridge") { bridgeTicks() },
			BootStep("repo") { RepoManager.initialize() },
			BootStep("skyblock data") { SkyBlockSession.init() },
			BootStep("static data loop") { HypixelStaticData.spawnDataCollectionLoop() },
			BootStep("client commands") { ClientCommandRegistrationCallback.EVENT.register(::onClientCommands) },
			BootStep("hud editors") { HudEditors.install() },
			BootStep("started bridge") {
				ClientLifecycleEvents.CLIENT_STARTED.register { ClientStartedEvent.publish(ClientStartedEvent()) }
			},
			BootStep("stopping hook") {
				ClientLifecycleEvents.CLIENT_STOPPING.register {
					logger.info("Shutting down coroutines")
					globalJob.cancel()
				}
			},
			BootStep("generated event registrations") { registerApexEvents() },
			BootStep("api extensions") { ApexAPIImpl.loadExtensions() },
			BootStep("tooltip bridge") {
				ItemTooltipCallback.EVENT.register { stack, context, type, lines ->
					dev.ashtonite365.apex.features.debug.FrameProfiler.time(dev.ashtonite365.apex.features.debug.FrameProfiler.Phase.TOOLTIP) { ItemTooltipEvent.publish(ItemTooltipEvent(stack, context, type, lines)) }
				}
			},
			BootStep("screen render hook") { hookScreenRender() },
			BootStep("client init event") { ClientInitEvent.publish(ClientInitEvent()) },
			BootStep("downloaded hypixel pack") { DownloadedHypixelPack.start() },
			BootStep("render system") { ApexRenderSystem.install() },
		)
	}

	private class BootStep(val name: String, val run: () -> Unit)

	private fun step(name: String, run: () -> Unit) {
		try {
			run()
		} catch (failure: Throwable) {
			logger.error("Start-up step \"$name\" failed", failure)
		}
	}

	@JvmStatic
	fun onClientInitialize() {
		clientBoot.forEach { step(it.name, it.run) }
	}

	@JvmStatic
	fun identifier(location: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, location)

	@OptIn(ExperimentalSerializationApi::class)
	inline fun <reified T : Any> tryDecodeJsonFromStream(stream: InputStream): Result<T> =
		runCatching { json.decodeFromStream<T>(stream) }
}
