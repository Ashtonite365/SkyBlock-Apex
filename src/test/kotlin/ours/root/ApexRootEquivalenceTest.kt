
package dev.ashtonite365.apex.test.ours.root

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.test.ours.OursGolden

class ApexRootEquivalenceTest {
	@Serializable
	data class Sample(val a: Int = 1, val b: String? = null, val c: List<Int> = listOf(1, 2), val d: Map<String, Double> = mapOf("x" to 0.5))

	private fun <T> attempt(f: () -> T): String = runCatching { f().toString() }.getOrElse { "ERR:${it.javaClass.simpleName}" }

	@Test
	fun rootObjectIsTheSame() {
		val out = mutableListOf<String>()
		out += "id=${Apex.MOD_ID} debug=${Apex.DEBUG} dataDir=${Apex.DATA_DIR} logger=${Apex.logger.name}"
		out += "identifier=${Apex.identifier("a/b_c")} ${attempt { Apex.identifier("Bad Path") }}"
		out += "gson=${Apex.gson.toJson(mapOf("k" to listOf(1, null)))}"
		val flavours = listOf("json" to Apex.json, "twoSpace" to Apex.twoSpaceJson, "tight" to Apex.tightJson)
		val lenient = "{ /* c */ \"a\": 3, // line\n \"b\": unquoted, \"zz\": 1, \"c\": [4,5,], }"
		for ((name, j) in flavours) {
			out += "$name config ${j.configuration}"
			out += "$name default ${j.encodeToString(Sample.serializer(), Sample())}"
			out += "$name custom ${j.encodeToString(Sample.serializer(), Sample(2, "s", listOf(), mapOf()))}"
			out += "$name lenient ${attempt { j.decodeFromString(Sample.serializer(), lenient) }}"
		}
		out += "stream ok ${Apex.tryDecodeJsonFromStream<Sample>("{\"a\":7}".byteInputStream())}"
		out += "stream bad ${Apex.tryDecodeJsonFromStream<Sample>("{\"a\":".byteInputStream()).exceptionOrNull()?.javaClass?.simpleName}"
		out += "stream list ${Apex.tryDecodeJsonFromStream<List<Int>>("[1,2,3]".byteInputStream())}"
		out += "scope ${Apex.coroutineScope.coroutineContext[kotlinx.coroutines.CoroutineName]} job=${Apex.globalJob.isActive}"
		out += "handler ${Apex.coroutineScope.coroutineContext[kotlinx.coroutines.CoroutineExceptionHandler] != null}"
		OursGolden.check("root-apex", out)
	}
}
