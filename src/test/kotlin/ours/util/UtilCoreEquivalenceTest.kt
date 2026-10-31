// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.util

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.google.gson.JsonParser
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import com.mojang.serialization.codecs.RecordCodecBuilder
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.util.collections.InstanceList
import dev.ashtonite365.apex.util.collections.WeakKeyCache
import dev.ashtonite365.apex.util.collections.centre
import dev.ashtonite365.apex.util.collections.getOrComputeNullable
import dev.ashtonite365.apex.util.collections.lastNotNullOfOrNull
import dev.ashtonite365.apex.util.collections.rememberRecent
import dev.ashtonite365.apex.util.collections.rememberByIdentity
import dev.ashtonite365.apex.util.collections.lruMap
import dev.ashtonite365.apex.util.collections.nonNegligibleSubSectionsAlignedWith
import dev.ashtonite365.apex.util.json.DashlessUUIDSerializer
import dev.ashtonite365.apex.util.json.ApexCodecs
import dev.ashtonite365.apex.util.json.InstantAsLongSerializer
import dev.ashtonite365.apex.util.json.KJsonOps
import dev.ashtonite365.apex.util.json.asJsonArray
import dev.ashtonite365.apex.util.json.intoGson
import dev.ashtonite365.apex.util.json.intoKotlinJson
import dev.ashtonite365.apex.util.json.toJsonArray

class UtilCoreEquivalenceTest {
	private fun <T> attempt(block: () -> T): String = try {
		block().toString()
	} catch (e: Throwable) {
		"ERR:" + e.javaClass.simpleName
	}

	private val samples = listOf(
		"null", "1", "1.5", "-0", "1e3", "12345678901234567890", "\"s\"", "\"1\"", "true", "false", "\"true\"",
		"[]", "{}", "[1,\"a\",null,true,{\"k\":[2.0]}]", "{\"b\":1,\"a\":{\"c\":null,\"d\":[]}}",
	)

	data class Rec(val name: String, val n: Int, val tags: List<String>)

	private val recCodec: Codec<Rec> = RecordCodecBuilder.create { i ->
		i.group(
			Codec.STRING.fieldOf("name").forGetter(Rec::name),
			Codec.INT.fieldOf("n").forGetter(Rec::n),
			Codec.STRING.listOf().fieldOf("tags").forGetter(Rec::tags),
		).apply(i, ::Rec)
	}

	@Test
	fun jsonHelpersMatch() {
		val out = mutableListOf<String>()
		val ops = KJsonOps.INSTANCE
		for (s in samples) {
			val k = Json.parseToJsonElement(s)
			val g = JsonParser.parseString(s)
			out += "conv $s -> gson=${attempt { k.intoGson() }} kotlin=${attempt { g.intoKotlinJson() }} rt=${attempt { k.intoGson().intoKotlinJson() == k }}"
			out += "ops $s str=${attempt { ops.getStringValue(k).result() }} num=${attempt { ops.getNumberValue(k).result().map { it.toString() } }}" +
				" bool=${attempt { ops.getBooleanValue(k).result() }} stream=${attempt { ops.getStream(k).result().map { it.toList() } }}" +
				" map=${attempt { ops.getMapValues(k).result().map { st -> st.toList().map { "${it.first}=${it.second}" } } }}" +
				" errs=${listOf(ops.getStringValue(k), ops.getNumberValue(k), ops.getBooleanValue(k), ops.getStream(k), ops.getMapValues(k)).map { it.error().map { e -> e.message() }.orElse("-") }}"
			out += "toJsonOps $s ${attempt { ops.convertTo(JsonOps.INSTANCE, k) }}"
			out += "remove $s ${attempt { ops.remove(k, "a") }}"
			out += "mergeList $s ${attempt { ops.mergeToList(k, JsonPrimitive(7)).let { it.result().map(Any::toString).orElse("E:" + it.error().get().message()) } }}"
			for (key in listOf(JsonPrimitive("key"), JsonPrimitive(3), JsonNull))
				out += "mergeMap $s $key ${attempt { ops.mergeToMap(k, key, JsonPrimitive(7)).let { it.result().map(Any::toString).orElse("E:" + it.error().get().message()) } }}"
		}
		out += "create ${ops.empty()} ${ops.createNumeric(3)} ${ops.createNumeric(2.5)} ${ops.createString("x")} ${ops.createBoolean(true)}" +
			" ${ops.createList(listOf<kotlinx.serialization.json.JsonElement>(JsonPrimitive(1), JsonNull).stream())}" +
			" ${attempt { ops.createMap(listOf<Pair<kotlinx.serialization.json.JsonElement, kotlinx.serialization.json.JsonElement>>(Pair.of(JsonPrimitive("a"), JsonPrimitive(1)), Pair.of(JsonPrimitive(5), JsonPrimitive(2))).stream()) }}"
		val rec = Rec("x", 4, listOf("p", "q"))
		out += "codecEncode ${attempt { recCodec.encodeStart(ops, rec).let { it.result().map(Any::toString).orElse("E:" + it.error().get().message()) } }}"
		out += "codecDecode ${attempt { recCodec.parse(ops, Json.parseToJsonElement("{\"name\":\"y\",\"n\":2,\"tags\":[\"a\"]}")).result() }}"
		out += "codecDecodeBad ${attempt { recCodec.parse(ops, Json.parseToJsonElement("{\"name\":1}")).error().map { it.message() } }}"
		out += "listCodec ${attempt { Codec.INT.listOf().encodeStart(ops, listOf(1, 2, 3)).result() }} ${attempt { Codec.INT.listOf().parse(ops, Json.parseToJsonElement("[4,5]")).result() }}"
		val uuid = UUID.fromString("0123abcd-4567-89ef-0123-456789abcdef")
		out += "dashless ${Json.encodeToString(DashlessUUIDSerializer, uuid)} ${Json.decodeFromString(DashlessUUIDSerializer, "\"0123abcd456789ef0123456789abcdef\"")}" +
			" ${Json.decodeFromString(DashlessUUIDSerializer, "\"0123abcd-4567-89ef-0123-456789abcdef\"")} ${attempt { Json.decodeFromString(DashlessUUIDSerializer, "\"zz\"") }}" +
			" ${DashlessUUIDSerializer.descriptor.serialName} ${DashlessUUIDSerializer.descriptor.kind}"
		out += "instant ${Json.encodeToString(InstantAsLongSerializer, Instant.ofEpochMilli(1234567))} ${Json.decodeFromString(InstantAsLongSerializer, "99")}" +
			" ${InstantAsLongSerializer.descriptor.serialName} ${InstantAsLongSerializer.descriptor.kind}"
		val lenient = ApexCodecs.UUID_LENIENT_PREFER_INT_STREAM
		for (s in listOf("\"0123abcd456789ef0123456789abcdef\"", "\"0123abcd-4567-89ef-0123-456789abcdef\"", "[19114957,1166510575,19088743,-1985229329]", "\"nope\"", "3"))
			out += "lenient $s ${attempt { lenient.parse(JsonOps.INSTANCE, JsonParser.parseString(s)).let { r -> r.result().map(Any::toString).orElse("E:" + r.error().get().message()) } }}"
		out += "lenientEnc ${lenient.encodeStart(JsonOps.INSTANCE, uuid).result()}"
		val codecSer = object : util.json.CodecSerializer<Rec>(recCodec) {}
		out += "codecSer ${attempt { Json.encodeToString(codecSer, rec) }} ${attempt { Json.decodeFromString(codecSer, "{\"name\":\"z\",\"n\":1,\"tags\":[]}") }}" +
			" ${codecSer.descriptor.serialName} ${attempt { Json.encodeToString(ListSerializer(codecSer), listOf(rec)) }}"
		out += "arrays ${listOf(JsonPrimitive(1), JsonPrimitive("b")).asJsonArray()} ${listOf("a", "b").toJsonArray()} ${emptyList<String>().toJsonArray()}"
		OursGolden.check("util-json", out)
	}

	@Test
	fun collectionHelpersMatch() {
		val out = mutableListOf<String>()
		val m = lruMap<String, Int>(3)
		for ((i, k) in listOf("a", "b", "c", "a", "d", "e", "b").withIndex()) {
			m[k] = i
			out += "lru $k ${m.entries}"
		}
		out += "lruGet ${m["a"]} ${m.entries}"
		var calls = 0
		val sq = { x: Int -> calls++; if (x < 0) null else x * x }.rememberRecent(2)
		out += "memo ${listOf(2, 2, 3, -1, -1, 2, 4, 2).map { sq(it) }} calls=$calls"
		calls = 0
		val ident = { s: StringBuilder -> calls++; s.length }.rememberByIdentity(4)
		val a = StringBuilder("ab"); val b = StringBuilder("ab")
		out += "memoId ${listOf(a, a, b, a, b).map { ident(it) }} calls=$calls"
		val nm = mutableMapOf<String, Any>()
		out += "nullable ${nm.getOrComputeNullable("x") { null as String? }} ${nm.getOrComputeNullable("x") { "later" }} ${nm.getOrComputeNullable("y") { "v" }} ${nm.size}"
		out += "lastNN ${listOf(1, 2, 3, 4).lastNotNullOfOrNull { if (it % 2 == 1) "o$it" else null }} ${listOf(2, 4).lastNotNullOfOrNull { if (it % 2 == 1) it else null }} ${emptyList<Int>().lastNotNullOfOrNull { it }}"
		val ranges = listOf(0f..1f, 0f..10f, 0.5f..3.2f, -2.5f..2.5f, 3f..3f, 1f..0f, 0f..1e-6f)
		for (r in ranges) for (step in listOf(1f, 0.5f, 3f, 0.3f))
			out += "range $r/$step centre=${r.centre} ${attempt { r.nonNegligibleSubSectionsAlignedWith(step).take(50).toList() }}"
		out += "rangeInf ${attempt { (0f..1f).nonNegligibleSubSectionsAlignedWith(Float.POSITIVE_INFINITY) }}"
		val it2 = (0f..1f).nonNegligibleSubSectionsAlignedWith(0.5f).iterator()
		out += "iter ${it2.hasNext()} ${it2.hasNext()} ${it2.next()} ${it2.next()} ${it2.next()} ${it2.hasNext()} ${attempt { it2.next() }}"
		val list = InstanceList<StringBuilder>("golden")
		val keep = StringBuilder("k")
		list.add(keep); list.add(keep); list.add(StringBuilder("k"))
		out += "instances ${list.size >= 2} ${list.getAll().count { it === keep }} ${list.name} ${InstanceList.allInstances.getAll().any { it === list }}"
		val cache = WeakKeyCache<StringBuilder, String, String>("golden-cache")
		val k1 = StringBuilder("same"); val k2 = StringBuilder("same")
		cache.put(k1, "x", "v1")
		out += "weak ${cache.get(k1, "x")} ${cache.get(k2, "x")} ${cache.get(k1, "y")} ${cache.size}"
		out += "weakGetOrPut ${cache.getOrPut(k1, "x") { _, _ -> "new" }} ${cache.getOrPut(k2, "x") { k, e -> "$k/$e" }} ${cache.size}"
		cache.clear()
		out += "weakClear ${cache.size} ${cache.name} ${WeakKeyCache.allInstances.getAll().any { it === cache }}"
		var hits = 0
		val f = WeakKeyCache.perKey<StringBuilder, Int>("golden-memo") { hits++; it.length }
		out += "weakMemo ${f(k1)} ${f(k1)} ${f(k2)} hits=$hits ${f.cache.size}"
		val g = WeakKeyCache.perKey<StringBuilder, Int, String>("golden-memo2") { k, n -> hits++; "$k$n" }
		out += "weakMemo2 ${g(k1, 1)} ${g(k1, 1)} ${g(k1, 2)} hits=$hits"
		val d = WeakKeyCache.passThrough<StringBuilder, Int, String>("golden-none") { k, n -> "$k-$n" }
		out += "dont ${d(k1, 3)}"
		OursGolden.check("util-collections", out)
	}
}
