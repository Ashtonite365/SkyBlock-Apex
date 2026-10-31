// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.ours

import com.mojang.authlib.minecraft.MinecraftProfileTexture
import com.mojang.authlib.properties.Property
import com.mojang.serialization.JsonOps
import io.github.moulberry.repo.data.Rarity
import io.netty.buffer.Unpooled
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlinx.serialization.json.Json
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.StringTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import util.mc.FakeInventory
import dev.ashtonite365.apex.test.util.ours.Golden.Companion.golden
import dev.ashtonite365.apex.util.PetInfoJson
import dev.ashtonite365.apex.util.ServerLocation
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ReforgeKey
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.blockPos
import dev.ashtonite365.apex.util.center
import dev.ashtonite365.apex.util.customDataTag
import dev.ashtonite365.apex.util.formattedString
import dev.ashtonite365.apex.util.statedAmount
import dev.ashtonite365.apex.util.reforgeKey
import dev.ashtonite365.apex.util.starCount
import dev.ashtonite365.apex.util.guessIdFromMenu
import dev.ashtonite365.apex.util.updateCustomData
import dev.ashtonite365.apex.util.petBlob
import dev.ashtonite365.apex.util.hypixelIdText
import dev.ashtonite365.apex.util.labelName
import dev.ashtonite365.apex.util.withApexUiId
import dev.ashtonite365.apex.util.withItemId
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.itemUuidText
import dev.ashtonite365.apex.util.itemUuid
import dev.ashtonite365.apex.util.timestamp
import dev.ashtonite365.apex.util.mc.NbtSnapshotGuard
import dev.ashtonite365.apex.util.mc.ItemView
import dev.ashtonite365.apex.util.mc.PatchView
import dev.ashtonite365.apex.util.mc.FakeSlot
import dev.ashtonite365.apex.util.mc.StartupStage
import dev.ashtonite365.apex.util.mc.KeptStack
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.mc.SkinPayload
import dev.ashtonite365.apex.util.mc.TextureRef
import dev.ashtonite365.apex.util.mc.ItemDraft
import dev.ashtonite365.apex.util.mc.NoComponents
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.ContainerSlotLookup
import dev.ashtonite365.apex.util.mc.SlotUtils
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.appendLore
import dev.ashtonite365.apex.util.mc.placeholderHeadUuid
import dev.ashtonite365.apex.util.mc.asScreenRectangle
import dev.ashtonite365.apex.util.mc.skullWithSkin
import dev.ashtonite365.apex.util.mc.texturesPropertyOf
import dev.ashtonite365.apex.util.mc.readTexturesProperty
import dev.ashtonite365.apex.util.mc.typeDefaultStack
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.mc.indices
import dev.ashtonite365.apex.util.mc.isEmpty
import dev.ashtonite365.apex.util.mc.isOfItem
import dev.ashtonite365.apex.util.mc.iterableView
import dev.ashtonite365.apex.util.mc.keepable
import dev.ashtonite365.apex.util.mc.loadItemFromNbt
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.mc.modifyLore
import dev.ashtonite365.apex.util.mc.itemEditor
import dev.ashtonite365.apex.util.mc.setCustomName
import dev.ashtonite365.apex.util.mc.applyEncodedSkin
import dev.ashtonite365.apex.util.mc.applySkinUrl
import dev.ashtonite365.apex.util.mc.smuggleNull
import dev.ashtonite365.apex.util.mc.toPatchBuilder
import dev.ashtonite365.apex.util.mc.toNbtList
import dev.ashtonite365.apex.util.mc.unsafeNbt
import dev.ashtonite365.apex.util.mc.CommonTextures
import dev.ashtonite365.apex.util.mc.LenientRegistryOps
import dev.ashtonite365.apex.util.mc.ApexDataComponentTypes
import dev.ashtonite365.apex.util.mc.ApexDataComponentTypes.neverEncode

@ExtendWith(AutoBootstrapExtension::class)
@OptIn(NeedsComponents::class)
class ItemUtilsGoldenTest {
	private fun stack(extra: CompoundTag, item: net.minecraft.world.level.ItemLike = Items.PAPER, lore: List<String> = listOf(), count: Int = 1): ItemView =
		ItemStack(item, count).also { s ->
			s.set(DataComponents.CUSTOM_DATA, CustomData.of(extra))
			if (lore.isNotEmpty()) s.itemEditor().loreAccordingToNbt = lore.map { Component.literal(it) }
		}.view()

	private fun ea(vararg kv: Pair<String, Any?>) = CompoundTag().also { t ->
		for ((k, v) in kv) when (v) {
			null -> {}
			is String -> t.putString(k, v)
			is Int -> t.putInt(k, v)
			is Long -> t.putLong(k, v)
			is CompoundTag -> t.put(k, v)
			else -> error(v)
		}
	}

	private fun c(vararg kv: Pair<String, Int>) = CompoundTag().also { t -> kv.forEach { t.putInt(it.first, it.second) } }

	@Test
	fun skyblockIds() = golden("item-skyblockid") {
		val f = Fuzz(41)
		val cores = listOf("", "ASPECT_OF_THE_END", "PET;4", "a:b", "Ä", "x y", "minus-sign", "dot.slash/", "MIXED_case", "\uD83D\uDE00", "tab\t", "semi;colon:colon", "İ", "ß", "UPPER0_9") +
			List(80) { f.str("AZaz09;:_-./ é§\u0130\uD83D", 8) }
		for (s in cores) {
			val id = ItemId(s)
			case("ident $s") { id.identifier }
			case("str $s") { id.toString() + "|" + id.skyblockId + "|" + id.hashCode() }
			case("codec $s") { ItemId.CODEC.encodeStart(JsonOps.INSTANCE, id).result().get().toString() + " " + ItemId.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonPrimitive(s)).result().get() }
			case("packet $s") {
				val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY)
				ItemId.PACKET_CODEC.encode(buf, id); val bytes = buf.readableBytes(); "$bytes ${ItemId.PACKET_CODEC.decode(buf)}"
			}
			case("cmp $s") { cores.map { ItemId(s).compareTo(ItemId(it)).coerceIn(-1, 1) } }
			case("json $s") { Json.encodeToString(ItemId.serializer(), id) }
		}
		case("consts") { listOf(ItemId.COINS, ItemId.EMPTY_SLOT, ItemId.NULL, ItemId.UNKNOWN_PET) }
		case("bazaarJson") { Json.encodeToString(ItemId.BazaarProduct.serializer(), ItemId.BazaarProduct("X")) }
		case("reforgeJson") { Json.encodeToString(ReforgeKey.serializer(), ReforgeKey("r")) }
		case("pet") { PetInfoJson("wolf", Rarity.LEGENDARY).let { listOf(it, it.catalogItemId) } }
		case("petJson") { Json.encodeToString(PetInfoJson.serializer(), PetInfoJson("wolf", Rarity.EPIC, 5.0, 2, UUID(1, 2), null, "X")) }
	}

	@Test
	fun itemIdMapping() = golden("item-idmapping") {
		if (runCatching { dev.ashtonite365.apex.features.items.ItemCustomizer.DConfig.data }.isFailure) dev.ashtonite365.apex.features.items.ItemCustomizer.DConfig.explicitDefaultLoad()
		val petJson = "{\"type\":\"ENDER_DRAGON\",\"tier\":\"LEGENDARY\",\"exp\":12.5,\"extra\":1}"
		val cases = listOf(
			ea(), ea("id" to ""), ea("id" to "HYPERION"), ea("id" to "a:b:c"), ea("id" to 5),
			ea("id" to "PET"), ea("id" to "PET", "petInfo" to petJson), ea("id" to "PET", "petInfo" to "{bad"), ea("id" to "PET", "petInfo" to "   "),
			ea("id" to "PET", "petInfo" to "{\"type\":\"x\",\"tier\":\"MYTHIC\"}"),
			ea("id" to "RUNE"), ea("id" to "RUNE", "runes" to c("ZAP" to 2)), ea("id" to "UNIQUE_RUNE", "runes" to c("spirit" to 3)),
			ea("id" to "RUNE", "runes" to c("A" to 1, "B" to 2)), ea("id" to "RUNE", "runes" to ea("x" to "notint")), ea("id" to "RUNE", "runes" to "str"),
			ea("id" to "ABICASE"), ea("id" to "ABICASE", "model" to "sumsung_1"),
			ea("id" to "ENCHANTED_BOOK"), ea("id" to "ENCHANTED_BOOK", "enchantments" to c("ultimate_wise" to 5)), ea("id" to "ENCHANTED_BOOK", "enchantments" to c("a" to 1, "b" to 1)),
			ea("id" to "ENCHANTED_BOOK", "enchantments" to CompoundTag()),
			ea("id" to "ATTRIBUTE_SHARD"), ea("id" to "ATTRIBUTE_SHARD", "attributes" to c("mana_pool" to 4)),
			ea("id" to "POTION"), ea("id" to "POTION", "potion" to "speed", "potion_level" to 3), ea("id" to "POTION", "potion_name" to "Spirit potion", "potion" to "x"),
			ea("id" to "POTION", "potion_type" to "Splash thing"), ea("id" to "POTION", "potion" to "x"), ea("id" to "POTION", "potion_level" to 2),
			ea("id" to "PARTY_HAT_SLOTH", "party_hat_emoji" to "smile"), ea("id" to "PARTY_HAT_CRAB", "party_hat_year" to 2022, "party_hat_color" to "red"),
			ea("id" to "PARTY_HAT_CRAB_ANIMATED", "party_hat_color" to "blue"), ea("id" to "CAKE_HAT_2026", "party_hat_color" to "pink"),
			ea("id" to "CAKE_HAT_2026", "party_hat_year" to 2022, "party_hat_color" to "pink"), ea("id" to "PARTY_HAT_CRAB"),
			ea("id" to "BALLOON_HAT_2024", "party_hat_year" to 2024, "party_hat_color" to "green"), ea("id" to "BALLOON_HAT_2025"),
			ea("id" to "X", "uuid" to "d3cb85e2-3075-48a1-b213-a9bfb62360c1"), ea("id" to "X", "uuid" to " "), ea("id" to "X", "uuid" to "nope"),
			ea("id" to "X", "timestamp" to 1700000000000L), ea("id" to "X", "timestamp" to "10/11/21 3:39 PM"), ea("id" to "X", "timestamp" to "1/2/99 12:05 am"),
			ea("id" to "X", "timestamp" to "garbage"), ea("id" to "X", "timestamp" to 5),
			ea("upgrade_level" to 3), ea("upgrade_level" to 0, "dungeon_item_level" to 2), ea("dungeon_item_level" to -1), ea("upgrade_level" to "x"),
			ea("modifier" to "fabled"), ea("modifier" to " "), ea("modifier" to 3),
		)
		for ((i, e) in cases.withIndex()) {
			val acc = stack(e)
			case("$i skyBlockId") { acc.repoItemId }
			case("$i raw") { acc.hypixelIdText }
			case("$i uuidStr") { acc.itemUuidText }
			case("$i uuid") { acc.itemUuid }
			case("$i ts") { acc.timestamp }
			case("$i pet") { acc.petBlob }
			case("$i stars") { acc.starCount() }
			case("$i reforge") { acc.reforgeKey() }
			case("$i ctx") { acc.guessIdFromMenu() }
			case("$i ea") { acc.customDataTag.toString() }
		}
		case("noCustomData") { ItemStack(Items.STONE).view().let { listOf(it.customDataTag.toString(), it.repoItemId, it.hypixelIdText) } }
		case("eaIsCopy") { val s = stack(ea("id" to "A")); val t = s.customDataTag; t.putString("id", "B"); s.hypixelIdText }
		case("setId") { ItemStack(Items.STONE).itemEditor().withItemId(ItemId("FOO")).let { it.hypixelIdText + " " + it.customDataTag } }
		case("setUi") { ItemStack(Items.STONE).itemEditor().withApexUiId("BAR").let { it.hypixelIdText } }
		case("modify") { val m = ItemStack(Items.STONE).itemEditor(); m.updateCustomData { it.putInt("x", 1) }; m.updateCustomData { it.putInt("y", 2) }; m.customDataTag.toString() }
		case("eaSetter") { val m = ItemStack(Items.STONE).itemEditor(); m.customDataTag = ea("q" to "r"); m.customDataTag.toString() }
		val lores = listOf(
			listOf(), listOf("nothing"), listOf(" Amount: 1,234"), listOf("Stored: 5k/10k"), listOf("Offer amount: 64x"), listOf("Order amount: 1.5kx"),
			listOf("§7Amount: 12x"), listOf("Compost Available: 3"), listOf("Stored: 5/"), listOf("x", "Compost Available: 9", " Amount: 2"),
			listOf(" Amount: 1.2M"), listOf("Amount: 3bx"), listOf("Stored: 1,000,000/2"), listOf("Amount: 12"), listOf(" Amount: 12x"),
		)
		for ((i, l) in lores.withIndex()) for (cnt in listOf(1, 7)) case("logical $i $cnt") { stack(ea(), lore = l, count = cnt).statedAmount() }
		case("renderingName") { stack(ea()).labelName.toString() }
		case("renderingNameCustom") { ItemStack(Items.STONE).also { it.itemEditor().setCustomName(Component.literal("hi")) }.view().labelName.toString() }
		case("renderingNameEmptyLazy") { KeptStack.empty().labelName.toString() }
	}

	@Test
	fun lazyStacks() = golden("item-lazystack") {
		val base = ItemStack(Items.DIAMOND_SWORD, 3).also { it.set(DataComponents.CUSTOM_NAME, Component.literal("n")); it.remove(DataComponents.ATTRIBUTE_MODIFIERS) }
		fun d(a: ItemView) = listOf(a.itemType.unwrapKey().get().identifier(), a.itemKey.identifier(), a.amount, a.get(DataComponents.CUSTOM_NAME), a.get(DataComponents.MAX_STACK_SIZE),
			a.effective(DataComponents.MAX_STACK_SIZE), a.get(DataComponents.ATTRIBUTE_MODIFIERS)?.let { "attr" }, a.effective(DataComponents.ATTRIBUTE_MODIFIERS)?.let { "attr" },
			a.isEmpty(), a.isOfItem(Items.DIAMOND_SWORD), a.isOfItem(Items.AIR), a.backingStack()?.count, a.displayNameAccordingToNbt, a.loreAccordingToNbt)
		case("fromStack") { d(base.keepable()) }
		case("accessor") { d(base.view()) }
		case("empty") { d(KeptStack.empty()) }
		case("emptyStack") { d(ItemStack.EMPTY.keepable()) }
		case("fromTemplateNull") { d(KeptStack.ofTemplate(null)) }
		case("build") { d(KeptStack.build(Items.APPLE) { setCustomName(Component.literal("a")) }) }
		case("buildAir") { d(KeptStack.build(Items.AIR)) }
		case("buildHolder") { d(KeptStack.build(Items.STICK.builtInRegistryHolder())) }
		case("withCount") { val l = base.keepable(); listOf(d(l.withCount(9)), d(l), d(KeptStack.build(Items.APPLE).withCount(0)), d(KeptStack.empty().withCount(5))) }
		case("template") { listOf(base.keepable().frozenTemplate()?.count, ItemStack.EMPTY.keepable().frozenTemplate(), KeptStack.empty().frozenTemplate()) }
		case("intoMutable") { listOf(d(base.keepable().toDraft()), d(KeptStack.build(Items.APPLE).toDraft()), d(KeptStack.empty().toDraft())) }
		case("withMutations") { d(base.keepable().editedCopy { amount = 0 }) to d(base.keepable().editedCopy { amount = 2; setCustomName(Component.literal("m")) }) }
		case("recreate") { val l = base.keepable(); l.dropLive(); listOf(l.backingStack(), l.rebuildLive().count, l.backingStack()?.count, l.current() === l.backingStack(), l.currentCopy() === l.backingStack()) }
		case("upgradeEmpty") { KeptStack.empty().current().isEmpty }
		case("typeHolder") { KeptStack.build(Items.APPLE).typeHolder().unwrapKey().get().identifier() }
		case("codec") {
			val l = KeptStack.build(Items.APPLE) { setCustomName(Component.literal("c")) }.withCount(4)
			val ops = net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, Client.builtinRegistries)
			val enc = KeptStack.CODEC.encodeStart(ops, l).result().get()
			val encEmpty = KeptStack.CODEC.encodeStart(ops, KeptStack.empty()).result().get()
			listOf(enc, encEmpty, d(KeptStack.CODEC.parse(ops, enc).result().get()), d(KeptStack.CODEC.parse(ops, encEmpty).result().get()))
		}
		case("mutable") {
			val m = ItemDraft(Items.APPLE.builtInRegistryHolder(), 2, DataComponentPatch.builder())
			m.set(DataComponents.MAX_STACK_SIZE, 64); m.set(DataComponents.CUSTOM_NAME, Component.literal("x")); m.remove(DataComponents.FOOD)
			val before = m.edits.build().toString()
			m.dropDefaults()
			listOf(before, m.edits.build().toString(), d(m), m.toTemplate().count, d(m.toStack()))
		}
		case("mutableEmpty") { d(ItemDraft(Items.APPLE.builtInRegistryHolder(), 0, DataComponentPatch.builder()).toStack()) }
		case("toBuilder") {
			val p = DataComponentPatch.builder().set(DataComponents.MAX_STACK_SIZE, 3).remove(DataComponents.FOOD).build()
			p.toPatchBuilder().build().toString() + " " + (p.toPatchBuilder().build() == p)
		}
		case("patchAccessor") { d(PatchView(Items.BREAD.builtInRegistryHolder(), DataComponentPatch.builder().set(DataComponents.MAX_STACK_SIZE, 2).build())) }
		case("nullData") { NoComponents.get(DataComponents.MAX_STACK_SIZE) }
		case("defaultStack") { KeptStack.build(Items.APPLE).typeDefaultStack().let { "${it.item} ${it.count}" } }
		case("mutator") { val s = ItemStack(Items.STONE); val m = s.itemEditor(); m.set(DataComponents.MAX_STACK_SIZE, 5); m.remove(DataComponents.ITEM_NAME); listOf(s.maxStackSize, s.get(DataComponents.ITEM_NAME), d(m)) }
		case("lore") {
			val m = ItemStack(Items.STONE).itemEditor()
			m.appendLore(listOf()); val a = m.get(DataComponents.LORE)
			m.appendLore(listOf(Component.literal("1"), Component.literal("2")))
			m.modifyLore { it.reversed() + Component.literal("3") }
			listOf(a, m.loreAccordingToNbt, m.displayNameAccordingToNbt)
		}
		case("displayName") {
			val s = ItemStack(Items.STONE); val m = s.itemEditor()
			val a = m.displayNameAccordingToNbt
			s.set(DataComponents.ITEM_NAME, Component.literal("item"))
			val b = m.displayNameAccordingToNbt
			m.displayNameAccordingToNbt = Component.literal("custom")
			listOf(a, b, m.displayNameAccordingToNbt)
		}
		case("loadNbt") {
			val ok = CompoundTag().also { it.putString("id", "minecraft:apple"); it.putInt("count", 3) }
			listOf(loadItemFromNbt(ok)?.let { d(it) }, loadItemFromNbt(CompoundTag()), loadItemFromNbt(CompoundTag().also { it.putString("id", "minecraft:nope") }))
		}
		case("nbtList") { listOf(IntTag.valueOf(1), StringTag.valueOf("a")).toNbtList().toString() + " " + emptyList<net.minecraft.nbt.Tag>().toNbtList() }
		case("unsafeNbt") { val t = ea("a" to 1); val cd = CustomData.of(t); val u = cd.unsafeNbt; listOf(u.toString(), u === cd.unsafeNbt) }
		case("mutationCheck") {
			val t = ea("a" to 1)
			val copy = NbtSnapshotGuard.watchedCopy(t)
			listOf(copy === t, copy == t, NbtSnapshotGuard.SetterHolder.snapshotSetter != null)
		}
		case("mutationCheckLog") { NbtSnapshotGuard.compareOnFinalize(ea("a" to 1), ea("a" to 1)); NbtSnapshotGuard.compareOnFinalize(ea("a" to 2), ea("a" to 1)); "ok" }
	}

	@Test
	fun skullsAndMisc() = golden("item-skulls-misc") {
		val payload = SkinPayload(
			mapOf(MinecraftProfileTexture.Type.SKIN to TextureRef("http://textures.minecraft.net/texture/abc", mapOf("model" to "slim"))),
			UUID(5, 6), "Name", false, Instant.ofEpochMilli(1234567890123L)
		)
		case("payload") { payload.toString() }
		case("payloadDefaults") { SkinPayload().let { listOf(it.byKind, it.ownerId, it.ownerName, it.publicSkin) } }
		case("texDefaults") { TextureRef("u") }
		val props = texturesPropertyOf(payload)
		case("props") { props.entries().map { "${it.key}=${it.value.name}|${it.value.value}|${it.value.signature}" } }
		case("decode") { readTexturesProperty(props.get("textures").single())?.let { if (it.issuedAt == payload.issuedAt) it else it.copy(issuedAt = Instant.EPOCH) } }
		for (v in listOf("", "e30", "e30=", "e30==", "e30===", "!!!", "eyJ0ZXh0dXJlcyI6e319", "eyJ0ZXh0dXJlcyI6e319=", "bnVsbA==", "W10=")) {
			case("decode $v") { readTexturesProperty(Property("textures", v))?.copy(issuedAt = Instant.EPOCH) }
		}
		case("decodeWrongName") { readTexturesProperty(Property("other", "e30=")) }
		case("arbitrary") { placeholderHeadUuid }
		case("encodedOwner") {
			val s = ItemStack(Items.PLAYER_HEAD); s.applyEncodedSkin(UUID(1, 2), "e30")
			val p = s.get(DataComponents.PROFILE)!!.partialProfile()
			listOf(p.id, p.name, p.properties.entries().map { "${it.key}=${it.value.value}|${it.value.signature}" })
		}
		fun skullDump(s: ItemView) = s.get(DataComponents.PROFILE)!!.partialProfile().let { p ->
			listOf(p.id, p.name, p.properties.entries().map { e -> e.key + "=" + readTexturesProperty(e.value)?.copy(issuedAt = Instant.EPOCH) })
		}
		case("skullItem") { skullWithSkin(UUID(3, 4), "http://x/y").let { listOf(it.amount, it.itemType.unwrapKey().get().identifier(), skullDump(it)) } }
		case("setSkullOwner") { val s = ItemStack(Items.PLAYER_HEAD); s.itemEditor().applySkinUrl(UUID(7, 8), "u"); skullDump(s.view()) }
		case("skullJson") { dev.ashtonite365.apex.Apex.json.encodeToString(SkinPayload.serializer(), payload) }
		case("skullJsonDecode") { dev.ashtonite365.apex.Apex.json.decodeFromString(SkinPayload.serializer(), "{\"textures\":{\"CAPE\":{\"url\":\"c\"}},\"profileId\":\"00000000000000010000000000000002\",\"timestamp\":5}") }
		case("smuggle") { val x: String = smuggleNull(); (x as Any?) == null }
		case("initLevels") { StartupStage.entries.map { it.name } }
		case("initAtLeast") { StartupStage.entries.map { StartupStage.isAtLeast(it) } }
		case("initBumpBad") { StartupStage.advanceTo(StartupStage.entries.last()); StartupStage.current }
		case("slotConsts") { listOf(SlotUtils.PACKET_LEFT, SlotUtils.PACKET_RIGHT, SlotUtils.PACKET_MIDDLE) }
		case("fakeSlot") {
			val st = ItemStack(Items.APPLE, 2)
			val s = FakeSlot(st, 5, 6)
			val inv = s.container
			listOf(s.index, s.x, s.y, s.item === st, s.containerSlot, inv.containerSize, inv.isEmpty, inv.getItem(0) === st, inv.removeItem(0, 1).isEmpty,
				inv.removeItemNoUpdate(0).isEmpty, inv.javaClass.name, st.count, runCatching { inv.getItem(1) }.exceptionOrNull()?.javaClass?.name,
				run { inv.setItem(0, ItemStack.EMPTY); inv.clearContent(); inv.setChanged(); inv.getItem(0) === st })
		}
		case("fakeInvEmpty") { FakeInventory(ItemStack.EMPTY).let { listOf(it.isEmpty, it.containerSize) } }
		case("container") {
			val c = SimpleContainer(ItemStack(Items.APPLE), ItemStack.EMPTY, ItemStack(Items.STONE, 3))
			listOf(c.indices.toList(), c.iterableView.map { "${it.item}x${it.count}" }, c.iterableView.spliterator().estimateSize(),
				runCatching { val it = c.iterableView.iterator(); repeat(4) { _ -> it.next() } }.exceptionOrNull()?.javaClass?.name)
		}
		case("slotIndex") { ContainerSlotLookup.SlotKey(3, true).let { listOf(it, it == ContainerSlotLookup.SlotKey(3, true), it.copy(index = 4)) } }
		case("tabList") {
			val t = TabListReader.TabSnapshot(Optional.of(Component.literal("h")), Optional.empty(), listOf(Component.literal("a"), Component.literal("b")))
			val ops = net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, Client.builtinRegistries)
			val enc = TabListReader.TabSnapshot.CODEC.encodeStart(ops, t).result().get()
			listOf(enc, TabListReader.TabSnapshot.CODEC.parse(ops, enc).result().get() == t, t)
		}
		case("tabListCache") {
			val t = TabListReader.TabSnapshot(Optional.empty(), Optional.empty(), listOf())
			TabListReader.cachedSnapshot = t
			val same = TabListReader.currentSnapshot === t
			TabListReader.cachedSnapshot = null
			same
		}
		case("rect") { me.shedaniel.math.Rectangle(1, 2, 30, 40).asScreenRectangle().toString() }
		case("textures") { CommonTextures.genericWidget() }
		case("tolerant") {
			val ops = LenientRegistryOps(NbtOps.INSTANCE, Client.builtinRegistries)
			listOf(LenientRegistryOps.PermissiveOwner<Any>().canSerialize(LenientRegistryOps.PermissiveOwner()), ops.javaClass.superclass.simpleName,
				ops.getter(Registries.ITEM).isPresent)
		}
		case("errorCodec") {
			val codec = ApexDataComponentTypes.errorCodec<String>("nope")
			listOf(runCatching { codec.decode(Unpooled.buffer()) }.exceptionOrNull()?.message, runCatching { codec.encode(Unpooled.buffer(), "x") }.exceptionOrNull()?.message)
		}
		case("neverEncode") {
			val b = net.minecraft.core.component.DataComponentType.builder<String>()
			(b.neverEncode() === b)
		}
		case("locraw") {
			listOf(ServerLocation("mini1", "SKYBLOCK", "hub").let { listOf(it, it.skyblockIsland === SkyBlockIsland.HUB) },
				ServerLocation("x", "BEDWARS", "hub").skyblockIsland, ServerLocation("x", "SKYBLOCK", null).skyblockIsland, ServerLocation("x").skyblockIsland,
				Json.encodeToString(ServerLocation.serializer(), ServerLocation("s", "SKYBLOCK", "garden", "Garden")),
				Json.decodeFromString(ServerLocation.serializer(), "{\"server\":\"a\",\"gametype\":\"SKYBLOCK\",\"mode\":\"rift\"}").skyblockIsland === SkyBlockIsland.RIFT)
		}
		case("sbdata") {
			val before = SkyBlockSession.serverLocation
			val out = ArrayList<Any?>()
			for (l in listOf(null, ServerLocation("limbo"), ServerLocation("mini1", "SKYBLOCK", "garden"), ServerLocation("lobby", "MAIN"))) {
				SkyBlockSession.serverLocation = l
				SkyBlockSession.profileId = UUID(9, 9)
				out.add(listOf(SkyBlockSession.inSkyBlock, SkyBlockSession.hasLiveServer, SkyBlockSession.skyblockIsland?.serverMode, SkyBlockSession.profileId, SkyBlockSession.profileIdOrZero))
			}
			SkyBlockSession.serverLocation = before
			SkyBlockSession.profileId = null
			out.add(listOf(SkyBlockSession.NULL_UUID, SkyBlockSession.hypixelTimeZone, SkyBlockSession.profileSuggestTexts, SkyBlockSession.inHypixel))
			out
		}
		case("coordinate") { listOf(BlockPos(1, 2, 3).center(), BlockPos(-1, -2, -3).center()) }
		case("registry") { listOf(Client.registryValue(Registries.ITEM, Identifier.withDefaultNamespace("diamond")), Client.registryValue(Registries.ITEM, Identifier.withDefaultNamespace("nope_nope"))) }
		val f = Fuzz(42)
		repeat(150) { i -> val comp = f.component(); case("fmt $i") { comp.formattedString() } }
	}
}
