package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.WeirdosLogic
import dev.ashtonite365.apex.features.dungeon.WeirdosLogic.Kind

class WeirdosLogicTest {
	private val solutions = listOf(
		"The reward is not in my chest!",
		"At least one of them is lying, and the reward is not in Baxter's chest!",
		"My chest doesn't have the reward. We are all telling the truth.",
		"My chest has the reward and I'm telling the truth!",
		"The reward isn't in any of our chests.",
		"Both of them are telling the truth. Also, Hope has the reward in their chest!",
	)
	private val wrongs = listOf(
		"One of us is telling the truth!",
		"They are both telling the truth. The reward isn't in Baxter's chest.",
		"We are all telling the truth!",
		"Baxter is telling the truth and the reward is in his chest.",
		"My chest doesn't have the reward. At least one of the others is telling the truth!",
		"One of the others is lying.",
		"They are both telling the truth, the reward is in Baxter's chest.",
		"They are both lying, the reward is in my chest!",
		"The reward is in my chest.",
		"The reward is not in my chest. They are both lying.",
		"Baxter is telling the truth.",
		"My chest has the reward.",
	)

	@Test fun everyReferenceSolutionLineIsSolution() = solutions.forEach {
		assertEquals(Kind.SOLUTION, WeirdosLogic.parse("§e[NPC] §cBaxter§f: $it")?.kind, it)
	}
	@Test fun everyReferenceWrongLineIsWrong() = wrongs.forEach {
		assertEquals(Kind.WRONG, WeirdosLogic.parse("[NPC] Ardis: $it")?.kind, it)
	}
	@Test fun nonNpcIgnored() = assertNull(WeirdosLogic.parse("Baxter: The reward is not in my chest!"))
	@Test fun singleSolutionPicked() {
		val l = listOf("[NPC] Ardis: The reward is in my chest.", "[NPC] Hope: The reward isn't in any of our chests.", "[NPC] Lino: One of us is telling the truth!").map { WeirdosLogic.parse(it)!! }
		assertEquals("Hope", WeirdosLogic.correctNpc(l))
	}

	@Test fun ashtonExampleAsWritten() {
		val l = listOf("[NPC] A: The reward isn't in any of our chests", "[NPC] B: The reward is in my chest", "[NPC] C: The reward is not in my chest").map { WeirdosLogic.parse(it)!! }
		assertEquals("A", WeirdosLogic.correctNpc(l))
	}

	@Test fun ambiguousWhenTwoSolutionLines() {
		val l = listOf("[NPC] A: The reward isn't in any of our chests.", "[NPC] C: The reward is not in my chest!").map { WeirdosLogic.parse(it)!! }
		assertEquals(listOf(Kind.SOLUTION, Kind.SOLUTION), l.map { it.kind })
		assertNull(WeirdosLogic.correctNpc(l))
	}
	@Test fun solvedMessage() = assertEquals(true, WeirdosLogic.isSolved("§a§lPUZZLE SOLVED! §r§bAshton §r§ewasn't fooled by §r§cBaxter§r§e! §r§4G§r§co§r§6o§r§ed§r§a!"))
}
