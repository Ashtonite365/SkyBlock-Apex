// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.plus.misc.DiscordStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DiscordSessionTest {
	private class FakeLink(replies: List<Int>) : DiscordStatus.Link {
		val sent = ByteArrayOutputStream()
		var closed = 0
		override val input: InputStream = ByteArrayInputStream(replies.flatMap { DiscordStatus.frame(it, "{\"x\":1}").toList() }.toByteArray())
		override val output: OutputStream = sent
		override fun close() { closed++ }

		fun ops(): List<Pair<Int, String>> {
			val buf = ByteBuffer.wrap(sent.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
			val res = mutableListOf<Pair<Int, String>>()
			while (buf.remaining() >= 8) {
				val op = buf.int; val len = buf.int
				val body = ByteArray(len); buf.get(body); res += op to String(body)
			}
			return res
		}
	}

	private fun session(links: MutableList<FakeLink?>, log: MutableList<String> = mutableListOf()): Pair<DiscordStatus.Session, MutableList<Int>> {
		val dials = mutableListOf<Int>()
		var n = 0
		return DiscordStatus.Session({ dials += n; links.getOrNull(n++) }, { 42L }, { "nonce" }, { log += it }) to dials
	}

	@Test
	fun handshakeThenActivityThenNoResendForSameText() {
		val link = FakeLink(listOf(1, 1, 3, 3, 1))
		val (s, dials) = session(mutableListOf(link))
		s.push("123", "Hypixel SkyBlock", "Hub") { 7L }
		s.push("123", "Hypixel SkyBlock", "Hub") { 7L }
		s.push("123", "Hypixel SkyBlock", "Mines") { null }
		assertEquals(listOf(0), dials)
		assertEquals(
			listOf(
				0 to """{"v":1,"client_id":"123"}""",
				1 to """{"cmd":"SET_ACTIVITY","args":{"pid":42,"activity":{"type":0,"details":"Hypixel SkyBlock","state":"Hub","timestamps":{"start":7}}},"nonce":"nonce"}""",
				1 to """{"cmd":"SET_ACTIVITY","args":{"pid":42,"activity":{"type":0,"details":"Hypixel SkyBlock","state":"Mines"}},"nonce":"nonce"}""",
				4 to "{}", 4 to "{}",
			),
			link.ops(),
		)
		assertNotNull(s.link)
	}

	@Test
	fun closeOnHandshakeLeavesNoLink() {
		val link = FakeLink(listOf(2))
		val (s, _) = session(mutableListOf(link))
		s.push("9", null, null) { null }
		assertEquals(1, link.closed)
		assertNull(s.link)
		assertEquals(listOf(0), link.ops().map { it.first })
	}

	@Test
	fun noEndpointAndNullAppId() {
		val (s, dials) = session(mutableListOf(null))
		s.push("9", "a", "b") { 1L }
		assertNull(s.link)
		assertEquals(1, dials.size)
		s.push(null, "a", "b") { 1L }
		assertEquals(1, dials.size)
	}

	@Test
	fun appIdChangeReconnectsAndEofDrops() {
		val first = FakeLink(listOf(1, 1))
		val second = FakeLink(listOf(1))
		val log = mutableListOf<String>()
		val (s, dials) = session(mutableListOf(first, second), log)
		s.push("1", "a", null) { null }
		s.push("2", "a", null) { null }
		assertEquals(1, first.closed)
		assertEquals(2, dials.size)
		assertEquals(listOf(0), second.ops().map { it.first }.take(1))

		assertNull(s.link)
		assertEquals(1, second.closed)
		assertEquals(listOf("Discord status: EOF"), log)
		s.push(null, null, null) { null }
		assertEquals(1, second.closed)
	}

	@Test
	fun sameTextAfterReconnectIsSentAgain() {
		val a = FakeLink(listOf(1, 1))
		val b = FakeLink(listOf(1, 1))
		val (s, _) = session(mutableListOf(a, b))
		s.push("1", "x", "y") { null }
		s.push(null, "x", "y") { null }
		assertEquals(1, a.closed)
		s.push("1", "x", "y") { null }
		assertEquals(listOf(0, 1), b.ops().map { it.first })
	}
}
