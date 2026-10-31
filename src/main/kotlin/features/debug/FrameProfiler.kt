
package dev.ashtonite365.apex.features.debug

object FrameProfiler {
	enum class Phase(val label: String) {
		BLUR("blur"), PANELS("skyglass panels"), STORAGE_PAGES("storage pages"), ITEMS("item renders"),
		SLOT_LISTENERS("slot listeners"), TOOLTIP("tooltip listeners"), GENERIC_WRAPPER("generic wrapper"),
		SCREEN("whole screen"),
	}

	@JvmField var enabled = false

	const val ALPHA = 0.05

	private val n = Phase.entries.size
	private val frameNanos = LongArray(n)
	private val frameCalls = IntArray(n)
	val avgMs = DoubleArray(n)
	val avgCalls = DoubleArray(n)
	val maxMs = DoubleArray(n)
	var avgFrameMs = 0.0; private set
	var frames = 0L; private set
	private var lastFrameAt = 0L

	inline fun <T> time(phase: Phase, block: () -> T): T {
		if (!enabled) return block()
		val t0 = System.nanoTime()
		try { return block() } finally { add(phase, System.nanoTime() - t0) }
	}

	fun add(phase: Phase, nanos: Long) {
		frameNanos[phase.ordinal] += nanos
		frameCalls[phase.ordinal]++
	}

	fun endFrame(now: Long = System.nanoTime()) {
		if (lastFrameAt != 0L) {
			val dt = (now - lastFrameAt) / 1e6
			if (dt < 1000) avgFrameMs = if (avgFrameMs == 0.0) dt else avgFrameMs + ALPHA * (dt - avgFrameMs)
		}
		lastFrameAt = now
		for (i in 0 until n) {
			val ms = frameNanos[i] / 1e6
			avgMs[i] = if (frames == 0L) ms else avgMs[i] + ALPHA * (ms - avgMs[i])
			avgCalls[i] = if (frames == 0L) frameCalls[i].toDouble() else avgCalls[i] + ALPHA * (frameCalls[i] - avgCalls[i])
			if (ms > maxMs[i]) maxMs[i] = ms
			frameNanos[i] = 0; frameCalls[i] = 0
		}
		frames++
	}

	fun reset() {
		frameNanos.fill(0); frameCalls.fill(0); avgMs.fill(0.0); avgCalls.fill(0.0); maxMs.fill(0.0)
		avgFrameMs = 0.0; frames = 0; lastFrameAt = 0
	}

	fun lines(): List<String> = buildList {
		val fps = if (avgFrameMs > 0) 1000 / avgFrameMs else 0.0
		add("Apex frame cost: %.1f ms/frame (%.0f fps), %d frames".format(avgFrameMs, fps, frames))
		for (p in Phase.entries) add("%s %.2f ms (x%.0f, max %.1f)".format(p.label, avgMs[p.ordinal], avgCalls[p.ordinal], maxMs[p.ordinal]))
	}
}
