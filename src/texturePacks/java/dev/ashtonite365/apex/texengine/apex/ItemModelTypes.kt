/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: the two item-model types packs can use in item definition files (`items/`) (SPEC §2.3).
 *  - predicates/legacy: { fallback, overrides: [ { model, predicate } ] }; the LAST matching override is used.
 *  - head_model: { head, regular }; `head` while the engine resolves a worn head, `regular` otherwise.
 */
package dev.ashtonite365.apex.texengine.apex

import com.google.gson.JsonElement
import com.mojang.serialization.Codec
import com.mojang.serialization.Dynamic
import com.mojang.serialization.JsonOps
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.ItemModel
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.item.ItemModels
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.resources.model.ResolvableModel
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4fc

private val RAW_JSON: Codec<JsonElement> = Codec.PASSTHROUGH.xmap(
	{ dynamic -> dynamic.convert(JsonOps.INSTANCE).value },
	{ json -> Dynamic(JsonOps.INSTANCE, json) },
)

object ItemModelTypes {

	val predicateModelIds: List<Identifier> =
		listOf(Identifier.parse("apex:predicates/legacy"), Identifier.parse("firmament:predicates/legacy"))
	val headModelIds: List<Identifier> =
		listOf(Identifier.parse("apex:head_model"), Identifier.parse("firmament:head_model"))

	fun register() {
		bind(predicateModelIds, PredicateModel.codec())
		bind(headModelIds, HeadModel.codec())
	}
}

private fun <T : ItemModel.Unbaked> bind(ids: List<Identifier>, canonical: MapCodec<T>) {
	ids.forEachIndexed { index, id ->
		ItemModels.ID_MAPPER.put(id, if (index == 0) canonical else canonical.xmap({ it }, { it }))
	}
}

object WornHeadPass {
	private class PassState {
		var open = false
		var headVariantDrawn = false
	}

	private val perThread: ThreadLocal<PassState> = ThreadLocal.withInitial { PassState() }

	fun open() {
		val state = perThread.get()
		state.open = true
		state.headVariantDrawn = false
	}

	fun close(): Boolean {
		val state = perThread.get()
		val drawn = state.headVariantDrawn
		state.open = false
		state.headVariantDrawn = false
		return drawn
	}

	fun wantsHeadVariant(): Boolean {
		val state = perThread.get()
		if (state.open) state.headVariantDrawn = true
		return state.open
	}
}

object PredicateModel {
	class Entry(val model: ItemModel.Unbaked, val predicate: Optional<JsonElement>)

	private val ENTRY: Codec<Entry> = RecordCodecBuilder.create { built ->
		built.group(
			ItemModels.CODEC.fieldOf("model").forGetter(Entry::model),
			RAW_JSON.optionalFieldOf("predicate").forGetter(Entry::predicate),
		).apply(built, ::Entry)
	}

	private val SELF_CODEC: MapCodec<Unbaked> = RecordCodecBuilder.mapCodec { built ->
		built.group(
			ItemModels.CODEC.fieldOf("fallback").forGetter(Unbaked::otherwise),
			ENTRY.listOf().optionalFieldOf("overrides", listOf()).forGetter(Unbaked::cases),
		).apply(built, ::Unbaked)
	}

	fun codec(): MapCodec<Unbaked> = SELF_CODEC

	class Unbaked(val otherwise: ItemModel.Unbaked, val cases: List<Entry>) : ItemModel.Unbaked {
		override fun bake(context: ItemModel.BakingContext, transform: Matrix4fc): ItemModel =
			Baked(otherwise.bake(context, transform), cases.map { case ->
				case.model.bake(context, transform) to Predicates.read(case.predicate.orElse(null))
			})

		override fun resolveDependencies(resolver: ResolvableModel.Resolver) {
			otherwise.resolveDependencies(resolver)
			for (case in cases) case.model.resolveDependencies(resolver)
		}

		override fun type(): MapCodec<out ItemModel.Unbaked> = SELF_CODEC
	}

	class Baked(private val otherwise: ItemModel, choices: List<Pair<ItemModel, StackTest>>) : ItemModel {
		private val models: Array<ItemModel> = Array(choices.size) { choices[it].first }
		private val tests: Array<StackTest> = Array(choices.size) { choices[it].second }

		fun select(stack: ItemStack, owner: ItemOwner?): ItemModel {
			val wearer = owner?.asLivingEntity()
			for (i in tests.indices.reversed()) {
				if (tests[i].test(stack, wearer)) return models[i]
			}
			return otherwise
		}

		override fun update(
			state: ItemStackRenderState, stack: ItemStack, resolver: ItemModelResolver, context: ItemDisplayContext,
			level: ClientLevel?, owner: ItemOwner?, seed: Int,
		) = select(stack, owner).update(state, stack, resolver, context, level, owner, seed)
	}
}

object HeadModel {
	private val SELF_CODEC: MapCodec<Unbaked> = RecordCodecBuilder.mapCodec { built ->
		built.group(
			ItemModels.CODEC.fieldOf("head").forGetter(Unbaked::worn),
			ItemModels.CODEC.fieldOf("regular").forGetter(Unbaked::held),
		).apply(built, ::Unbaked)
	}

	fun codec(): MapCodec<Unbaked> = SELF_CODEC

	class Unbaked(val worn: ItemModel.Unbaked, val held: ItemModel.Unbaked) : ItemModel.Unbaked {
		override fun bake(context: ItemModel.BakingContext, transform: Matrix4fc): ItemModel =
			Baked(worn.bake(context, transform), held.bake(context, transform))

		override fun resolveDependencies(resolver: ResolvableModel.Resolver) {
			worn.resolveDependencies(resolver)
			held.resolveDependencies(resolver)
		}

		override fun type(): MapCodec<out ItemModel.Unbaked> = SELF_CODEC
	}

	class Baked(private val whenWorn: ItemModel, private val usual: ItemModel) : ItemModel {
		private fun current(): ItemModel = if (WornHeadPass.wantsHeadVariant()) whenWorn else usual

		override fun update(
			state: ItemStackRenderState, stack: ItemStack, resolver: ItemModelResolver, context: ItemDisplayContext,
			level: ClientLevel?, owner: ItemOwner?, seed: Int,
		) = current().update(state, stack, resolver, context, level, owner, seed)
	}
}
