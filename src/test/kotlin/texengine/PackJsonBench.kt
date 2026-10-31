// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import com.google.gson.JsonParser
import dev.ashtonite365.apex.texengine.apex.PackJson
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("bench")
class PackJsonBench {
	private val kotlinxLenient = Json { isLenient = true; allowTrailingComma = true; allowComments = true }

	private val sample: String = buildString {
		append("{\"overrides\":[")
		for (i in 0 until 200) {
			if (i > 0) append(',')
			append("""{"model":"firmskyblock:item/model_$i","predicate":{"apex:display_name":{"regex":"Item $i.*"},"apex:extra_attributes":{"path":"modifier","string":"rare_$i"},"apex:lore":{"contains":"x"},"n":$i.5}}""")
		}
		append("],\"parent\":\"item/generated\"}")
	}

	@Test
	fun `B-JSON pack json reader`() {
		val viaKotlinx = { JsonParser.parseString(kotlinxLenient.parseToJsonElement(sample).toString()) }
		assertEquals(viaKotlinx(), PackJson.parse(sample))
		val results = listOf(
			Bench.run("B-JSON", "kotlinx lenient + Gson (replaced)", 200) { var x: Any? = null; repeat(200) { x = viaKotlinx() }; x },
			Bench.run("B-JSON", "Gson only (strict, reference)", 200) { var x: Any? = null; repeat(200) { x = JsonParser.parseString(sample) }; x },
			Bench.run("B-JSON", "PackJson (Apex)", 200) { var x: Any? = null; repeat(200) { x = PackJson.parse(sample) }; x },
		)
		val report = StringBuilder("B-JSON, ${sample.length} chars per document\n")
		for (r in results) report.append(String.format("%-36s %10.0f ns/op %10.0f B/op%n", r.case, r.nsPerOp, r.bytesPerOp))
		println(report)
		java.nio.file.Files.writeString(BenchPacks.dir.resolve("b-json.txt"), report.toString())
	}
}
