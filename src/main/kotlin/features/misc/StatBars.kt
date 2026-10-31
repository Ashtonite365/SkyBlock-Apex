// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.misc

import dev.ashtonite365.apex.gui.hud.HudEditorKeys
import dev.ashtonite365.apex.gui.hud.HudTextScale
import dev.ashtonite365.apex.gui.config.HudAnchor
import org.joml.Vector2i
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ActionBarEvent
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.gui.config.HudMeta
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.util.getLegacyFormatString
import dev.ashtonite365.apex.util.hud.BarStyle
import dev.ashtonite365.apex.util.hud.BarStyleChoice
import dev.ashtonite365.apex.util.hud.HudStyle
import dev.ashtonite365.apex.util.hud.StylePack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

enum class BarColour(private val id: String, val argb: Int?) : StringRepresentable {
	DEFAULT("default", null),
	RED("red", 0xFFE03030.toInt()),
	ORANGE("orange", 0xFFFF8C1A.toInt()),
	GOLD("gold", 0xFFFFC300.toInt()),
	GREEN("green", 0xFF4CD04C.toInt()),
	LIME("lime", 0xFF9CFF3A.toInt()),
	AQUA("aqua", 0xFF30D0E0.toInt()),
	BLUE("blue", 0xFF3070F0.toInt()),
	PURPLE("purple", 0xFFA050F0.toInt()),
	PINK("pink", 0xFFFF70C0.toInt()),
	WHITE("white", 0xFFF0F0F0.toInt()),
	;

	fun resolve(default: Int) = argb ?: default
	override fun getSerializedName() = id
}

object StatBars {

	const val MIN_W = 40
	const val MAX_W = 300
	const val MIN_H = 7
	const val MAX_H = 30
	const val DEF_H = 11

	@Config
	object TConfig : ManagedConfig("stat-bars", Category.HUD) {
		val enabled by toggle("enabled") { true }
		val showHealth by toggle("show-health") { true }
		val showMana by toggle("show-mana") { true }
		val showDefence by toggle("show-defence") { true }
		val showXp by toggle("show-xp") { true }
		val showVitality by toggle("show-vitality") { true }
		val showIcons by toggle("show-icons") { false }
		val showNumbers by toggle("show-numbers") { true }
		val absorptionTint by toggle("absorption-tint") { true }
		val regenAnimation by toggle("regen-animation") { true }
		val healthStyle by choice("health-style") { BarStyleChoice.CLASSIC }
		val healthColour by choice("health-colour") { BarColour.DEFAULT }
		val manaStyle by choice("mana-style") { BarStyleChoice.CLASSIC }
		val manaColour by choice("mana-colour") { BarColour.DEFAULT }
		val xpStyle by choice("xp-style") { BarStyleChoice.CLASSIC }
		val xpColour by choice("xp-colour") { BarColour.DEFAULT }
		val vitalityStyle by choice("vitality-style") { BarStyleChoice.CLASSIC }
		val vitalityColour by choice("vitality-colour") { BarColour.DEFAULT }

		val hud by position("hud", 100, 16, HudAnchor.HOTBAR) { Vector2i(-51, -2) }
		val manaHud by position("mana-hud", 100, 16, HudAnchor.HOTBAR) { Vector2i(51, -2) }
		val xpHud by position("xp-hud", 100, 16, HudAnchor.HOTBAR) { Vector2i(51, -20) }
		val defenceHud by position("defence-hud", 80, 10, HudAnchor.HOTBAR) { Vector2i(0, -38) }
		val vitalityHud by position("vitality-hud", 100, 16, HudAnchor.HOTBAR) { Vector2i(-51, -20) }
		val healthWidth by integerInput("health-width", MIN_W, MAX_W) { 90 }
		val healthThickness by integerInput("health-thickness", MIN_H, MAX_H) { DEF_H }
		val manaWidth by integerInput("mana-width", MIN_W, MAX_W) { 90 }
		val manaThickness by integerInput("mana-thickness", MIN_H, MAX_H) { DEF_H }
		val xpWidth by integerInput("xp-width", MIN_W, MAX_W) { 90 }
		val xpThickness by integerInput("xp-thickness", MIN_H, MAX_H) { DEF_H }
		val vitalityWidth by integerInput("vitality-width", MIN_W, MAX_W) { 90 }
		val vitalityThickness by integerInput("vitality-thickness", MIN_H, MAX_H) { DEF_H }
		val widerKey by keyBinding("edit-wider") { com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT }
		val narrowerKey by keyBinding("edit-narrower") { com.mojang.blaze3d.platform.InputConstants.KEY_LEFT }
		val thickerKey by keyBinding("edit-thicker") { com.mojang.blaze3d.platform.InputConstants.KEY_UP }
		val thinnerKey by keyBinding("edit-thinner") { com.mojang.blaze3d.platform.InputConstants.KEY_DOWN }
		val cycleStyleKey by keyBinding("edit-cycle-style") { com.mojang.blaze3d.platform.InputConstants.KEY_LBRACKET }
		val cycleColourKey by keyBinding("edit-cycle-colour") { com.mojang.blaze3d.platform.InputConstants.KEY_RBRACKET }
		val openEditorKey by keyBindingWithDefaultUnbound("open-editor")
	}

	@Config
	object VanillaConfig : ManagedConfig("stat-bars-vanilla", Category.HUD) {
		val hideHearts by toggle("hide-hearts") { true }
		val hideHunger by toggle("hide-hunger") { true }
		val hideXp by toggle("hide-xp") { true }
		val hideArmour by toggle("hide-armour") { true }
	}

	@Config
	object ActionBarConfig : ManagedConfig("stat-bars-action-bar", Category.HUD) {
		val hideHealth by toggle("hide-health") { true }
		val hideDefence by toggle("hide-defence") { true }
		val hideMana by toggle("hide-mana") { true }

		override fun migrate(saved: kotlinx.serialization.json.JsonObject) = dropRetired(saved)
	}

	val RETIRED_ACTION_BAR_KEYS = setOf("hide-vitality")

	fun dropRetired(saved: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject =
		if (RETIRED_ACTION_BAR_KEYS.none { it in saved }) saved
		else kotlinx.serialization.json.JsonObject(saved.filterKeys { it !in RETIRED_ACTION_BAR_KEYS })

	data class Stats(
		var health: Int? = null, var maxHealth: Int? = null,
		var mana: Int? = null, var maxMana: Int? = null,
		var defence: Int? = null,
		var vitality: Int? = null, var maxVitality: Int? = null,
	)

	var stats = Stats()
		private set
	private var lastUpdate = TimeMark.farPast()
	private var lastHealth: Int? = null
	private var regenStart = TimeMark.farPast()

	fun parse(text: String): Stats {
		val p = ActionBarStats.parse(text)
		return Stats(p.health, p.maxHealth, p.mana, p.maxMana, p.defence)
	}

	data class StripFlags(val health: Boolean, val mana: Boolean, val defence: Boolean, val vitality: Boolean)

	fun stripFlags() = StripFlags(
		health = ActionBarConfig.hideHealth && TConfig.showHealth,
		mana = ActionBarConfig.hideMana && TConfig.showMana,
		defence = ActionBarConfig.hideDefence && TConfig.showDefence,
		vitality = TConfig.showVitality)

	fun stripSegments(legacy: String, parsed: ActionBarStats.Parsed = ActionBarStats.parse(legacy), flags: StripFlags = stripFlags()): String =
		ActionBarStats.strip(legacy, parsed, health = flags.health, mana = flags.mana, defence = flags.defence, vitality = flags.vitality)

	@Subscribe
	fun onActionBar(event: ActionBarEvent) {
		val legacy = event.text.getLegacyFormatString()
		val parsed = ActionBarStats.parse(legacy)
		vitDebugActionBar(legacy, parsed)
		if (!TConfig.enabled) return
		if (parsed.health != null) {
			val prev = lastHealth
			if (prev != null && parsed.health > prev) regenStart = TimeMark.now()
			lastHealth = parsed.health
			stats.health = parsed.health; stats.maxHealth = parsed.maxHealth
		}
		if (parsed.mana != null) { stats.mana = parsed.mana; stats.maxMana = parsed.maxMana }
		if (parsed.defence != null) stats.defence = parsed.defence
		if (parsed.vitality != null) {

			CombatStatsVitality.changeLog(stats.vitality, stats.maxVitality, parsed.vitality, parsed.maxVitality)?.let { dev.ashtonite365.apex.Apex.logger.info(it) }
			stats.vitality = parsed.vitality; stats.maxVitality = parsed.maxVitality ?: CombatStatsVitality.maxFallback()
			lastActionBarVitality = TimeMark.now()
		}
		if (!parsed.any) return
		lastUpdate = TimeMark.now()
		if (!SkyBlockSession.inSkyBlock && fakeActionBar == null) return
		val stripped = stripSegments(legacy, parsed)
		if (stripped == legacy) return
		if (!ActionBarStats.hasVisibleText(stripped)) event.cancel()
		else event.text = Component.literal(stripped)
	}

	fun isActive(): Boolean = TConfig.enabled && (SkyBlockSession.inSkyBlock || fakeActionBar != null) && lastUpdate.passedTime() < 10.seconds

	private val fakeActionBar: String? = System.getenv("APEX_FAKE_ACTIONBAR")?.takeIf { it.isNotBlank() }
	private val openEditorAfter: Int? = System.getenv("APEX_OPEN_EDITOR")?.toIntOrNull()

	private val screenshotAt: Int? = System.getenv("APEX_SCREENSHOT_AT")?.toIntOrNull()
	private var worldTicks = 0

	@Subscribe
	fun onDebugTick(event: TickEvent) {
		if (fakeActionBar == null && openEditorAfter == null && screenshotAt == null) return
		val conn = Client.game.connection ?: return
		if (Client.player == null) return
		worldTicks++

		if (fakeActionBar != null && worldTicks % 20 == 0) {
			if (worldTicks % 40 == 0)
				conn.setActionBarText(net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(Component.literal(fakeActionBar)))
			else
				conn.handleSystemChat(net.minecraft.network.protocol.game.ClientboundSystemChatPacket(Component.literal(fakeActionBar), true))
		}
		if (openEditorAfter != null && worldTicks == openEditorAfter * 20) openEditor()
		if (screenshotAt != null && worldTicks == screenshotAt * 20)
			net.minecraft.client.Screenshot.grab(Client.game.gameDirectory, Client.gameRenderer.mainRenderTarget()) {}
	}

	@JvmStatic fun hideVanillaHearts() = isActive() && TConfig.showHealth && VanillaConfig.hideHearts
	@JvmStatic fun hideVanillaHunger() = isActive() && VanillaConfig.hideHunger
	@JvmStatic fun hideVanillaXp() = isActive() && TConfig.showXp && VanillaConfig.hideXp
	@JvmStatic fun hideVanillaArmour() = isActive() && TConfig.showDefence && VanillaConfig.hideArmour

	private class Element(
		val id: String,
		val label: String,
		val hud: () -> HudMeta,
		val style: ManagedOption<BarStyleChoice>?,
		val colour: ManagedOption<BarColour>?,
		val width: ManagedOption<Int>? = null,
		val thickness: ManagedOption<Int>? = null,
	)

	private val elements = listOf(
		Element("health", "Health", { TConfig.hud }, TConfig.option("health-style"), TConfig.option("health-colour"), TConfig.option("health-width"), TConfig.option("health-thickness")),
		Element("mana", "Mana", { TConfig.manaHud }, TConfig.option("mana-style"), TConfig.option("mana-colour"), TConfig.option("mana-width"), TConfig.option("mana-thickness")),
		Element("xp", "XP", { TConfig.xpHud }, TConfig.option("xp-style"), TConfig.option("xp-colour"), TConfig.option("xp-width"), TConfig.option("xp-thickness")),
		Element("defence", "Defence", { TConfig.defenceHud }, null, null),
		Element("vitality", "Vitality", { TConfig.vitalityHud }, TConfig.option("vitality-style"), TConfig.option("vitality-colour"), TConfig.option("vitality-width"), TConfig.option("vitality-thickness")),
	)

	init {
		for (e in elements) {
			val key = "apex.config.stat-bars." + e.hudKey

			HudMeta.hiddenEditorLabels += key
			if (e.width != null && e.thickness != null) HudMeta.sizeOverrides[key] = { Vector2i(e.width.value, e.thickness.value) }
			else HudMeta.sizeOverrides[key] = { Vector2i(Client.font.width("0000❈ Defense"), 9) }
		}
	}

	private val Element.hudKey get() = when (id) {
		"health" -> "hud"; else -> "$id-hud"
	}

	@Suppress("UNCHECKED_CAST")
	private fun <T : Any> ManagedConfig.option(name: String): ManagedOption<T> = allOptions[name] as ManagedOption<T>

	private fun pulse(): Float {
		if (!TConfig.regenAnimation) return 0F
		val t = regenStart.passedTime().inWholeMilliseconds
		if (t > 700) return 0F
		return sin(t / 700.0 * PI).toFloat()
	}

	private fun lighten(argb: Int, amount: Float): Int {
		if (amount <= 0F) return argb
		fun ch(shift: Int): Int { val c = argb ushr shift and 0xFF; return (c + (255 - c) * amount * 0.6F).toInt().coerceIn(0, 255) shl shift }
		return (argb and 0xFF000000.toInt()) or ch(16) or ch(8) or ch(0)
	}

	private fun drawBarElement(ctx: GuiGraphicsExtractor, meta: HudMeta, e: Element, label: String?, fraction: Float,
	                           colour: Int, overlayFraction: Float = 0F, overlayColour: Int = 0, regen: Float = 0F) {
		val style = styleOf(e)
		val w = e.width?.value ?: 90
		val h = e.thickness?.value ?: DEF_H
		ctx.pose().pushMatrix()
		meta.applyTo(ctx.pose())
		StylePack.drawBar(ctx, 0, 0, w, h, fraction, lighten(colour, regen), style)
		if (overlayFraction > 0F) {
			val i = if (style.codeDrawn) 1 else style.fillInset
			val ow = ((w - 2 * i) * overlayFraction.coerceIn(0F, 1F)).toInt()
			if (ow > 0) ctx.fill(i, i, i + ow, h - i, overlayColour)
		}
		if (regen > 0F) {
			val filled = (w * fraction.coerceIn(0F, 1F)).toInt()
			val x = (filled * (1F - regen)).toInt().coerceIn(0, (filled - 3).coerceAtLeast(0))
			if (filled > 3) ctx.fill(x, 0, x + 3, h, ((regen * 0x90).toInt() shl 24) or 0xFFFFFF)
		}
		ctx.pose().popMatrix()
		if (TConfig.showNumbers && label != null) {

			val fit = meta.textFit()
			val local = HudTextScale.barLabel(w * meta.scaleX / fit.scale, h * meta.scaleY / fit.scale, Client.font.width(label))
			val p = meta.topLeft
			ctx.pose().pushMatrix()
			ctx.pose().translate(p.x() + w * meta.scaleX / 2F, p.y() + h * meta.scaleY / 2F)
			ctx.pose().scale(fit.scale * local, fit.scale * local)
			ctx.text(Client.font, label, -Client.font.width(label) / 2, -4, -1, true)
			ctx.pose().popMatrix()
		}
	}

	private fun drawTextElement(ctx: GuiGraphicsExtractor, meta: HudMeta, text: String) {
		ctx.pose().pushMatrix()
		meta.applyTextTransformations(ctx.pose(), Client.font.width(text).toFloat(), Client.font.lineHeight.toFloat())
		ctx.text(Client.font, text, 0, 0, -1, true)
		ctx.pose().popMatrix()
	}

	private fun styleOf(e: Element) = e.style!!.value.let { if (it == BarStyleChoice.GLOBAL) BarStyle.CLASSIC else it.resolved }
	private fun colourOf(e: Element, default: Int) = e.colour!!.value.resolve(default)

	private fun drawAll(ctx: GuiGraphicsExtractor, preview: Boolean) {
		val s = if (preview && stats.health == null) Stats(1234, 1500, 800, 900, 250) else stats
		val (eHealth, eMana, eXp, eDef) = elements
		val eVit = elements[4]
		val hp = s.health; val maxHp = s.maxHealth
		if (TConfig.showHealth && hp != null && maxHp != null && maxHp > 0) {
			val absorb = (hp - maxHp).coerceAtLeast(0)
			val absorbTint = TConfig.absorptionTint && absorb > 0
			val base = colourOf(eHealth, 0xFFD42A2A.toInt())
			val label = if (absorbTint) "${maxHp}§6+$absorb§f/$maxHp❤" else "$hp/$maxHp❤"
			drawBarElement(ctx, eHealth.hud(), eHealth, label, (hp.toFloat() / maxHp).coerceIn(0F, 1F), base,
				overlayFraction = if (absorbTint) absorb.toFloat() / maxHp else 0F, overlayColour = 0xC0FFC300.toInt(),
				regen = pulse())
		}
		val mana = s.mana; val maxMana = s.maxMana
		if (TConfig.showMana && mana != null && maxMana != null && maxMana > 0)
			drawBarElement(ctx, eMana.hud(), eMana, "$mana/$maxMana✎", (mana.toFloat() / maxMana).coerceIn(0F, 1F),
				colourOf(eMana, 0xFF2F6FE8.toInt()))
		val player = Client.player
		if (TConfig.showXp && player != null)
			drawBarElement(ctx, eXp.hud(), eXp, dev.ashtonite365.apex.features.skills.SkillXpBar.active()
				?.let { k -> dev.ashtonite365.apex.features.skills.SkillXpBar.barText(k) } ?: "Level ${player.experienceLevel}",
				dev.ashtonite365.apex.features.skills.SkillXpBar.progress(player.experienceProgress).coerceIn(0F, 1F),
				colourOf(eXp, 0xFF4CC232.toInt()))
		if (TConfig.showDefence && s.defence != null)
			drawTextElement(ctx, eDef.hud(), "§a${s.defence}❈ Defense")
		val vit = if (preview && s.vitality == null) 800 else s.vitality
		val maxVit = if (preview && s.maxVitality == null) 1000 else s.maxVitality
		if (TConfig.showVitality && vit != null && maxVit != null && maxVit > 0)
			drawBarElement(ctx, eVit.hud(), eVit, vitalityLabel(vit, maxVit), vitalityFraction(vit, maxVit), colourOf(eVit, 0xFFB01E1E.toInt()))
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!isActive()) return
		drawAll(event.context, preview = false)
	}

	private fun isEditor(s: net.minecraft.client.gui.screens.Screen?) = s is dev.ashtonite365.apex.gui.hud.editor.HudEditorScreen
	private var hovered: Element? = null
	private var styleWasDown = false
	private var colourWasDown = false
	private var resizeWasDown = listOf(false, false, false, false)

	fun vitalityLabel(vit: Int, maxVit: Int) = "$vit/$maxVit${ActionBarStats.GLYPH_VITALITY}"
	fun vitalityFraction(vit: Int, maxVit: Int) = (vit.toFloat() / maxVit).coerceIn(0F, 1F)
	fun elementIds(): List<String> = elements.map { it.id }

	fun allHuds(): List<HudMeta> = elements.map { it.hud() }

	fun openEditor() {
		ScreenUtil.setScreenLater(editorScreen(Client.screen))
	}

	fun editorScreen(last: net.minecraft.client.gui.screens.Screen?): net.minecraft.client.gui.screens.Screen =
		dev.ashtonite365.apex.gui.hud.editor.HudEditors.editor(last)

	@Subscribe
	fun onCommands(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.HUD, "edit", "Move and resize every enabled HUD element (also Right Shift); restyle the HUD bars") {
			onRun { openEditor() }
		}
	}

	@Subscribe
	fun onEditorRender(event: ScreenRenderPostEvent) {
		if (!isEditor(event.screen)) return
		if (TConfig.enabled) drawAll(event.drawContext, preview = true)
		hovered = elements.filter { it.hud().isEnabled }.firstOrNull { e ->
			val m = e.hud()
			val p = m.topLeft
			event.mouseX in p.x()..(p.x() + m.drawnWidth) && event.mouseY in p.y()..(p.y() + m.drawnHeight)
		}
		val hint = hovered?.let { e ->
			if (e.style == null) "§e${e.label}"
			else "§e${e.label}§7: style §f${e.style.value.serializedName}§7, colour §f${e.colour!!.value.serializedName}§7  (${keyName(TConfig.cycleStyleKey)} style, ${keyName(TConfig.cycleColourKey)} colour, ${resizeHint()} ${e.width?.value}x${e.thickness?.value})"
		} ?: "§7Drag to move, scroll or drag a corner to resize. Hover a bar: ${keyName(TConfig.cycleStyleKey)} style, ${keyName(TConfig.cycleColourKey)} colour, arrows move 1 px"
		event.drawContext.text(Client.font, hint, 4, event.screen.height - 12, -1, true)
	}

	private fun keyName(k: SavedKeyBinding) = k.format().string

	private fun resizeHint(): String {
		val keys = listOf(TConfig.widerKey, TConfig.narrowerKey, TConfig.thickerKey, TConfig.thinnerKey)
			.filter { !HudEditorKeys.isArrow(it) && it.button != dev.ashtonite365.apex.keybindings.GenericInputButton.unbound() }
		return if (keys.isEmpty()) "arrows move 1 px, corner handles resize" else "${keys.joinToString("/") { keyName(it) }} width/thickness"
	}

	private inline fun <reified E : Enum<E>> ManagedOption<E>.cycle() {
		val all = enumValues<E>()
		set(all[(value.ordinal + 1) % all.size])
		element.markDirty()
	}

	private val vitDebugSeen = HashSet<String>()
	private val vitDebugPair = Regex("(\\d[\\d,]*) */ *(\\d[\\d,]*) *(.)?")
	private const val VIT_KNOWN_SYMBOLS = "❤♥✎❈❂♨\uE028"

	private fun vitEscape(s: String) = buildString { for (c in s) if (c.code in 0x20..0x7E) append(c) else append("\\u%04X".format(c.code)) }

	private fun vitDebug(source: String, legacy: String) {
		if (vitDebugSeen.size >= 200 || !vitDebugSeen.add("$source|$legacy")) return
		dev.ashtonite365.apex.Apex.logger.debug("[ApexVitDebug] source=$source raw=$legacy escaped=${vitEscape(legacy)}")
	}

	private fun vitDebugActionBar(legacy: String, parsed: ActionBarStats.Parsed) {
		if (legacy.contains("vital", ignoreCase = true)) return vitDebug("actionbar", legacy)
		val plain = legacy.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "")
		for (m in vitDebugPair.findAll(plain)) {
			val a = m.groupValues[1].replace(",", "").toIntOrNull()
			val b = m.groupValues[2].replace(",", "").toIntOrNull()
			val isHealth = a == parsed.health && b == parsed.maxHealth
			val isMana = a == parsed.mana && b == parsed.maxMana
			val sym = m.groupValues[3]
			val oddSym = sym.isNotEmpty() && !sym[0].isLetterOrDigit() && !sym[0].isWhitespace() && sym[0] !in VIT_KNOWN_SYMBOLS
			if ((!isHealth && !isMana) || oddSym) return vitDebug("actionbar", legacy)
		}
	}

	private var lastActionBarVitality = TimeMark.farPast()

	@Subscribe
	fun onVitalityTick(event: TickEvent) {
		if (!TConfig.enabled || !TConfig.showVitality) return
		if (lastActionBarVitality.passedTime() < kotlin.time.Duration.parse("3s")) return
		val sb = dev.ashtonite365.apex.util.ScoreboardUtil.scoreboardLines.map { it.getLegacyFormatString() }
		val tab = runCatching { dev.ashtonite365.apex.util.mc.TabListReader.currentSnapshot.body }.getOrElse { listOf() }.map { it.getLegacyFormatString() }
		for (l in sb) if (l.contains("vital", ignoreCase = true)) vitDebug("scoreboard", l)
		for (l in tab) if (l.contains("vital", ignoreCase = true)) vitDebug("tablist", l)
		val lines = sb.asSequence() + tab.asSequence()
		val found = lines.firstNotNullOfOrNull { ActionBarStats.parseVitality(it) } ?: return
		stats.vitality = found.first; stats.maxVitality = found.second
	}

	@Subscribe
	fun onVitalityMaxFallback(event: TickEvent) {
		if (!TConfig.enabled || !TConfig.showVitality || stats.maxVitality != null) return
		stats.maxVitality = CombatStatsVitality.maxFallback()
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		val screen = Client.screen
		if (screen == null && TConfig.openEditorKey.isPressed() && Client.player != null) openEditor()
		if (!isEditor(screen)) { styleWasDown = false; colourWasDown = false; hovered = null; return }
		val styleDown = TConfig.cycleStyleKey.isPressed()
		val colourDown = TConfig.cycleColourKey.isPressed()
		val e = hovered
		if (e?.style != null) {
			if (styleDown && !styleWasDown) e.style.cycle()
			if (colourDown && !colourWasDown) e.colour!!.cycle()
		}
		styleWasDown = styleDown; colourWasDown = colourDown
		val keys = listOf(TConfig.widerKey, TConfig.narrowerKey, TConfig.thickerKey, TConfig.thinnerKey)
		val resize = keys.map { it.isPressed() }

		val fire = HudEditorKeys.resizeFires(resize, resizeWasDown, keys.map { HudEditorKeys.isArrow(it) })
		if (e?.width != null && e.thickness != null) {
			fun ManagedOption<Int>.bump(d: Int, min: Int, max: Int) { set((value + d).coerceIn(min, max)); element.markDirty() }
			if (fire[0]) e.width.bump(+5, MIN_W, MAX_W)
			if (fire[1]) e.width.bump(-5, MIN_W, MAX_W)
			if (fire[2]) e.thickness.bump(+1, MIN_H, MAX_H)
			if (fire[3]) e.thickness.bump(-1, MIN_H, MAX_H)
		}
		resizeWasDown = resize
	}

}
