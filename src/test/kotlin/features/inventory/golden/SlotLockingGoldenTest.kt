// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.inventory.golden

import java.util.UUID
import net.minecraft.world.inventory.ContainerInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.features.inventory.GlobalSlotLocks
import dev.ashtonite365.apex.features.inventory.LegacyIslandLocks
import dev.ashtonite365.apex.features.inventory.LegacyProfileLocks
import dev.ashtonite365.apex.features.inventory.LockKeyGesture
import dev.ashtonite365.apex.features.inventory.SlotBinding
import dev.ashtonite365.apex.features.inventory.SlotBindings
import dev.ashtonite365.apex.features.inventory.SlotLockRules
import dev.ashtonite365.apex.features.inventory.SlotLockStore

@org.junit.jupiter.api.extension.ExtendWith(dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension::class)
class SlotLockingGoldenTest {
	private fun SlotBindings.text() = pairs.joinToString(" ") { "${it.hotbar}-${it.inventory}" }

	@Test
	fun dataFormats() {
		val n = Golden.check("slot-locking-data") { key ->
			val kind = key.substringBefore(' ')
			val arg = key.substringAfter(' ')
			when (kind) {
				"bound" -> runCatching {
					val b = Apex.json.decodeFromString(SlotBindings.serializer(), arg)
					b.text() + " => " + Apex.json.encodeToString(SlotBindings.serializer(), b)
				}.getOrElse { "fail" }
				"global" -> runCatching {
					SlotLockStore.json.encodeToString(GlobalSlotLocks.serializer(), SlotLockStore.json.decodeFromString(GlobalSlotLocks.serializer(), arg))
				}.getOrElse { "fail" }
				"legacy" -> runCatching {
					Apex.json.encodeToString(LegacyProfileLocks.serializer(), Apex.json.decodeFromString(LegacyProfileLocks.serializer(), arg))
				}.getOrElse { "fail" }
				"legacy-encode" -> Apex.json.encodeToString(LegacyProfileLocks.serializer(), LegacyProfileLocks(
					mutableSetOf(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")),
					LegacyIslandLocks(mutableSetOf(3, 1), SlotBindings(mutableSetOf(SlotBinding(0, 9))))))
				"ops" -> {
					val b = SlotBindings()
					arg.split(' ').joinToString(" ; ") { op ->
						val nums = op.drop(1).split(',').map(String::toInt)
						val shown = when (op[0]) {
							'f' -> b.involving(nums[0]).joinToString("+") { "${it.hotbar}-${it.inventory}" }
							'd' -> "".also { b.releaseInventorySlot(nums[0]) }
							'r' -> b.unbind(nums[0]).toString()
							else -> "".also { b.bind(nums[0], nums[1], exclusive = false) }
						}
						shown + "|" + b.text()
					}
				}
				else -> error(kind)
			}
		}
		assertEquals(31, n)
	}

	@Test
	fun protectionRules() {
		val n = Golden.check("slot-locking-rules") { key ->
			val kind = key.substringBefore(' ')
			val f = key.substringAfter(' ').split(',')
			fun b(i: Int) = f[i].toBooleanStrict()
			when (kind) {
				"uuid" -> {
					val guarded = SlotLockRules.itemGuarded(ContainerInput.valueOf(f[0]), b(1), b(2), b(4)) {
						if (b(3)) null else SlotLockRules.ItemFacts(huntingBox = b(5), uuidLocked = b(6) && b(7))
					}
					if (guarded) "protect" else "pass"
				}
				"slot" -> SlotLockRules.slotGuarded(b(0), b(1), b(2), b(3)).toString()
				"quick" -> when (SlotLockRules.boundClick(ContainerInput.valueOf(f[0]), b(1), b(2), b(3), b(4), b(5), f[6].toInt())) {
					SlotLockRules.BoundClick.IGNORE -> "pass"
					SlotLockRules.BoundClick.SWAP_WITH_PARTNER -> "silent+swap"
					SlotLockRules.BoundClick.CANCEL -> "silent"
				}
				"dungeon" -> SlotLockRules.keepsProtectionInDungeon(b(0), b(1), b(2), b(3)).toString()
				"screen" -> {
					val menu = SlotLockRules.MenuFacts(b(0), f[1].toInt(), f[2], f[3].ifEmpty { null }, f[4].ifEmpty { null })
					"salvage=${SlotLockRules.salvageMenu(menu.title)} trade=${SlotLockRules.tradeMenu(menu)} npc=${SlotLockRules.npcShop(menu)}"
				}
				else -> error(kind)
			}
		}
		assertEquals(1613, n)
	}

	private class FakeSlot(val label: String) {
		val index = label.removePrefix("c").toInt()
		val player = !label.startsWith("c")
	}

	@Test
	fun keyGestures() {
		val n = Golden.check("slot-locking-gestures") { key ->
			val (head, script) = key.split('|')
			val (sameKey, multi, keyName) = head.split(',')
			val bindKey = sameKey == "true" || keyName == "B"
			val lockKey = sameKey == "true" || keyName == "L"
			val locks = GlobalSlotLocks()
			val log = mutableListOf<String>()

			val slots = HashMap<String, FakeSlot>()
			val gesture = LockKeyGesture<FakeSlot> { it.index }
			for (ev in script.split(' ')) {
				val slot = ev.drop(1).takeUnless { it == "-" }?.let { slots.getOrPut(it) { FakeSlot(it) } }
				val effect = if (ev[0] == 'P') gesture.pressed(slot, slot?.player == true, bindKey, lockKey)
				else gesture.released(slot, bindKey, lockKey)
				if (effect != null && effect.applyTo(locks, exclusiveBinds = multi != "true"))
					log += effect.javaClass.simpleName.lowercase()
			}
			val bound = locks.boundSlots.pairs.sortedWith(compareBy({ it.hotbar }, { it.inventory })).joinToString(" ") { "${it.hotbar}-${it.inventory}" }
			"locked=${locks.lockedSlots.sorted()} bound=$bound stored=${gesture.remembered?.label} log=$log"
		}
		assertEquals(108, n)
	}
}
