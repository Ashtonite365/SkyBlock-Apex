// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.ours

import kotlin.random.Random
import net.minecraft.ChatFormatting
import net.minecraft.nbt.ByteArrayTag
import net.minecraft.nbt.ByteTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.IntArrayTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.LongArrayTag
import net.minecraft.nbt.LongTag
import net.minecraft.nbt.ShortTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

class Fuzz(seed: Long) {
	val r = Random(seed)

	fun pick(s: String): Char = s[r.nextInt(s.length)]
	fun <T> pick(l: List<T>): T = l[r.nextInt(l.size)]

	fun str(alphabet: String, maxLen: Int, minLen: Int = 0): String {
		val n = minLen + r.nextInt(maxLen - minLen + 1)
		return buildString { repeat(n) { append(pick(alphabet)) } }
	}

	val formatty = "ab §§§0123456789abcdefklmnorKLMNOR_xyz,.:-"

	fun style(): Style {
		var s = Style.EMPTY
		when (r.nextInt(4)) {
			0 -> s = s.withColor(pick(ChatFormatting.entries.filter { TextColor.fromLegacyFormat(it) != null }))
			1 -> s = s.withColor(TextColor.fromRgb(r.nextInt(0x1000000)))
			else -> {}
		}
		fun tri(): Boolean? = when (r.nextInt(3)) { 0 -> null; 1 -> true; else -> false }
		tri()?.let { s = s.withBold(it) }
		tri()?.let { s = s.withItalic(it) }
		tri()?.let { s = s.withUnderlined(it) }
		tri()?.let { s = s.withStrikethrough(it) }
		tri()?.let { s = s.withObfuscated(it) }
		return s
	}

	fun component(depth: Int = 0): MutableComponent {
		val base: MutableComponent = when (r.nextInt(if (depth > 2) 3 else 5)) {
			0 -> Component.literal("")
			1, 2 -> Component.literal(str(formatty, 8))
			3 -> Component.translatableWithFallback("apex.fuzz.k" + r.nextInt(3), if (r.nextBoolean()) null else "fb%s", Component.literal(str(formatty, 4)))
			else -> Component.translatable("apex.fuzz.%s", component(depth + 1))
		}
		base.setStyle(style())
		if (depth < 3) repeat(r.nextInt(if (depth == 0) 4 else 3)) { base.append(component(depth + 1)) }
		return base
	}

	fun tag(depth: Int = 0, arrays: Boolean = true): Tag {
		val kinds = if (depth > 3) 7 else 9 + (if (arrays) 3 else 0)
		return when (r.nextInt(kinds)) {
			0 -> ByteTag.valueOf(r.nextInt(256).toByte())
			1 -> ShortTag.valueOf(r.nextInt(65536).toShort())
			2 -> IntTag.valueOf(if (r.nextBoolean()) r.nextInt(-1000, 1000) else r.nextInt())
			3 -> LongTag.valueOf(if (r.nextBoolean()) r.nextLong(-1000, 1000) else r.nextLong())
			4 -> FloatTag.valueOf(pick(listOf(0f, -0f, 1.5f, r.nextFloat() * 1000 - 500, Float.MAX_VALUE, 1e-7f, 3f)))
			5 -> DoubleTag.valueOf(pick(listOf(0.0, -2.25, r.nextDouble() * 1e6 - 5e5, 1e20, 1e-9, 7.0)))
			6 -> StringTag.valueOf(str("abc XYZ\"\\'\n\t§{}[],:;=é0123", 10))
			7 -> CompoundTag().also { c -> repeat(r.nextInt(5)) { c.put(key(), tag(depth + 1, arrays)) } }
			8 -> ListTag().also { l ->
				val homogeneous = r.nextBoolean()
				val proto = r.nextInt(4)
				repeat(r.nextInt(5)) {
					l.add(if (homogeneous) when (proto) {
						0 -> IntTag.valueOf(r.nextInt(100)); 1 -> StringTag.valueOf(str("abc", 3)); 2 -> CompoundTag().also { it.putInt(key(), 1) }
						else -> DoubleTag.valueOf(r.nextDouble())
					} else tag(depth + 1, arrays))
				}
			}
			9 -> ByteArrayTag(ByteArray(r.nextInt(4)) { r.nextInt(256).toByte() })
			10 -> IntArrayTag(IntArray(r.nextInt(4)) { r.nextInt() })
			else -> LongArrayTag(LongArray(r.nextInt(4)) { r.nextLong() })
		}
	}

	fun key(): String = when (r.nextInt(4)) {
		0 -> str("abcdefghijklmnopqrstuvwxyzABC_0123456789.+-", 8, 1)
		1 -> str("ab cd\"\\:é{}", 5)
		2 -> pick(listOf("id", "Count", "tag", "ExtraAttributes", "display", "Lore", "Name", "", "0"))
		else -> str("xyzXYZ_", 4, 1)
	}

	fun compound(): CompoundTag = CompoundTag().also { c -> repeat(1 + r.nextInt(5)) { c.put(key(), tag(1)) } }
}
