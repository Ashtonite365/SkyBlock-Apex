// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.testutil

import com.mojang.datafixers.DSL
import com.mojang.serialization.Codec
import com.mojang.serialization.Dynamic
import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.util.datafix.DataFixers
import net.minecraft.util.datafix.fixes.References
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.TagParser
import net.minecraft.resources.RegistryOps
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import dev.ashtonite365.apex.features.debug.ExportedTestConstantMeta
import dev.ashtonite365.apex.test.ApexTestBootstrap
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.mc.KeptStack
import dev.ashtonite365.apex.util.mc.TabListReader

object ItemResources {

	private const val TEXT_AS_STRING_SINCE = 4325

	init { ApexTestBootstrap.bootstrapMinecraft() }

	fun loadString(path: String): String {
		require(path.firstOrNull() != '/')
		val stream = ItemResources::class.java.classLoader.getResourceAsStream(path) ?: throw NullPointerException("missing test resource $path")
		return stream.use { String(it.readAllBytes(), Charsets.UTF_8) }
	}

	fun loadSNbt(path: String): CompoundTag = TagParser.parseCompoundFully(loadString(path))

	fun getNbtOps(): RegistryOps<Tag> = Client.registries.createSerializationContext(NbtOps.INSTANCE)

	fun tryMigrateNbt(nbtCompound: CompoundTag, typ: DSL.TypeReference?): Tag {
		val stamp = nbtCompound.read("source", ExportedTestConstantMeta.CODEC).orElse(null)
		nbtCompound.remove("source")
		if (stamp == null) return nbtCompound
		val legacyText = typ == References.TEXT_COMPONENT && stamp.dataVersion < TEXT_AS_STRING_SINCE
		val input: Tag = if (legacyText) StringTag.valueOf(NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, nbtCompound).toString()) else nbtCompound
		if (typ == null) return input
		val target = SharedConstants.getCurrentVersion().dataVersion().version
		return DataFixers.getDataFixer().update(typ, Dynamic(NbtOps.INSTANCE, input), stamp.dataVersion, target).value
	}

	private fun <T> decode(codec: Codec<T>, file: String, fixAs: DSL.TypeReference?, what: String): T =
		codec.parse(getNbtOps(), tryMigrateNbt(loadSNbt(file), fixAs)).getOrThrow { IllegalStateException("$what: $it") }

	fun loadTablist(name: String): TabListReader.TabSnapshot =
		decode(TabListReader.TabSnapshot.CODEC, "testdata/tablist/$name.snbt", null, "Could not load tablist '$name'")

	fun loadText(name: String): Component =
		decode(ComponentSerialization.CODEC, "testdata/chat/$name.snbt", References.TEXT_COMPONENT, "Could not load test chat '$name'")

	fun loadItem(name: String): KeptStack = runCatching {
		KeptStack.CODEC.parse(getNbtOps(), tryMigrateNbt(loadSNbt("testdata/items/$name.snbt"), References.ITEM_STACK)).orThrow
	}.getOrElse { problem -> if (problem is Exception) throw RuntimeException("Could not load item resource '$name'", problem) else throw problem }
}
