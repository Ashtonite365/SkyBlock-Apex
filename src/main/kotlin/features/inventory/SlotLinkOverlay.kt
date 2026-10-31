package dev.ashtonite365.apex.features.inventory

import kotlin.math.roundToInt
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import dev.ashtonite365.apex.features.inventory.BindLinkStyle
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen
import dev.ashtonite365.apex.util.accessors.castAccessor
import dev.ashtonite365.apex.util.customgui.CustomGui
import dev.ashtonite365.apex.util.render.outlineBox
import dev.ashtonite365.apex.util.render.lineBetween
import dev.ashtonite365.apex.util.itemUuid
import dev.ashtonite365.apex.util.mc.view

object SlotLinkOverlay {

	data class SlotBox(val x: Int, val y: Int, val scale: Float = 1f, val standIn: Boolean = false) {
		val size get() = (16 * scale).roundToInt()

		fun anchor(hotbar: Boolean): Pair<Int, Int> = x + (9 * scale).roundToInt() to y + (if (hotbar) 0 else size)
		fun contains(mx: Double, my: Double) = InventoryPanel.hitsCell(x, y, mx, my, scale)
	}

	class LockState(
		val lockedSlots: Set<Int>,
		val boundPairs: List<Pair<Int, Int>>,
		val lockedUUIDs: Set<java.util.UUID>,

		val uuidAt: (Int) -> java.util.UUID?,
	) {

		fun lockAt(index: Int): LockKind? = when {
			index in lockedSlots -> LockKind.SLOT
			uuidAt(index)?.let { it in lockedUUIDs } == true -> LockKind.UUID
			else -> null
		}
		companion object {
			fun of(global: GlobalSlotLocks, uuidAt: (Int) -> java.util.UUID?) = LockState(
				global.lockedSlots.toSet(), global.boundSlots.pairs.map { it.hotbar to it.inventory }, global.lockedUUIDs.toSet(), uuidAt)
		}
	}

	enum class LockKind(val base: String) { SLOT("slot_locked"), UUID("uuid_locked") }

	data class Lock(val index: Int, val box: SlotBox, val kind: LockKind)

	data class Frame(val links: List<Link>, val locks: List<Lock>, val drag: Drag?)

	data class Link(
		val hotbar: Int, val inventory: Int, val hotBox: SlotBox, val invBox: SlotBox,
		val hotHighlit: Boolean, val invHighlit: Boolean, val line: Boolean, val lineHighlit: Boolean,
	)

	data class Drag(val from: SlotBox, val fromHotbar: Boolean, val to: SlotBox?, val toHotbar: Boolean, val onSelf: Boolean = false)

	const val HIGHLIT = 0xFF00FF00.toInt()
	const val NORMAL = 0xc0a0f000.toInt()

	fun isHotbar(inventoryIndex: Int) = inventoryIndex < 9

	fun inventoryIndexOfWindowSlot(windowSlot: Int, windowSlots: Int): Int? {
		if (windowSlots < 36) return null
		val rel = windowSlot - InventoryPanel.chestSlots(windowSlots)
		return when (rel) {
			in 0..26 -> 9 + rel
			in 27..35 -> rel - 27
			else -> null
		}
	}

	fun windowSlotOfInventoryIndex(inventoryIndex: Int, windowSlots: Int): Int? {
		if (windowSlots < 36 || inventoryIndex !in 0..35) return null
		val c = InventoryPanel.chestSlots(windowSlots)
		return if (inventoryIndex < 9) c + 27 + inventoryIndex else c + inventoryIndex - 9
	}

	fun inventoryIndexOfCell(row: Int, col: Int): Int = if (row == 3) col else 9 + row * 9 + col

	fun fromPanel(origin: Pair<Int, Int>): Map<Int, SlotBox> = buildMap {
		for (row in 0..3) for (col in 0..8) {
			val (x, y) = InventoryPanel.itemPos(row, col, origin.first, origin.second)
			put(inventoryIndexOfCell(row, col), SlotBox(x, y))
		}
	}

	fun fromTileGrid(gx: Int, gy: Int, hotbarGap: Int = 4): Map<Int, SlotBox> = buildMap {
		for (row in 0..3) for (col in 0..8)
			put(inventoryIndexOfCell(row, col), SlotBox(gx + col * 18 + 1, gy + row * 18 + (if (row == 3) hotbarGap else 0) + 1, standIn = true))
	}

	fun fromWindowCells(cells: Map<Int, Pair<Int, Int>>, windowSlots: Int, scale: Float = 1f, standIn: Boolean = false): Map<Int, SlotBox> = buildMap {
		for ((w, p) in cells) inventoryIndexOfWindowSlot(w, windowSlots)?.let { put(it, SlotBox(p.first, p.second, scale, standIn)) }
	}

	fun fromPositions(positionOf: (Int) -> Pair<Int, Int>): Map<Int, SlotBox> =
		(0 until 36).associateWith { positionOf(it).let { (x, y) -> SlotBox(x, y, standIn = true) } }

	fun forMenuSession(
		panelOrigin: Pair<Int, Int>?, realCells: Map<Int, Pair<Int, Int>>, cellScale: Float,
		standIns: Map<Int, Pair<Int, Int>>, windowSlots: Int,
	): Map<Int, SlotBox> {
		val out = LinkedHashMap<Int, SlotBox>()
		if (panelOrigin != null && windowSlots >= 36) out.putAll(fromPanel(panelOrigin))
		out.putAll(fromWindowCells(realCells, windowSlots, cellScale))
		out.putAll(fromWindowCells(standIns, windowSlots, standIn = true))
		return out
	}

	fun fromRealSlots(slots: List<Triple<Int, Int, Int>>, leftPos: Int, topPos: Int): Map<Int, SlotBox> = buildMap {
		for ((idx, sx, sy) in slots) if (idx in 0..35 && sx > -10000 && sy > -10000) put(idx, SlotBox(leftPos + sx, topPos + sy))
	}

	fun hovered(boxes: Map<Int, SlotBox>, mx: Double, my: Double): Int? =
		boxes.entries.lastOrNull { it.value.contains(mx, my) }?.key

	fun plan(pairs: List<Pair<Int, Int>>, boxes: Map<Int, SlotBox>, hovered: Int?, mode: BindLinkStyle): List<Link> {
		val highlit = mutableSetOf<Int>()
		val out = mutableListOf<Link>()
		for ((hot, inv) in pairs) {
			val hb = boxes[hot] ?: continue
			val ib = boxes[inv] ?: continue
			val anyHovered = hovered != null && (hovered == hot || hovered == inv)
			if (!anyHovered && mode == BindLinkStyle.NOTHING) continue
			if (anyHovered) { highlit += hot; highlit += inv }
			out += Link(hot, inv, hb, ib, hot in highlit, inv in highlit,
				line = mode == BindLinkStyle.EVERYTHING || anyHovered, lineHighlit = anyHovered)
		}
		return out
	}

	fun planDrag(from: Int?, hovered: Int?, boxes: Map<Int, SlotBox>): Drag? {
		val fb = boxes[from ?: return null] ?: return null
		val to = hovered?.takeIf { it == from || isHotbar(it) != isHotbar(from) }
		val tb = if (to == null || to == from) null else boxes[to]
		return Drag(fb, isHotbar(from), tb, to?.let { isHotbar(it) } ?: false, onSelf = hovered == from)
	}

	fun planFrame(boxes: Map<Int, SlotBox>, state: LockState, hovered: Int?, mode: BindLinkStyle, dragging: Int? = null): Frame =
		Frame(
			plan(state.boundPairs, boxes, hovered, mode),
			boxes.mapNotNull { (i, b) -> state.lockAt(i)?.let { Lock(i, b, it) } },
			planDrag(dragging, hovered, boxes),
		)

	fun liveState(): LockState {
		val inv = net.minecraft.client.Minecraft.getInstance().player?.inventory
		return LockState.of(SlotLocking.globalData) { i -> inv?.getItem(i)?.view()?.itemUuid }
	}

	private fun color(highlit: Boolean) =
		if (highlit) me.shedaniel.math.Color.ofOpaque(0x00FF00) else me.shedaniel.math.Color.ofTransparent(NORMAL)

	fun drawLinks(ctx: GuiGraphicsExtractor, frame: Frame, mouseX: Int, mouseY: Int) {
		for (l in frame.links) {
			if (l.line) {
				val (ix, iy) = l.invBox.anchor(false)
				val (hx, hy) = l.hotBox.anchor(true)
				ctx.lineBetween(ix, iy, hx, hy, color(l.lineHighlit))
			}
			ctx.outlineBox(l.hotBox.x, l.hotBox.y, l.hotBox.size, l.hotBox.size, color(l.hotHighlit).color)
			ctx.outlineBox(l.invBox.x, l.invBox.y, l.invBox.size, l.invBox.size, color(l.invHighlit).color)
		}
		val d = frame.drag ?: return
		ctx.outlineBox(d.from.x, d.from.y, d.from.size, d.from.size, HIGHLIT)
		val (fx, fy) = d.from.anchor(d.fromHotbar)
		val green = me.shedaniel.math.Color.ofOpaque(0x00FF00)
		if (d.to == null) {
			if (!d.onSelf) ctx.lineBetween(fx, fy, mouseX, mouseY, green)
		} else {
			val (tx, ty) = d.to.anchor(d.toHotbar)
			ctx.lineBetween(fx, fy, tx, ty, green)
			ctx.outlineBox(d.to.x, d.to.y, d.to.size, d.to.size, HIGHLIT)
		}
	}

	fun drawLocks(ctx: GuiGraphicsExtractor, frame: Frame) {
		for (l in frame.locks) if (l.box.standIn) SlotLocking.drawLockSprite(ctx, l.kind, l.box.x, l.box.y, l.box.size)
	}

	fun inventoryIndex(slot: Slot?): Int? = slot?.takeIf { it.container is Inventory }?.containerSlot?.takeIf { it in 0..35 }

	fun realSlotBoxes(screen: AbstractContainerScreen<*>): Map<Int, SlotBox> {
		val acc = screen.castAccessor()
		val slots = screen.menu.slots.mapNotNull { s -> inventoryIndex(s)?.let { Triple(it, s.x, s.y) } }
		return fromRealSlots(slots, acc.x_Apex, acc.y_Apex)
	}

	@JvmStatic
	fun afterCustomGui(screen: Screen, gui: CustomGui, ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val hs = screen as? AbstractContainerScreen<*> ?: return
		runCatching {
			val boxes = gui.slotLinkBoxes(hs)
			if (boxes.isEmpty()) return
			val acc = hs.castAccessor()
			val hovered = inventoryIndex(acc.focusedSlot_Apex)?.takeIf { it in boxes }
				?: hovered(boxes, mouseX.toDouble(), mouseY.toDouble())
			val frame = planFrame(boxes, liveState(), hovered, SlotLocking.TConfig.bindLines, inventoryIndex(SlotLocking.pendingBindSlot))
			drawLinks(ctx, frame, mouseX, mouseY)
			drawLocks(ctx, frame)
		}
	}
}
