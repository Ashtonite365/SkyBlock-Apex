// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.misc

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object DiscordStatus {
	@Config
	object TConfig : ManagedConfig("discord-status", Category.MISC) {
		val enabled by toggle("enabled") { false }
		val applicationId by string("application-id") { "" }
		val showIsland by toggle("show-island") { true }
		val showElapsed by toggle("show-elapsed") { true }
	}

	private const val OP_HANDSHAKE = 0
	private const val OP_FRAME = 1
	private const val OP_CLOSE = 2
	private const val OP_PING = 3
	private const val OP_PONG = 4

	fun frame(opcode: Int, json: String): ByteArray {
		val body = json.toByteArray(Charsets.UTF_8)
		val buf = ByteBuffer.allocate(8 + body.size).order(ByteOrder.LITTLE_ENDIAN)
		buf.putInt(opcode)
		buf.putInt(body.size)
		buf.put(body)
		return buf.array()
	}

	internal fun readOpcode(input: InputStream): Int {
		val head = input.readNBytes(8)
		if (head.size < 8) throw IOException("EOF")
		val view = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
		val opcode = view.getInt(0)
		input.readNBytes(view.getInt(4))
		return opcode
	}

	fun activityJson(pid: Long, details: String?, state: String?, start: Long?, nonce: String): JsonObject = buildJsonObject {
		put("cmd", "SET_ACTIVITY")
		putJsonObject("args") {
			put("pid", pid)
			putJsonObject("activity") {
				put("type", 0)
				if (details != null) put("details", details)
				if (state != null) put("state", state)
				if (start != null) putJsonObject("timestamps") { put("start", start) }
			}
		}
		put("nonce", nonce)
	}

	private fun handshakeJson(clientId: String) = buildJsonObject { put("v", 1); put("client_id", clientId) }.toString()

	private val CLIENT_DIRS = listOf("", "app/com.discordapp.Discord", "app/com.discordapp.DiscordCanary", "snap.discord", "snap.discord-canary", "snap.discord-ptb")
	private const val SLOTS = 10

	fun candidatePaths(env: (String) -> String?, windows: Boolean): List<String> {
		if (windows) return List(SLOTS) { "\\\\.\\pipe\\discord-ipc-$it" }
		val runtimeDir = env("XDG_RUNTIME_DIR")
		val roots = LinkedHashSet<String>()
		runtimeDir?.let { roots += it }
		for (name in listOf("TMPDIR", "TMP", "TEMP")) env(name)?.let { roots += it }
		val lastPart = runtimeDir?.substringAfterLast('/')
		if (lastPart != null && lastPart.isNotEmpty() && lastPart.all(Char::isDigit)) roots += "/run/user/$lastPart"
		roots += "/tmp"
		val out = ArrayList<String>(roots.size * CLIENT_DIRS.size * SLOTS)
		for (root in roots) for (dir in CLIENT_DIRS) {
			val folder = if (dir.isEmpty()) root else "$root/$dir"
			for (slot in 0 until SLOTS) out += "$folder/discord-ipc-$slot"
		}
		return out
	}

	internal interface Link : Closeable {
		val input: InputStream
		val output: OutputStream
	}

	private class PipeFileLink(path: String) : Link {
		private val raf = RandomAccessFile(path, "rw")
		override val input: InputStream = object : InputStream() {
			override fun read() = raf.read()
			override fun read(b: ByteArray, off: Int, len: Int) = raf.read(b, off, len)
		}
		override val output: OutputStream = object : OutputStream() {
			override fun write(b: Int) = raf.write(b)
			override fun write(b: ByteArray, off: Int, len: Int) = raf.write(b, off, len)
		}
		override fun close() = raf.close()
	}

	private class SocketLink(path: Path) : Link {
		private val ch: SocketChannel = SocketChannel.open(StandardProtocolFamily.UNIX)

		init {
			ch.connect(UnixDomainSocketAddress.of(path))
		}

		override val input: InputStream = object : InputStream() {
			override fun read(): Int {
				val one = ByteBuffer.allocate(1)
				if (ch.read(one) == -1) return -1
				one.flip()
				return one.get().toInt() and 0xFF
			}
			override fun read(b: ByteArray, off: Int, len: Int) = ch.read(ByteBuffer.wrap(b, off, len))
		}
		override val output: OutputStream = object : OutputStream() {
			override fun write(b: Int) = write(byteArrayOf(b.toByte()))
			override fun write(b: ByteArray, off: Int, len: Int) {
				val chunk = ByteBuffer.wrap(b, off, len)
				while (chunk.hasRemaining()) ch.write(chunk)
			}
		}
		override fun close() = ch.close()
	}

	internal class Session(
		private val dial: () -> Link?,
		private val pid: () -> Long,
		private val nonce: () -> String,
		private val log: (String) -> Unit,
	) {
		var link: Link? = null
			private set
		private var clientId: String? = null
		private var shown: String? = null

		fun drop() {
			try { link?.close() } catch (_: Throwable) {}
			link = null
			clientId = null
			shown = null
		}

		private fun ensure(appId: String): Boolean {
			if (clientId == appId) return true
			drop()
			val fresh = dial() ?: return false
			fresh.output.write(frame(OP_HANDSHAKE, handshakeJson(appId)))
			fresh.output.flush()
			if (readOpcode(fresh.input) == OP_CLOSE) {
				try { fresh.close() } catch (_: Throwable) {}
				return false
			}
			link = fresh
			clientId = appId
			return true
		}

		fun push(appId: String?, details: String?, state: String?, start: () -> Long?) {
			try {
				if (appId == null) { drop(); return }
				if (!ensure(appId)) return
				val current = link ?: return
				val wanted = "$details|$state"
				if (wanted == shown) return
				val body = activityJson(pid(), details, state, start(), nonce())
				current.output.write(frame(OP_FRAME, body.toString()))
				current.output.flush()
				while (readOpcode(current.input) == OP_PING) current.output.write(frame(OP_PONG, "{}"))
				shown = wanted
			} catch (e: Exception) {
				log("Discord status: ${e.message}")
				drop()
			}
		}
	}

	private val onWindows = System.getProperty("os.name").lowercase().contains("win")
	private val launchedAt = System.currentTimeMillis() / 1000
	private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "Apex-DiscordStatus").also { it.isDaemon = true } }
	@Volatile private var pending = false

	private fun dialFirst(): Link? {
		for (path in candidatePaths(System::getenv, onWindows)) {
			try {
				return if (onWindows) PipeFileLink(path) else SocketLink(Path.of(path))
			} catch (_: Throwable) {
			}
		}
		return null
	}

	private val session = Session(::dialFirst, { ProcessHandle.current().pid() }, { UUID.randomUUID().toString() }, { Apex.logger.debug(it) })

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 100 != 0 || pending) return
		val id = TConfig.applicationId.trim()
		val appId = if (TConfig.enabled && id.isNotEmpty() && id.all(Char::isDigit)) id else null
		if (appId == null && session.link == null) return
		val details = if (SkyBlockSession.inSkyBlock) "Hypixel SkyBlock" else null
		val state = if (TConfig.showIsland) SkyBlockSession.skyblockIsland?.userFriendlyName else null
		pending = true
		worker.execute {
			try {
				session.push(appId, details, state) { launchedAt.takeIf { TConfig.showElapsed } }
			} finally {
				pending = false
			}
		}
	}
}
