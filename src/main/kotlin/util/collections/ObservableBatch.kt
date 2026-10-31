package dev.ashtonite365.apex.util.collections

import io.github.notenoughupdates.moulconfig.observer.ObservableList

fun <T> ObservableList<T>.batchUpdate(changes: () -> Unit) {
	val listener = observer
	observer = null
	try {
		changes()
	} finally {
		observer = listener
	}
	update()
}
