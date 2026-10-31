
package dev.ashtonite365.apex.features.debug

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.ManagedConfig

object FrameProfilerHooks {
	private var screenStartedAt = 0L

	@JvmStatic
	fun screenStart() {
		screenStartedAt = if (FrameProfiler.enabled) System.nanoTime() else 0L
	}

	@JvmStatic
	fun screenEnd(ctx: GuiGraphicsExtractor) {
		if (!FrameProfiler.enabled) return
		val now = System.nanoTime()
		if (screenStartedAt != 0L) FrameProfiler.add(FrameProfiler.Phase.SCREEN, now - screenStartedAt)
		FrameProfiler.endFrame(now)
		val font = Client.game.font
		var y = 2
		for (line in FrameProfiler.lines()) {
			ctx.text(font, line, 2, y, 0xFFFFFF55.toInt(), true)
			y += font.lineHeight + 1
		}
	}

	fun dump(): List<String> = FrameProfiler.lines().also { lines -> lines.forEach { Apex.logger.info("[ApexPerf] $it") } }

	@Subscribe
	fun onCommands(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.GENERAL, "perf", "Debug frame-cost profiler (off by default, this session only): /apex perf on | off | dump | reset") {
			onRun { source.sendFeedback(Component.literal("§e[Apex] Frame profiler is " + (if (FrameProfiler.enabled) "on" else "off") + ". Use /apex perf on | off | dump | reset.")) }
			onLiteral("on") { onRun { FrameProfiler.reset(); FrameProfiler.enabled = true; source.sendFeedback(Component.literal("§e[Apex] Frame profiler on: readout top-left of any open screen. /apex perf dump writes it to latest.log.")) } }
			onLiteral("off") { onRun { FrameProfiler.enabled = false; source.sendFeedback(Component.literal("§e[Apex] Frame profiler off.")) } }
			onLiteral("dump") { onRun { dump().forEach { source.sendFeedback(Component.literal("§7$it")) }; source.sendFeedback(Component.literal("§e[Apex] Also written to latest.log as [ApexPerf].")) } }
			onLiteral("reset") { onRun { FrameProfiler.reset(); source.sendFeedback(Component.literal("§e[Apex] Profiler averages reset.")) } }
		}
	}
}
