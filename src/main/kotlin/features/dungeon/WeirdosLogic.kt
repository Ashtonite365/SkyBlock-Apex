
package dev.ashtonite365.apex.features.dungeon

object WeirdosLogic {
	private const val N = "[A-Za-z]+"

	val solutionLines = listOf(
		Regex("The reward is not in my chest!"),
		Regex("At least one of them is lying, and the reward is not in $N's chest[.!]?"),
		Regex("My chest doesn't have the reward\\. We are all telling the truth[.!]?"),
		Regex("My chest has the reward and I'm telling the truth!"),
		Regex("The reward isn't in any of our chests[.!]?"),
		Regex("Both of them are telling the truth\\. Also, $N has the reward in their chest[.!]?"),
	)

	val wrongLines = listOf(
		Regex("One of us is telling the truth!"),
		Regex("They are both telling the truth\\. The reward isn't in $N's chest\\."),
		Regex("We are all telling the truth!"),
		Regex("$N is telling the truth and the reward is in his chest\\."),
		Regex("My chest doesn't have the reward\\. At least one of the others is telling the truth!"),
		Regex("One of the others is lying\\."),
		Regex("They are both telling the truth, the reward is in $N's chest\\."),
		Regex("They are both lying, the reward is in my chest!"),
		Regex("The reward is in my chest\\."),
		Regex("The reward is not in my chest\\. They are both lying\\."),
		Regex("$N is telling the truth\\."),
		Regex("My chest has the reward\\."),
	)
	private val formatting = dev.ashtonite365.apex.util.SharedRegex.SECTION
	private val npcLine = Regex("^\\[NPC] ($N): (.+)$")

	enum class Kind { SOLUTION, WRONG, UNKNOWN }
	data class Line(val npc: String, val message: String, val kind: Kind)

	fun strip(raw: String) = raw.replace(formatting, "").replace('\u00A0', ' ').trim()

	fun parse(raw: String): Line? {
		val m = npcLine.matchEntire(strip(raw)) ?: return null
		val msg = m.groupValues[2].trim()
		val kind = when {
			solutionLines.any { it.matches(msg) } -> Kind.SOLUTION
			wrongLines.any { it.matches(msg) } -> Kind.WRONG
			else -> Kind.UNKNOWN
		}
		return Line(m.groupValues[1], msg, kind)
	}

	fun correctNpc(lines: List<Line>): String? =
		lines.filter { it.kind == Kind.SOLUTION }.map { it.npc }.distinct().singleOrNull()

	fun isSolved(raw: String) = strip(raw).let { it.startsWith("PUZZLE SOLVED!") && "wasn't fooled by" in it }
}
