package dev.ashtonite365.apex.util.mc

import com.mojang.serialization.Codec
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import net.minecraft.core.Holder
import net.minecraft.core.TypedInstance
import net.minecraft.core.component.DataComponentGetter
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.PatchedDataComponentMap
import net.minecraft.resources.ResourceKey
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.Items
import net.minecraft.world.level.ItemLike
import dev.ashtonite365.apex.events.ComponentsLoadedEvent

@RequiresOptIn
annotation class NeedsComponents

interface ItemView {

	val itemType: Holder<Item>

	val amount: Int

	fun <T : Any> resolve(kind: DataComponentType<T>, defaults: DataComponentGetter): T?

	fun backingStack(): ItemStack? = null

	val itemKey: ResourceKey<Item> get() = itemType.unwrapKey().orElseThrow()

	fun <T : Any> get(kind: DataComponentType<T>): T? = resolve(kind, NoComponents)

	@NeedsComponents
	fun <T : Any> effective(kind: DataComponentType<T>): T? = resolve(kind, itemType.components())
}

@NeedsComponents
fun ItemView.typeDefaultStack(): ItemStack = itemType.value().defaultInstance

fun ItemView.isEmpty(): Boolean = amount <= 0 || itemType.value() === Items.AIR

fun ItemView.isOfItem(other: ItemLike): Boolean = itemType.value() === other.asItem()

fun ItemStack.keepable(): KeptStack = KeptStack.ofStack(this)

fun ItemStack.view(): ItemView = StackView(this)

fun ItemStack.itemEditor(): ItemEditor = StackEditor(this)

interface ComponentWriter {
	fun <T : Any> set(kind: DataComponentType<T>, value: T)
	fun remove(kind: DataComponentType<*>)
}

interface ItemEditor : ItemView, ComponentWriter

private fun <T : Any> DataComponentPatch.lookup(kind: DataComponentType<T>, defaults: DataComponentGetter): T? =
	get(defaults, kind)

open class StackView(val itemStack: ItemStack) : ItemView {
	override val itemType: Holder<Item> get() = itemStack.typeHolder()
	override val amount: Int get() = itemStack.count
	override fun backingStack(): ItemStack = itemStack
	override fun <T : Any> resolve(kind: DataComponentType<T>, defaults: DataComponentGetter): T? =
		itemStack.componentsPatch.lookup(kind, defaults)
}

private class StackEditor(stack: ItemStack) : StackView(stack), ItemEditor {
	override fun <T : Any> set(kind: DataComponentType<T>, value: T) {
		itemStack.set(kind, value)
	}

	override fun remove(kind: DataComponentType<*>) {
		itemStack.remove(kind)
	}
}

class PatchView(override val itemType: Holder<Item>, val patch: DataComponentPatch) : ItemView {
	override val amount: Int get() = 1
	override fun <T : Any> resolve(kind: DataComponentType<T>, defaults: DataComponentGetter): T? =
		patch.lookup(kind, defaults)
}

class ItemDraft(override val itemType: Holder<Item>, override var amount: Int, var edits: DataComponentPatch.Builder) : ItemEditor {
	override fun <T : Any> set(kind: DataComponentType<T>, value: T) {
		edits.set(kind, value)
	}

	override fun remove(kind: DataComponentType<*>) {
		edits.remove(kind)
	}

	override fun <T : Any> resolve(kind: DataComponentType<T>, defaults: DataComponentGetter): T? =
		edits.build().lookup(kind, defaults)

	@NeedsComponents
	fun dropDefaults() = dropDefaults(itemType.components())

	fun dropDefaults(base: DataComponentMap) {
		val merged = PatchedDataComponentMap(base)
		merged.applyPatch(edits.build())
		edits = merged.asPatch().toPatchBuilder()
	}

	fun toTemplate(): ItemStackTemplate = ItemStackTemplate(itemType, amount, edits.build())

	fun toStack(): KeptStack = KeptStack.ofDraft(this)
}

fun DataComponentPatch.toPatchBuilder(): DataComponentPatch.Builder {
	val split = split()
	val out = DataComponentPatch.builder()
	for (kind in split.removed()) out.remove(kind)
	out.set<Any>(split.added())
	return out
}

object NoComponents : DataComponentGetter {
	override fun <T : Any> get(kind: DataComponentType<out T>): T? = null
}

open class KeptStack private constructor(private var live: ItemStack?, private var frozen: ItemStackTemplate?) :
	ItemView, TypedInstance<Item> {
	private var stamp: Int = ComponentsLoadedEvent.generation

	companion object Factory {
		fun ofStack(itemStack: ItemStack) = KeptStack(itemStack, null)

		fun ofTemplate(template: ItemStackTemplate?) = KeptStack(null, template)

		fun empty() = KeptStack(null, null)

		fun ofDraft(draft: ItemDraft): KeptStack = if (draft.isEmpty()) empty() else ofTemplate(draft.toTemplate())

		fun build(item: ItemLike, edit: ItemEditor.() -> Unit = {}) = build(item.asItem().builtInRegistryHolder(), edit)

		fun build(type: Holder<Item>, edit: ItemEditor.() -> Unit = {}) =
			ofDraft(ItemDraft(type, 1, DataComponentPatch.builder()).apply(edit))

		val CODEC: Codec<KeptStack> = ExtraCodecs.optionalEmptyMap(ItemStackTemplate.CODEC).xmap(
			{ stored -> ofTemplate(stored.getOrNull()) },
			{ kept -> Optional.ofNullable(kept.frozenTemplate()) },
		)
	}

	fun toDraft(): ItemDraft {
		frozen?.let { return ItemDraft(it.item(), it.count(), it.components().toPatchBuilder()) }
		live?.let { return ItemDraft(it.typeHolder(), it.count, it.componentsPatch.toPatchBuilder()) }
		return ItemDraft(Items.AIR.builtInRegistryHolder(), 1, DataComponentPatch.builder())
	}

	fun editedCopy(edit: ItemDraft.() -> Unit): KeptStack = toDraft().apply(edit).toStack()

	fun frozenTemplate(): ItemStackTemplate? {
		if (frozen == null) {
			val stack = live
			if (stack != null && !stack.isEmpty) frozen = ItemStackTemplate.fromNonEmptyStack(stack)
		}
		return frozen
	}

	@NeedsComponents
	fun rebuildLive(): ItemStack {
		val made = frozen?.create() ?: ItemStack.EMPTY
		stamp = ComponentsLoadedEvent.generation
		live = made
		return made
	}

	@NeedsComponents
	fun current(): ItemStack {
		dropIfStale()
		return live ?: rebuildLive()
	}

	@NeedsComponents
	fun currentCopy(): ItemStack = current().copy()

	fun dropIfStale() {
		if (live == null || stamp == ComponentsLoadedEvent.generation) return
		frozenTemplate()
		live = null
	}

	fun dropLive() {
		live = null
	}

	override val itemType: Holder<Item>
		get() = live?.typeHolder() ?: frozen?.item() ?: Items.AIR.builtInRegistryHolder()

	override val amount: Int get() = live?.count ?: frozen?.count() ?: 0

	override fun <T : Any> resolve(kind: DataComponentType<T>, defaults: DataComponentGetter): T? {
		dropIfStale()
		live?.let { return it.componentsPatch.lookup(kind, defaults) }
		return frozen?.components()?.lookup(kind, defaults)
	}

	override fun typeHolder(): Holder<Item> = itemType

	fun withCount(count: Int): KeptStack = KeptStack(live?.copyWithCount(count), frozen?.withCount(count))

	override fun backingStack(): ItemStack? = live
}
