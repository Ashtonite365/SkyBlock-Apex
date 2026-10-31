package dev.ashtonite365.apex.util

object TestUtil {
	private val testRunnerPackages = listOf("org.junit.", "io.kotest.")

	@JvmField
	val isInTest: Boolean = Throwable().stackTrace.any { frame ->
		testRunnerPackages.any { frame.className.startsWith(it) }
	}

	inline fun <T> unlessTesting(block: () -> T): T? = if (!isInTest) block() else null
}
