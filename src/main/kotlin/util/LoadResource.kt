package dev.ashtonite365.apex.util

import dev.ashtonite365.apex.repo.RepoDownloadManager
import java.io.InputStream
import kotlin.io.path.inputStream
import net.minecraft.resources.Identifier

fun Identifier.openApexResource(): InputStream {
	val packed = Client.resources.getResource(this)
	if (packed.isPresent) return packed.get().open()
	check(namespace == "neurepo") { "Could not read resource $this" }
	return RepoDownloadManager.repoSavedLocation.resolve(path).inputStream()
}
