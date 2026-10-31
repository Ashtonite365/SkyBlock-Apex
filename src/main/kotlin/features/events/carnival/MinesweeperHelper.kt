package dev.ashtonite365.apex.features.events.carnival

import io.github.notenoughupdates.moulconfig.observer.ObservableList
import io.github.notenoughupdates.moulconfig.platform.MoulConfigPlatform
import io.github.notenoughupdates.moulconfig.xml.Bind
import java.util.UUID
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.core.BlockPos
import net.minecraft.world.level.LevelAccessor
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.AttackBlockEvent
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.debug.DebugLogger
import dev.ashtonite365.apex.util.LegacyFormattingCode
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.GuiXml
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.center
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.skullWithSkin
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.withApexUiId
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.useMatch

object MinesweeperHelper {
	val sandBoxLow: BlockPos = BlockPos(-112, 72, -11)
	val sandBoxHigh: BlockPos = BlockPos(-106, 72, -5)

	val boardSize: Pair<Int, Int> = (sandBoxHigh.x - sandBoxLow.x) to (sandBoxHigh.z - sandBoxLow.z)

	val gameStartMessage: String = "[NPC] Carnival Pirateman: Good luck, matey!"
	val gameEndMessage: String = "Fruit Digging"
	val bombPattern: java.util.regex.Pattern = "MINES! There (are|is) (?<bombCount>[0-8]) bombs? hidden nearby\\.".toPattern()
	val startGameQuestion: String = "[NPC] Carnival Pirateman: Would ye like to do some Fruit Digging?"

	enum class Piece
	constructor(
		val fruitName: String,
		val points: Int,
		val totalPerBoard: Int,
		val fruitColor: LegacyFormattingCode,
		val specialAbility: String,
		val textureHash: String,
	) {
		COCONUT("Coconut", 200, 3, LegacyFormattingCode.DARK_PURPLE, "Prevents a bomb from exploding next turn",
			"10ceb1455b471d016a9f06d25f6e468df9fcf223e2c1e4795b16e84fcca264ee"),
		APPLE("Apple", 100, 8, LegacyFormattingCode.GREEN, "Gains 100 points for each apple dug up",
			"17ea278d6225c447c5943d652798d0bbbd1418434ce8c54c54fdac79994ddd6c"),
		WATERMELON("Watermelon", 100, 4, LegacyFormattingCode.DARK_BLUE, "Blows up an adjacent fruit for half the points",
			"efe4ef83baf105e8dee6cf03dfe7407f1911b3b9952c891ae34139560f2931d6"),
		DURIAN("Durian", 800, 2, LegacyFormattingCode.DARK_PURPLE, "Halves the points earned in the next turn",
			"ac268d36c2c6047ffeec00124096376b56dbb4d756a55329363a1b27fcd659cd"),
		MANGO("Mango", 300, 10, LegacyFormattingCode.GREEN, "Just an ordinary fruit",
			"f363a62126a35537f8189343a22660de75e810c6ac004a7d3da65f1c040a839"),
		DRAGON_FRUIT("Dragonfruit", 1200, 1, LegacyFormattingCode.LIGHT_PURPLE, "Halves the points earned in the next turn",
			"3cc761bcb0579763d9b8ab6b7b96fa77eb6d9605a804d838fec39e7b25f95591"),
		POMEGRANATE("Pomegranate", 200, 4, LegacyFormattingCode.DARK_BLUE, "Grants an extra 50% more points in the next turn",
			"40824d18079042d5769f264f44394b95b9b99ce689688cc10c9eec3f882ccc08"),
		CHERRY("Cherry", 200, 2, LegacyFormattingCode.DARK_PURPLE, "The second cherry grants 300 bonus points",
			"c92b099a62cd2fbf8ada09dec145c75d7fda4dc57b968bea3a8fa11e37aa48b2"),
		BOMB("Bomb", -1, 15, LegacyFormattingCode.DARK_RED, "Destroys nearby fruit",
			"a76a2811d1e176a07b6d0a657b910f134896ce30850f6e80c7c83732d85381ea"),
		RUM("Rum", -1, 5, LegacyFormattingCode.YELLOW, "Stops your dowsing ability for one turn",
			"407b275d28b927b1bf7f6dd9f45fbdad2af8571c54c8f027d1bff6956fbf3c16"),
		;

		val textureUrl: String = "http://textures.minecraft.net/texture/$textureHash"

		val itemStack = run {
			val head = skullWithSkin(UUID.randomUUID(), textureUrl)
			head.editedCopy { withApexUiId("MINESWEEPER_$name") }
		}

		@get:Bind(value = "fruitName")
		val textFruitName: Component = Component.literal(fruitName)

		@get:Bind(value = "pieceLabel")
		val pieceLabel: Component = Component.literal(fruitColor.formattingCode + fruitName)

		@get:Bind(value = "boardLabel")
		val boardLabel: Component = Component.literal("§a$totalPerBoard§7/§rboard")

		@get:Bind(value = "description")
		val getDescription: Component =
			Component.literal(if (points < 0) specialAbility else "$specialAbility Default points: $points.")

		@Bind
		@OptIn(NeedsComponents::class)
		fun getIcon() = MoulConfigPlatform.wrap(itemStack.current())
	}

	object TutorialScreen {
		@get:Bind(value = "pieces")
		val pieces: ObservableList<Piece> = ObservableList(Piece.entries.asReversed().toList())

		@get:Bind(value = "modes")
		val modes: ObservableList<DowsingMode> = ObservableList(DowsingMode.entries.toList())
	}

	enum class DowsingMode
	constructor(
		val itemType: Item,
		@get:Bind("feature") val feature: String,
		@get:Bind("description") val description: String,
	) {
		MINES(feature = "Bomb detection", description = "Tells you how many bombs are near the block", itemType = Items.IRON_SHOVEL),
		ANCHOR(feature = "Lowest fruit", description = "Shows you which block nearby contains the lowest scoring fruit", itemType = Items.DIAMOND_SHOVEL),
		TREASURE(feature = "Highest fruit", description = "Tells you which kind of fruit is the highest scoring nearby", itemType = Items.GOLDEN_SHOVEL),
		;

		@Bind("itemType")
		fun getItemStack() = ItemStack(itemType).let(MoulConfigPlatform::wrap)

		companion object Shovel {
			val id: ItemId = ItemId("CARNIVAL_SHOVEL")

			fun fromItem(itemStack: ItemStack): DowsingMode? =
				if (itemStack.view().repoItemId == id) entries.firstOrNull { it.itemType == itemStack.item } else null
		}
	}

	data class BoardPosition(
		val x: Int,
		val y: Int,
	) {
		fun toBlockPos(): BlockPos {
			val corner = sandBoxLow
			return BlockPos(corner.x + x, corner.y, corner.z + y)
		}

		fun getBlock(world: LevelAccessor): Block {
			val here = world.getBlockState(toBlockPos())
			return here.block
		}

		private fun shows(world: LevelAccessor, block: Block) = getBlock(world) == block
		fun isUnopened(world: LevelAccessor): Boolean = shows(world, Blocks.SAND)
		fun isOpened(world: LevelAccessor): Boolean = shows(world, Blocks.SANDSTONE)
		fun isScorched(world: LevelAccessor): Boolean = shows(world, Blocks.SANDSTONE_STAIRS)

		companion object Lookup {

			fun fromBlockPos(
				blockPos: BlockPos,
			): BoardPosition? {
				val origin = sandBoxLow
				if (blockPos.y != origin.y) return null
				val east = blockPos.x - origin.x
				val south = blockPos.z - origin.z
				val cell = BoardPosition(east, south)
				val onBoard = cell.x in 0 until boardSize.first && cell.y in 0 until boardSize.second
				return cell.takeIf { onBoard }
			}
		}
	}

	data class GameState
	constructor(
		val bombCounts: MutableMap<BoardPosition, Int> = hashMapOf(),
		val knownBombs: MutableSet<BoardPosition> = hashSetOf(),
		var lastDug: BoardPosition? = null,
		var lastShovelMode: DowsingMode? = null,
	)

	val log: DebugLogger = DebugLogger("minesweeper")

	var gameState: GameState? = null

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand): Unit {
		event.addSubcommand("minesweepertutorial", ManagedConfig.Category.EVENTS) {
			onRun { openTutorial() }
		}
	}

	private fun openTutorial() {
		val tutorial = GuiXml.loadScreen("carnival/minesweeper_tutorial", TutorialScreen, null)
		ScreenUtil.setScreenLater(tutorial)
	}

	@Subscribe
	fun onWorldChange(event: WorldReadyEvent): Unit {
		gameState = null
	}

	private fun offerTutorial() {
		val offer = Component.translatable("apex.carnival.tutorial.minesweeper")
			.withStyle { style -> style.withClickEvent(ClickEvent.RunCommand("/apex minesweepertutorial")) }
		Client.showChat(offer)
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent): Unit {
		val line = event.bareText
		if (line == startGameQuestion && CarnivalFeatures.TConfig.displayTutorials) offerTutorial()
		if (CarnivalFeatures.TConfig.enableBombSolver.not()) {
			gameState = null
			return
		}
		if (line == gameStartMessage) {
			gameState = GameState()
			log.log("Game started")
		}
		if (line.trim() == gameEndMessage) {
			gameState = null
			log.log("Finished game")
		}
		val state = gameState ?: return
		bombPattern.useMatch(line) {
			val count = group("bombCount").toInt()
			log.log { "Marking ${state.lastDug} as having $count nearby" }
			state.lastDug?.let { cell -> state.bombCounts[cell] = count }
		}
	}

	@Subscribe
	fun onBlockClick(event: AttackBlockEvent): Unit {
		val state = gameState ?: return
		val cell = BoardPosition.fromBlockPos(event.blockPos)
		log.log { "Breaking block at ${event.blockPos} ($cell)" }
		state.lastDug = cell
		val held = event.player.mainHandItem
		state.lastShovelMode = DowsingMode.fromItem(held)
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent): Unit {
		val state = gameState ?: return
		WorldDraw.drawInWorld(event) {
			state.bombCounts.forEach { (cell, count) ->
				text(cell.toBlockPos().above().center(), Component.literal("§a$count \uD83D\uDCA3"))
			}
		}
	}
}
