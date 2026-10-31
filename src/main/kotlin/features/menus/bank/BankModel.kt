// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.bank

import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.MenuChain
import dev.ashtonite365.apex.util.ApexClickDelay
import dev.ashtonite365.apex.features.menus.support.SlotText

enum class BankPage { MAIN, DEPOSIT, WITHDRAW }

enum class BankAction(val page: BankPage?) {
	OPEN_DEPOSIT(BankPage.MAIN), OPEN_WITHDRAW(BankPage.MAIN),
	DEPOSIT_ALL(BankPage.DEPOSIT), DEPOSIT_HALF(BankPage.DEPOSIT), DEPOSIT_CUSTOM(BankPage.DEPOSIT),
	WITHDRAW_ALL(BankPage.WITHDRAW), WITHDRAW_HALF(BankPage.WITHDRAW), WITHDRAW_20(BankPage.WITHDRAW), WITHDRAW_CUSTOM(BankPage.WITHDRAW),
	BACK(null), CLOSE(null);
}

data class BankButton(val action: BankAction, val slot: Int, val label: String, val amount: Double?)

data class BankTx(val sign: Int, val amount: Double, val ageSeconds: Long?, val ageText: String, val who: String?)

enum class TxSort : StringRepresentable {
	NEWEST, LARGEST, DEPOSITS_FIRST;
	override fun getSerializedName() = name.lowercase()
}

data class BankModel(
	val page: BankPage,
	val account: String?,
	val balance: Double?,
	val purse: Double?,
	val interestInSeconds: Long?,
	val projectedInterest: Double?,
	val transactions: List<BankTx>,
	val buttons: List<BankButton>,

	val tier: String? = null,
	val bankLimit: Double? = null,
	val balanceLimit: Double? = null,
	val lastInterest: Double? = null,
	val projectedPct: String? = null,
) {

	val cap: Double? get() = bankLimit ?: balanceLimit
	fun button(a: BankAction) = buttons.firstOrNull { it.action == a }
}

object BankParser {
	private val I = setOf(RegexOption.IGNORE_CASE)

	val MAIN_TITLE = Regex("^(?!Bank$)(?:(Personal|Co-?op|Coop|Island|Profile)\\s+)?Bank(?:er)?(?:\\s+Account)?$", I)
	val DEPOSIT_TITLE = Regex("^(?:Bank\\s+)?Deposits?(?:\\s+Coins)?$", I)
	val WITHDRAW_TITLE = Regex("^(?:Bank\\s+)?Withdraw(?:al|als|ing)?(?:\\s+Coins)?$", I)

	val ANY_TITLE = Regex("${MAIN_TITLE.pattern}|^Bank\\s+(?:Deposits?|Withdraw(?:al|als|ing)?)(?:\\s+Coins)?$", I)

	fun page(title: String): BankPage? {
		val t = MenuText.clean(title)
		return when {
			MAIN_TITLE.matches(t) -> BankPage.MAIN
			DEPOSIT_TITLE.matches(t) -> BankPage.DEPOSIT
			WITHDRAW_TITLE.matches(t) -> BankPage.WITHDRAW
			else -> null
		}
	}

	private val N = "([+-]?\\d[\\d,]*(?:\\.\\d+)?\\s*[kKmMbB]?)"
	private val BALANCE = Regex("(?:current\\s+|bank\\s+|account\\s+)?balance\\s*[:=]?\\s*$N", I)
	private val PURSE = Regex("(?:your\\s+)?purse(?:\\s+balance)?\\s*[:=]\\s*$N", I)
	private val AMOUNT = Regex("amount\\s+to\\s+(?:deposit|withdraw)\\s*[:=]?\\s*$N", I)
	private val INTEREST_TIME = Regex("(?:until|next|time\\s+(?:until|left)|interest\\s+in)[^:]*?:?\\s*((?:\\d|an?\\s).*)$", I)
	private val INTEREST_AMOUNT = Regex("(?:projected|estimated|expected|next)?\\s*interest(?:\\s+(?:earned|amount|payout))?\\s*[:=]\\s*$N", I)
	private val TX = Regex("^([+-])\\s*$N\\s*(?:coins)?\\s*[,·|-]?\\s*(.*?)(?:\\s+by\\s+(.+))?$", I)

	private fun name(re: String) = Regex(re, I)

	fun actionRegex(a: BankAction): Regex? = NAMES.firstOrNull { (_, f) -> BankPage.entries.any { f(it) == a } }?.first
	private val NAMES: List<Pair<Regex, (BankPage) -> BankAction?>> = listOf(
		name("^deposit(?:\\s+coins)?$") to { p -> if (p == BankPage.MAIN) BankAction.OPEN_DEPOSIT else null },
		name("^withdraw(?:al)?(?:\\s+coins)?$") to { p -> if (p == BankPage.MAIN) BankAction.OPEN_WITHDRAW else null },
		name("(?:whole|entire|full|all(?:\\s+of)?(?:\\s+your)?)\\s+purse|deposit\\s+(?:all|everything)") to { _ -> BankAction.DEPOSIT_ALL },
		name("half\\s+(?:of\\s+)?(?:your\\s+|the\\s+)?purse") to { _ -> BankAction.DEPOSIT_HALF },
		name("everything|(?:whole|entire|full|all(?:\\s+of)?(?:\\s+the)?)\\s+(?:bank\\s+)?account|withdraw\\s+all") to { _ -> BankAction.WITHDRAW_ALL },
		name("half\\s+(?:of\\s+)?(?:the\\s+|your\\s+)?(?:bank\\s+)?account") to { _ -> BankAction.WITHDRAW_HALF },

		name("(?:^|\\s)20\\s*%") to { _ -> BankAction.WITHDRAW_20 },
		name("specific\\s+amount|custom\\s+amount|enter\\s+(?:an\\s+)?amount") to { p ->
			when (p) { BankPage.DEPOSIT -> BankAction.DEPOSIT_CUSTOM; BankPage.WITHDRAW -> BankAction.WITHDRAW_CUSTOM; else -> null }
		},
		name("^(?:go\\s+)?back(?:\\s+to.*)?$") to { _ -> BankAction.BACK },
		name("^close$") to { _ -> BankAction.CLOSE },
	)
	private val TX_ITEM = name("transactions?|history")

	private val SIDEBAR_PURSE = Regex("^(purse|piggy)\\s*:\\s*([\\d,]+(?:\\.\\d+)?)", I)
	fun purseFromSidebar(lines: List<String>): Double? = sidebarPurseLine(lines)?.second

	fun sidebarPurseLine(lines: List<String>): Pair<String, Double>? = lines.firstNotNullOfOrNull { l ->
		SIDEBAR_PURSE.find(MenuText.clean(l))?.let { m ->
			m.groupValues[2].replace(",", "").toDoubleOrNull()?.let { (if (m.groupValues[1].equals("piggy", true)) "Piggy" else "Purse") to it }
		}
	}

	fun livePurse(sidebar: List<String>, m: BankModel): Double? =
		purseFromSidebar(sidebar) ?: m.purse ?: m.button(BankAction.DEPOSIT_ALL)?.amount

	private val TIER = Regex("^current\\s+account\\s*:\\s*(.+)$", I)
	private val BANK_LIMIT = Regex("^bank\\s+limit\\s*:\\s*(.+)$", I)
	private val BALANCE_LIMIT = Regex("^balance\\s+limit\\s*:\\s*(.+)$", I)
	private val LAST_INTEREST = Regex("^last\\s+interest\\s*:\\s*(.+?)(?:\\s+coins)?\\s*$", I)
	private val PROJECTED = Regex("^projected\\s*:\\s*([\\d,.]+(?:\\s*[kKmMbB]\\b)?)(?:\\s+coins)?\\s*(?:\\(([\\d.]+%)\\))?", I)
	private val WORD_NUMBER = Regex("^([\\d,]*\\.?\\d+)\\s*(thousand|million|billion|trillion)\\b", I)

	fun bigNumber(s: String): Double? {
		val t = MenuText.clean(s)
		WORD_NUMBER.find(t)?.let { m ->
			val base = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
			return base * when (m.groupValues[2].lowercase()) { "thousand" -> 1e3; "million" -> 1e6; "billion" -> 1e9; else -> 1e12 }
		}
		return MenuText.number(t)
	}

	fun parse(title: String, slots: List<SlotText>): BankModel? {
		val page = page(title) ?: return null
		val account = MAIN_TITLE.matchEntire(MenuText.clean(title))?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }
		val buttons = mutableListOf<BankButton>()
		var balance: Double? = null; var purse: Double? = null; var interestIn: Long? = null; var projected: Double? = null
		val txs = mutableListOf<BankTx>()
		var tier: String? = null; var bankLimit: Double? = null; var balanceLimit: Double? = null; var last: Double? = null; var pct: String? = null
		for (s in slots) {
			val n = s.cleanName
			val lore = s.cleanLore
			val action = NAMES.firstNotNullOfOrNull { (re, f) -> if (re.containsMatchIn(n)) f(page) else null }
			if (action != null && (action.page == null || action.page == page) && buttons.none { it.action == action })
				buttons += BankButton(action, s.slot, n, lore.firstNotNullOfOrNull { AMOUNT.find(it)?.let { m -> MenuText.number(m.groupValues[1]) } })
			val isTx = TX_ITEM.containsMatchIn(n)
			for (raw in lore) {
				val l = raw.trim()
				if (tier == null) TIER.matchEntire(l)?.let { tier = it.groupValues[1].trim() }
				if (bankLimit == null) BANK_LIMIT.matchEntire(l)?.let { bankLimit = bigNumber(it.groupValues[1]) }
				if (balanceLimit == null) BALANCE_LIMIT.matchEntire(l)?.let { balanceLimit = bigNumber(it.groupValues[1]) }
				if (last == null) LAST_INTEREST.matchEntire(l)?.let { last = bigNumber(it.groupValues[1]) }
				PROJECTED.find(l)?.let { m -> if (projected == null) projected = MenuText.number(m.groupValues[1]); if (pct == null) pct = m.groupValues[2].takeIf { it.isNotEmpty() } }
				if (LAST_INTEREST.matches(l) || BALANCE_LIMIT.matches(l) || BANK_LIMIT.matches(l) || l.startsWith("Max interest", true)) continue
				if (balance == null) BALANCE.find(l)?.let { balance = MenuText.number(it.groupValues[1]) }
				if (purse == null) PURSE.find(l)?.let { purse = MenuText.number(it.groupValues[1]) }
				if (projected == null) INTEREST_AMOUNT.find(l)?.let { m ->
					if (MenuText.durationSeconds(l.substringAfter(':', "")) == null) projected = MenuText.number(m.groupValues[1])
				}
				if (interestIn == null && l.contains("interest", true)) INTEREST_TIME.find(l)?.let { interestIn = MenuText.durationSeconds(it.groupValues[1]) }
			}
			if (isTx) txs += parseTxLines(lore)
		}
		val needed = when (page) {
			BankPage.MAIN -> setOf(BankAction.OPEN_DEPOSIT, BankAction.OPEN_WITHDRAW)
			BankPage.DEPOSIT -> setOf(BankAction.DEPOSIT_ALL, BankAction.DEPOSIT_HALF, BankAction.DEPOSIT_CUSTOM)
			BankPage.WITHDRAW -> setOf(BankAction.WITHDRAW_ALL, BankAction.WITHDRAW_HALF, BankAction.WITHDRAW_20, BankAction.WITHDRAW_CUSTOM)
		}
		if (buttons.none { it.action in needed }) return null
		return BankModel(page, account, balance, purse, interestIn, projected, txs, buttons, tier, bankLimit, balanceLimit, last, pct)
	}

	fun parseTxLines(lore: List<String>): List<BankTx> {
		val out = mutableListOf<BankTx>()
		val l = lore.map { MenuText.clean(it).trim() }
		var i = 0
		while (i < l.size) {
			val t = parseTx(l[i])
			if (t == null) { i++; continue }
			val next = l.getOrNull(i + 1)
			if (t.ageText.isEmpty() && next != null && parseTx(next) == null && next.isNotEmpty()) {
				val (age, who) = next.split(Regex("\\s+by\\s+"), limit = 2).let { it[0].trim() to it.getOrNull(1)?.trim() }
				out += t.copy(ageSeconds = MenuText.durationSeconds(age.removeSuffix(" ago").trim()), ageText = age, who = who ?: t.who)
				i += 2
			} else { out += t; i++ }
		}
		return out
	}

	fun parseTx(line: String): BankTx? {
		val m = TX.matchEntire(MenuText.clean(line)) ?: return null
		val amount = MenuText.number(m.groupValues[2]) ?: return null
		val rest = m.groupValues[3].trim().trim(',').trim()
		val age = rest.removeSuffix(" ago").trim()
		return BankTx(if (m.groupValues[1] == "-") -1 else 1, kotlin.math.abs(amount), MenuText.durationSeconds(age), rest,
			m.groupValues[4].trim().takeIf { it.isNotEmpty() })
	}

	fun sort(txs: List<BankTx>, by: TxSort): List<BankTx> = when (by) {
		TxSort.NEWEST -> txs.sortedWith(compareBy(nullsLast()) { it.ageSeconds })
		TxSort.LARGEST -> txs.sortedByDescending { it.amount }
		TxSort.DEPOSITS_FIRST -> txs.sortedWith(compareByDescending<BankTx> { it.sign }.thenBy(nullsLast()) { it.ageSeconds })
	}

	fun net(txs: List<BankTx>) = txs.sumOf { it.sign * it.amount }
}

object BankAmounts {
	fun amount(a: BankAction, purse: Double?, balance: Double?, custom: Double? = null): Double? = when (a) {
		BankAction.DEPOSIT_ALL -> purse
		BankAction.DEPOSIT_HALF -> purse?.let { kotlin.math.floor(it / 2) }
		BankAction.WITHDRAW_ALL -> balance
		BankAction.WITHDRAW_HALF -> balance?.let { kotlin.math.floor(it / 2) }
		BankAction.WITHDRAW_20 -> balance?.let { kotlin.math.floor(it * 0.2) }
		BankAction.DEPOSIT_CUSTOM -> custom?.let { c -> purse?.let { minOf(c, it) } ?: c }
		BankAction.WITHDRAW_CUSTOM -> custom?.let { c -> balance?.let { minOf(c, it) } ?: c }
		else -> null
	}

	fun shown(a: BankAction, page: BankModel?, purse: Double?, balance: Double?): Double? =
		page?.takeIf { it.page == a.page }?.button(a)?.amount ?: amount(a, purse, balance)

	data class AmountLine(val text: String, val colour: Int)
	const val GREEN = 0xFF55FF55.toInt()
	const val RED = 0xFFFF5555.toInt()
	fun amountLine(a: BankAction, amount: Double?): AmountLine? {
		amount ?: return null
		val dep = a.page == BankPage.DEPOSIT
		return AmountLine((if (dep) "+" else "-") + shortText(amount), if (dep) GREEN else RED)
	}

	fun label(name: String, amount: Double?) = if (amount == null) name else "$name (${shortText(amount)})"

	fun whole(v: Double): Long = kotlin.math.floor(maxOf(0.0, v)).toLong()

	fun wholeText(v: Double): String = "%,d".format(java.util.Locale.ROOT, whole(v))

	fun emptyReason(page: BankPage?, purse: Double?, balance: Double?): String? = when (page) {
		BankPage.DEPOSIT -> if (purse != null && whole(purse) == 0L) "purse empty" else null
		BankPage.WITHDRAW -> if (balance != null && whole(balance) == 0L) "bank empty" else null
		else -> null
	}

	fun fitScale(textW: Int, boxW: Int): Float = if (textW <= boxW || textW <= 0) 1f else maxOf(0f, boxW.toFloat()) / textW

	fun shortText(v: Double): String {
		val n = whole(v)
		return when {
			n >= 1_000_000_000L -> "${n / 1_000_000_000L}B"
			n >= 1_000_000L -> "${n / 1_000_000L}M"
			n >= 1_000L -> "${n / 1_000L}K"
			else -> n.toString()
		}
	}
}

object BankRefresh {
	private val NOW_HAVE = Regex("You now have ([\\d,.]+[kKmMbB]?) coins in your account!")

	fun chatBalance(line: String): Double? =
		if (BankChains.WITHDRAW_CHAT.matches(line) || BankChains.DEPOSIT_CHAT.matches(line))
			NOW_HAVE.find(line)?.groupValues?.get(1)?.let { MenuText.number(it) } else null

	fun precision(text: String): Double {
		val t = text.replace(",", "")
		val mult = when (t.lastOrNull()?.lowercaseChar()) { 'k' -> 1e3; 'm' -> 1e6; 'b' -> 1e9; else -> 1.0 }
		val decimals = t.trimEnd { it.isLetter() }.substringAfter('.', "").length
		return 0.5 * mult / Math.pow(10.0, decimals.toDouble())
	}

	fun loreIsCurrent(lore: Double?, chatText: String): Boolean {
		val chat = MenuText.number(chatText) ?: return true
		return lore != null && kotlin.math.abs(lore - chat) <= precision(chatText)
	}
	fun chatBalanceText(line: String): String? = if (chatBalance(line) != null) NOW_HAVE.find(line)?.groupValues?.get(1) else null
}

object BankChains {

	data class Press(val single: Int?)
	private val NONE = Press(null)

	val WITHDRAW_CHAT = Regex("^You have withdrawn [\\d,.]+[kKmMbB]? coins! You now have [\\d,.]+[kKmMbB]? coins in your account!$")

	val DEPOSIT_CHAT = Regex("^You have deposited [\\d,.]+[kKmMbB]? coins! You now have [\\d,.]+[kKmMbB]? coins in your account!$")

	fun press(m: BankModel, a: BankAction, purse: Double? = null, balance: Double? = null): Press {
		if (BankAmounts.emptyReason(a.page, purse, balance) != null) return NONE
		if (a == BankAction.OPEN_DEPOSIT || a == BankAction.OPEN_WITHDRAW || a == BankAction.BACK)
			return Press(m.button(a)?.slot)
		val page = a.page ?: return NONE
		if (page != BankPage.DEPOSIT && page != BankPage.WITHDRAW) return NONE
		if (m.page == page) return Press(m.button(a)?.slot)
		if (m.page != BankPage.MAIN) return NONE

		return Press(m.button(if (page == BankPage.DEPOSIT) BankAction.OPEN_DEPOSIT else BankAction.OPEN_WITHDRAW)?.slot)
	}
}

object BankLayout {
	data class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
		fun contains(px: Double, py: Double) = px >= x && px < x + w && py >= y && py < y + h
	}
	fun pills(x: Int, y: Int, width: Int, n: Int, h: Int = 16, gap: Int = 3): List<Rect> {
		if (n <= 0) return emptyList()
		val pw = (width - (n - 1) * gap) / n
		return List(n) { Rect(x + it * (pw + gap), y, pw, h) }
	}

	fun fitPills(x: Int, y: Int, width: Int, labels: List<String>, textWidth: (String) -> Int, h: Int = 16, gap: Int = 3, padX: Int = 8): List<Rect>? {
		if (labels.isEmpty()) return emptyList()
		val need = labels.map { textWidth(it) + padX }
		val free = width - need.sum() - (labels.size - 1) * gap
		if (free < 0) return null
		val extra = free / labels.size
		var cx = x
		return need.map { n -> Rect(cx, y, n + extra, h).also { cx += n + extra + gap } }
	}

	fun amountRect(pill: Rect, lineH: Int = 9, gap: Int = 2) = Rect(pill.x, pill.y + pill.h + gap, pill.w, lineH)
	fun intersects(a: Rect, b: Rect) = a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h

	data class InterestRow(val x: Int, val projX: Int, val full: Boolean)
	const val INTEREST_SHIFT = 8
	const val GAP = 4

	fun interestRow(colX: Int, right: Int, minX: Int, labelW: Int, timerW: Int, projFullW: Int?, projShortW: Int?): InterestRow {
		fun need(p: Int?) = maxOf(labelW, timerW + (p?.let { GAP + it } ?: 0))
		val base = colX - INTEREST_SHIFT
		for ((w, full) in listOf(projFullW to true, projShortW to false)) {
			val x = minOf(base, right - need(w))
			if (x >= minX) return InterestRow(x, x + timerW + GAP, full)
		}
		val x = maxOf(minX, minOf(base, right - need(projShortW)))
		return InterestRow(x, x + timerW + GAP, false)
	}

	fun hit(rects: List<Rect>, px: Double, py: Double) = rects.indexOfLast { it.contains(px, py) }.takeIf { it >= 0 }
}

object BankCap {
	fun fraction(balance: Double?, cap: Double?): Double? = if (balance == null || cap == null || cap <= 0) null else (balance / cap).coerceIn(0.0, 1.0)
	fun text(balance: Double?, cap: Double?): String = (balance?.let { MenuText.coins(it) } ?: "—") + (cap?.let { " / " + MenuText.coins(it) } ?: "")
}
