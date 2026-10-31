// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.testutil

import java.io.File

object RepoFile {
	val root: File by lazy {
		listOf(".", "..", "../..", "../../..").map { File(it).absoluteFile.normalize() }
			.first { File(it, "src/main/kotlin/util/ApexClickDelay.kt").isFile }
	}

	operator fun invoke(rel: String) = File(root, rel)
}
