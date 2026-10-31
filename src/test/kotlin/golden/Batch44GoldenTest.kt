// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.plus.PlusRepoSource
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch44GoldenTest {
	private class Stepped(val size: Int, val step: Int) : InputStream() {
		var pos = 0
		var closed = false
		override fun read(): Int = if (pos >= size) -1 else (pos++ % 251)
		override fun read(b: ByteArray, off: Int, len: Int): Int {
			if (pos >= size) return -1
			val n = minOf(len, step, size - pos)
			for (i in 0 until n) b[off + i] = ((pos + i) % 251).toByte()
			pos += n
			return n
		}
		override fun close() { closed = true }
	}

	private fun msg(block: () -> String): String = try { block() } catch (e: Exception) { "THROWS ${e::class.simpleName}: ${e.message}" }

	@Test
	fun repo() {
		val S = Batch44Repo
		val out = mutableListOf<Pair<String, String>>()
		out += "consts" to "${PlusRepoSource.ZIP_URL} ${PlusRepoSource.ALLOWED_HOSTS} ${PlusRepoSource.MAX_ZIP_BYTES} ${PlusRepoSource.MAX_EXTRACTED_BYTES} ${PlusRepoSource.MAX_REDIRECTS} ${PlusRepoSource.BUNDLED_BASE} ${PlusRepoSource.Source.entries.map { it.label }}"
		out += "describe" to "${PlusRepoSource.describe()} ${PlusRepoSource.source} ${PlusRepoSource.lastError} ${PlusRepoSource.generation}"
		for (u in S.uris) out += "url|$u" to Golden.run { "${PlusRepoSource.isAllowedUrl(URI(u))}" }
		for ((i, n) in S.names.withIndex()) out += "path|$i" to "${PlusRepoSource.safeEntryPath(n)}"
		for ((i, z) in S.zips.withIndex()) {
			val bytes = S.zipBytes(z, i)
			for (cap in listOf(0L, 100L, 8192L, 30000L, PlusRepoSource.MAX_EXTRACTED_BYTES)) {
				val src = ByteArrayInputStream(bytes)
				out += "zip|$i|$cap" to msg {
					PlusRepoSource.readZip(src, cap).entries.joinToString(";") { (k, v) -> "$k=${v.size}:${v.contentHashCode()}" }
				} + " left=${src.available()}"
			}
		}
		out += "zip|garbage" to msg { PlusRepoSource.readZip(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))).toString() }
		for (size in S.streamSizes) for (step in S.readSteps) for (cap in S.caps) {
			val st = Stepped(size, step)
			out += "cap|$size|$step|$cap" to msg { PlusRepoSource.readCapped(st, cap).let { "${it.size} ${it.contentHashCode()}" } } + " pos=${st.pos} closed=${st.closed}"
		}
		for (t in S.texts) out += "text|$t" to "${PlusRepoSource.readText(t)?.let { "${it.length} ${it.hashCode()}" }}"
		val n = Golden.verify("batch44-repo", out, minCases = 3520)
		assertTrue(n >= 3520)
	}
}
