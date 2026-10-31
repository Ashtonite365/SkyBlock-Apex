// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

import dev.ashtonite365.apex.util.ApexClickDelay

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.customgui.customGui
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.repoItemId

class MenuChain(
	val steps: List<Step>,
	val finish: Finish = Finish.AfterLastClick,
	val timeoutMs: Long = 5_000L,
	startMs: Long = 0L,
	private val delay: () -> Long = { ApexClickDelay.nextMs() },

	val reopenSettleMs: Long = REOPEN_SETTLE_MS,

	val holdTimeoutMs: Long = HOLD_TIMEOUT_MS,
) {
	companion object {
		const val REOPEN_SETTLE_MS = 300L; const val HOLD_TIMEOUT_MS = 60_000L

		fun pressRule(steps: List<Step>, command: Boolean): String? {
			if (steps.any { it.text != null }) return "text entry is never automated, type it yourself"
			var free = if (command) 0 else 1
			for (s in steps) {
				if (s.hold) continue
				if (free == 0) return "that would send more than one click for one press"
				free--
			}
			return null
		}
		private val ODD_SPACES = Regex("[\\s\\u00A0\\u2000-\\u200B\\u202F\\u205F\\u3000]+")

		fun normTitle(s: String) = s.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").replace('\u2018', '\'').replace('\u2019', '\'')
			.replace('\u201C', '"').replace('\u201D', '"').replace(ODD_SPACES, " ").trim()

		fun norm(s: String) = normTitle(s).trimEnd('!', ' ').lowercase()
		private fun n(s: String) = norm(s)

		fun identityLine(l: Label): String? = l.lore.firstOrNull { it.isNotBlank() }?.trim()

		fun sameItem(expect: Label, live: Label?): Boolean = live != null && live.slot == expect.slot && n(live.name) == n(expect.name) &&
			identityLine(live).orEmpty().equals(identityLine(expect).orEmpty(), true) && (expect.id == null || expect.id == live.id)
	}

	data class Label(val slot: Int, val name: String, val lore: List<String> = emptyList(), val id: String? = null)

	data class Target(val name: String, val slot: Int? = null, val anyAtSlot: Boolean = false, val confirmAny: Boolean = false, val alt: String? = null,

		val match: Regex? = null,

		val pick: ((List<Label>) -> Label?)? = null,

		val inventory: Boolean = false,

		val missing: String? = null,

		val waitWhile: ((List<Label>) -> Boolean)? = null)

	data class Step(val title: Regex, val target: Target, val button: Int = 0, val optional: Boolean = false,
		val label: String = target.name, val text: String? = null, val timeoutReason: String? = null,

		val doneChat: Regex? = null,

		val refuse: ((Label) -> String?)? = null,

		val hold: Boolean = false,

		val skip: Skip? = null) {
		init { require(button == 0 || button == 1) { "MenuChain step button must be 0 (left) or 1 (right), got $button" } }
		companion object {

			fun text(value: String, label: String = "text entry") = Step(Regex(""), Target(""), label = label, text = value)
		}
	}

	data class Skip(val title: Regex, val verify: ((String, List<Label>) -> Boolean?)? = null, val reason: String = "this is not the expected page, nothing was clicked")

	sealed interface Finish {

		data object AfterLastClick : Finish

		data class Confirmed(val chat: Regex, val reopen: Regex? = null) : Finish
	}

	sealed interface Action {
		data object Wait : Action

		data class Click(val containerId: Int, val slot: Int, val button: Int, val delayMs: Long, val name: String = "", val expect: Label? = null) : Action

		data class Type(val containerId: Int, val value: String, val delayMs: Long) : Action

		data class Hold(val containerId: Int, val label: Label, val labels: List<Label>, val previous: Label?, val title: String) : Action
		data object Done : Action

		data class Abort(val reason: String, val seen: List<String>? = null) : Action
	}

	var index = 0; private set

	var latestId = Int.MIN_VALUE; private set
	private var latestTitle = ""
	private var latestEntry = false

	private var clickedId: Int? = null
	private var pending: Action? = null
	private var lastProgressMs = startMs
	private var opensSinceAction = 0
	private var lastOpenMs = Long.MIN_VALUE
	var finished: Action? = null; private set

	private var held: Action.Click? = null
	private var heldLabel: Label? = null
	private var pendingLabel: Label? = null
	private var heldAtMs = 0L

	var lastClicked: Label? = null; private set
	val holding get() = held != null

	var clicks = 0; private set

	private fun done(a: Action): Action { finished = a; pending = null; held = null; return a }

	private var verifying: Int? = null

	var skipped = 0; private set
	private fun isConfirm(s: String) = norm(s).let { it == "confirm" || it.startsWith("confirm ") }
	private val restOptional get() = steps.drop(index).all { it.optional }

	fun begin(containerId: Int?, title: String?, nowMs: Long, requireNewWindow: Boolean = false, textEntry: Boolean = false): Action {
		lastProgressMs = nowMs
		if (containerId == null || title == null) return Action.Wait
		if (requireNewWindow) { latestId = containerId; latestTitle = normTitle(title); latestEntry = textEntry; clickedId = containerId; return Action.Wait }
		return onOpen(containerId, title, nowMs, textEntry)
	}

	fun onOpen(containerId: Int, rawTitle: String, nowMs: Long, textEntry: Boolean = false): Action {
		finished?.let { return it }
		val title = normTitle(rawTitle)
		if (verifying != containerId) verifying = null
		if (containerId != latestId) { opensSinceAction++; lastOpenMs = nowMs }
		latestId = containerId; latestTitle = title; latestEntry = textEntry
		held?.let { if (containerId != it.containerId) return done(Action.Abort("the page changed while waiting for your confirm, nothing was clicked")) }
		if (index >= steps.size) {
			val f = finish
			if (f is Finish.Confirmed && f.reopen != null && containerId != clickedId && f.reopen.containsMatchIn(title)) return done(Action.Done)
			return Action.Wait
		}
		if (containerId == clickedId) return Action.Wait
		fun fits(s: Step) = if (s.text != null) textEntry else !textEntry && s.title.containsMatchIn(title)
		fun skips(s: Step) = s.text == null && !textEntry && s.skip?.title?.containsMatchIn(title) == true
		var step: Step
		while (true) {
			while (index < steps.size && !fits(steps[index]) && steps[index].optional && !skips(steps[index])) index++
			if (index >= steps.size) return if (finish == Finish.AfterLastClick) done(Action.Done) else Action.Wait
			step = steps[index]
			if (fits(step) || !skips(step)) break

			if (step.skip?.verify != null) { verifying = containerId; return Action.Wait }
			index++; skipped++
		}
		if (!fits(step)) return done(Action.Abort(when {
			step.text != null -> "expected a sign/anvil for '${step.label}', got '$title'"
			textEntry -> "unexpected sign/anvil"
			else -> "expected '${step.title.pattern}', got '$title' (${title.length} chars)"
		}))
		if (step.text != null) {
			if (step.text.isBlank()) return done(Action.Abort("empty value for '${step.label}'"))
			return Action.Type(containerId, step.text, ApexClickDelay.clamp(delay())).also { pending = it }
		}
		return Action.Wait
	}

	fun onContents(containerId: Int, labels: List<Label>, nowMs: Long, inventory: List<Label> = emptyList()): Action {
		finished?.let { return it }
		if (containerId != latestId || latestEntry || pending != null || held != null || index >= steps.size || containerId == clickedId) return Action.Wait
		var step = steps[index]
		if (verifying == containerId) {
			val sk = step.skip ?: return Action.Wait
			when (sk.verify?.invoke(latestTitle, labels)) {
				null -> return Action.Wait
				false -> return done(Action.Abort(sk.reason, labels.filter { it.name.isNotBlank() }.map { "${it.slot}:${it.name}" }))
				true -> {
					verifying = null; index++; skipped++
					if (index >= steps.size) return if (finish == Finish.AfterLastClick) done(Action.Done) else Action.Wait
					step = steps[index]
				}
			}
		}

		if (step.text != null || !step.title.containsMatchIn(latestTitle)) return Action.Wait

		if (opensSinceAction >= 2 && nowMs - lastOpenMs < reopenSettleMs) return Action.Wait
		val t = step.target
		val hits = when {
			t.inventory -> listOfNotNull(t.pick?.invoke(inventory))
			t.pick != null -> listOfNotNull(t.pick.invoke(labels))
			t.match != null -> labels.filter { t.match.containsMatchIn(it.name) }
			t.anyAtSlot -> {
				val at = labels.firstOrNull { it.slot == t.slot }
				val n = at?.let { norm(it.name) } ?: ""
				if (at == null || n.isBlank() || n == "go back" || n == "close") return done(Action.Abort("no confirm item at slot ${t.slot}"))
				listOf(at)
			}
			t.confirmAny -> labels.filter { isConfirm(it.name) || (t.alt != null && norm(it.name) == norm(t.alt)) }.take(1)
			t.name.endsWith("…") -> { val pre = t.name.dropLast(1).trim().lowercase(); labels.filter { it.name.trim().lowercase().startsWith(pre) } }
			else -> labels.filter { norm(it.name) == norm(t.name) }
		}
		if (hits.size > 1) return done(Action.Abort("several items named '${t.name}'"))
		val hit = hits.singleOrNull() ?: return when {

			t.missing != null && labels.any { it.name.isNotBlank() } && t.waitWhile?.invoke(labels) != true ->
				done(Action.Abort(t.missing, labels.filter { it.name.isNotBlank() }.map { "${it.slot}:${it.name}" }))
			else -> Action.Wait
		}
		if (!t.anyAtSlot && t.slot != null && hit.slot != t.slot) return done(Action.Abort("'${t.name}' is in slot ${hit.slot}, expected ${t.slot}"))
		step.refuse?.invoke(hit)?.let { return done(Action.Abort(it)) }
		if (step.hold) {
			held = Action.Click(containerId, hit.slot, step.button, 0L, hit.name, hit); heldLabel = hit; heldAtMs = nowMs
			return Action.Hold(containerId, hit, labels, lastClicked, latestTitle)
		}
		pendingLabel = hit
		return Action.Click(containerId, hit.slot, step.button, ApexClickDelay.clamp(delay()), hit.name, hit).also { pending = it }
	}

	fun approve(nowMs: Long): Action {
		finished?.let { return it }
		val h = held ?: return Action.Wait
		if (latestId != h.containerId) return done(Action.Abort("the page changed before your confirm, nothing was clicked"))
		held = null; lastProgressMs = nowMs; pendingLabel = heldLabel
		return h.copy(delayMs = ApexClickDelay.clamp(delay())).also { pending = it }
	}

	fun fire(containerId: Int, nowMs: Long): Boolean {
		val p = pending ?: return false
		val id = when (p) { is Action.Click -> p.containerId; is Action.Type -> p.containerId; else -> return false }

		if (id == latestId && containerId != id) return false
		pending = null
		if (finished != null || containerId != id || containerId != latestId) return false
		clicks++; clickedId = containerId; index++; lastProgressMs = nowMs; opensSinceAction = 0
		if (p is Action.Click) lastClicked = pendingLabel
		if (index >= steps.size && finish == Finish.AfterLastClick) done(Action.Done)
		return true
	}

	fun onChat(line: String, nowMs: Long = lastProgressMs): Action {
		finished?.let { return it }

		val prev = steps.getOrNull(index - 1)
		if (clicks > 0 && pending == null && clickedId != null && prev?.doneChat?.containsMatchIn(line) == true) {
			clickedId = null; lastProgressMs = nowMs
			if (index >= steps.size && finish == Finish.AfterLastClick) return done(Action.Done)
		}
		val f = finish
		if (clicks > 0 && restOptional && f is Finish.Confirmed && f.chat.containsMatchIn(line)) return done(Action.Done)
		return Action.Wait
	}

	fun onTick(nowMs: Long): Action {
		finished?.let { return it }
		if (held != null) return if (nowMs - heldAtMs > holdTimeoutMs) done(Action.Abort("no answer at the confirm, nothing was clicked")) else Action.Wait
		if (pending != null) return Action.Wait
		if (nowMs - lastProgressMs > timeoutMs) return done(Action.Abort(
			if (index >= steps.size || (clicks > 0 && restOptional && finish is Finish.Confirmed)) "Not confirmed by Hypixel"
			else steps[index].timeoutReason ?: "timed out waiting for '${steps[index].label}'"))
		return Action.Wait
	}

	fun cancel(reason: String): Action = finished ?: done(Action.Abort(reason))

	fun stop(reason: String): Action = finished ?: done(Action.Abort(reason, emptyList()))

	val clickPending get() = pending != null
}

object MenuChainRunner {
	interface Owner {
		val id: String

		fun cover(screen: AbstractContainerScreen<*>) {}

		fun showVanilla(screen: AbstractContainerScreen<*>?) { screen?.customGui = null }

		fun refresh(result: MenuChain.Action) {}

		fun hold(h: MenuChain.Action.Hold) { MenuChainRunner.cancel("this menu has no confirm step, nothing was clicked") }
	}

	private var chain: MenuChain? = null
	private var owner: Owner? = null

	var pressDriven = false; private set
	private var seenId = Int.MIN_VALUE
	private val settle = ContainerSettle()

	private var signScreen: Any? = null
	private var signId = -1000

	val active get() = chain != null

	val covering get() = active

	val ownerId: String? get() = owner?.id

	fun coveringFor(idPrefix: String) = covering && ownsCover(ownerId, idPrefix)

	fun ownsCover(ownerId: String?, idPrefix: String) = ownerId != null && (ownerId == idPrefix || ownerId.startsWith("$idPrefix-"))

	private fun current(): Triple<Int, String, Boolean>? {
		val scr = Client.screen
		if (scr is net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen) {
			if (signScreen !== scr) { signScreen = scr; signId-- }
			return Triple(signId, "Sign", true)
		}
		val screen = scr as? AbstractContainerScreen<*> ?: return null
		val menu = Client.player?.containerMenu ?: return null
		if (screen.menu !== menu) return null
		return Triple(menu.containerId, screen.title.bareText, menu is net.minecraft.world.inventory.AnvilMenu)
	}

	private var ending = false

	fun start(c: MenuChain, o: Owner, command: String? = null): Boolean {
		if (active) return false
		if (ending) { log("refused ${o.id}: a follow-up chain needs its own player press"); return false }
		MenuChain.pressRule(c.steps, command != null)?.let { why ->
			log("refused ${o.id}: $why")
			Client.showChat(net.minecraft.network.chat.Component.literal("§6[Apex] §cNothing was clicked: $why."))
			return false
		}

		if (c.steps.isEmpty()) { log("refused ${o.id}: no step to click"); return false }
		if (MenuRegistry.ownerSwitchedOff(o.id)) { log("refused ${o.id}: its section switch is OFF"); return false }
		chain = c; owner = o

		pressDriven = MenuPress.attributed
		MenuPress.acted()
		val cur = current()
		seenId = cur?.first ?: Int.MIN_VALUE
		log("start ${o.id} (${c.steps.size} steps${if (pressDriven) ", user press" else ""})")
		handle(c.begin(cur?.first, cur?.second, System.currentTimeMillis(), requireNewWindow = command != null, textEntry = cur?.third == true))
		if (command != null && chain === c) Client.runCommand(command)
		return true
	}

	fun cancel(reason: String) { chain?.let { MenuPress.acted(); handle(it.cancel(reason)) } }

	fun stop(reason: String) { chain?.let { MenuPress.acted(); handle(it.stop(reason)) } }

	fun approveHold() {
		val c = chain ?: return
		if (MenuPress.attributed) pressDriven = true
		MenuPress.acted()
		handle(c.approve(System.currentTimeMillis()))
	}

	private fun log(s: String) = runCatching { Apex.logger.info("[ApexMenu] chain $s") }

	private fun end(a: MenuChain.Action) {
		val o = owner ?: return
		chain = null; owner = null

		ending = true
		try { MenuPress.carry(pressDriven) { endFor(o, a) } } finally { ending = false }
	}

	private fun endFor(o: Owner, a: MenuChain.Action) {
		val screen = Client.screen as? AbstractContainerScreen<*>
		if (a is MenuChain.Action.Abort) {
			log("abort ${o.id}: ${a.reason}")

			Client.showChat(net.minecraft.network.chat.Component.literal(if (a.seen != null) "§c${a.reason}." else "§6[Apex] §cAborted: ${a.reason}."))
			runCatching { o.showVanilla(screen) }
		} else log("done ${o.id}")
		runCatching { o.refresh(a) }
	}

	private fun handle(a: MenuChain.Action) = MenuPress.carry(pressDriven) { handleNow(a) }

	private fun handleNow(a: MenuChain.Action) {
		val c = chain ?: return
		when (a) {
			is MenuChain.Action.Click -> ApexClickDelay.laterMs(a.delayMs) {
				if (chain !== c) return@laterMs
				val menu = Client.player?.containerMenu ?: return@laterMs

				val e = a.expect
				if (e != null && menu.containerId == a.containerId) {
					val live = menu.slots.getOrNull(a.slot)?.let { labelOf(it) }
					if (!MenuChain.sameItem(e, live)) {
						log("re-verify failed in ${menu.containerId}: slot ${a.slot} was '${e.name}' [${MenuChain.identityLine(e) ?: "-"}], now '${live?.name ?: "empty"}' [${live?.let { MenuChain.identityLine(it) } ?: "-"}]; nothing clicked")
						handle(c.cancel("'${e.name}' changed before the click, nothing was clicked"))
						return@laterMs
					}
				}

				if (c.fire(menu.containerId, System.currentTimeMillis())) {
					val slot = menu.slots.getOrNull(a.slot) ?: return@laterMs
					if (a.name.isNotEmpty()) log("click '${a.name}' slot ${a.slot} button ${a.button} in ${menu.containerId}")
					clickNow(menu, slot, a.button)
				}
				c.finished?.let { end(it) }
			}

			is MenuChain.Action.Type -> handle(c.cancel("text entry is never automated, nothing was typed"))
			is MenuChain.Action.Hold -> { log("hold '${a.label.name}' slot ${a.label.slot} in ${a.containerId}: waiting for the Apex confirm"); owner?.let { o -> runCatching { o.hold(a) } } }
			MenuChain.Action.Done, is MenuChain.Action.Abort -> end(a)
			MenuChain.Action.Wait -> {}
		}
	}

	private fun labelOf(slot: net.minecraft.world.inventory.Slot): MenuChain.Label {
		val st = slot.item
		val id = if (slot.container === Client.player?.inventory)
			runCatching { st.view().repoItemId?.let { dev.ashtonite365.apex.util.bazaarStockOf(it).stockKey } }.getOrNull() else null
		return MenuChain.Label(slot.index, st.hoverName.string.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").trim(),
			runCatching { st.view().loreAccordingToNbt.map { l -> l.bareText } }.getOrDefault(emptyList()), id)
	}

	private fun clickNow(menu: net.minecraft.world.inventory.AbstractContainerMenu, slot: net.minecraft.world.inventory.Slot, button: Int) {
		ApexClickDelay.send(menu, slot, button)
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val c = chain ?: return
		val r = c.onChat(event.bareText, System.currentTimeMillis())
		if (r == MenuChain.Action.Done) log("confirmed by chat: ${event.bareText}")
		handle(r)
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		val c = chain ?: return

		if (MenuRegistry.ownerSwitchedOff(owner?.id)) { cancel("section switch turned OFF"); return }
		val now = System.currentTimeMillis()

		if (c.holding && Client.screen == null) { cancel("the page was closed at the confirm, nothing was clicked"); return }
		val cur = current()
		if (cur != null && cur.first != seenId) {
			seenId = cur.first
			if (covering) (Client.screen as? AbstractContainerScreen<*>)?.let { s -> runCatching { owner?.cover(s) } }
			handle(c.onOpen(cur.first, cur.second, now, cur.third))
			if (chain !== c) return
		}
		val screen = Client.screen as? AbstractContainerScreen<*>
		val menu = Client.player?.containerMenu
		if (cur != null && !cur.third && screen != null && menu != null && screen.menu === menu && !c.clickPending) {
			val slots = menu.slots.filter { it.container !== Client.player?.inventory && !it.item.isEmpty }
			val sig = menu.stateId.toString() + "|" + slots.joinToString(";") { "${it.index}:${it.item.hoverName.string}:${it.item.count}:${runCatching { it.item.view().loreAccordingToNbt.size }.getOrDefault(0)}" }
			if (slots.isNotEmpty() && settle.stable(menu.containerId, sig)) {
				val labels = slots.filter { !BuiltInRegistries.ITEM.getKey(it.item.item).path.contains("glass_pane") }.map { labelOf(it) }
				val inv = menu.slots.filter { it.container === Client.player?.inventory && !it.item.isEmpty }.map { labelOf(it) }
				handle(c.onContents(menu.containerId, labels, now, inv))
				if (chain !== c) return
			}
		}
		handle(c.onTick(now))
	}
}
