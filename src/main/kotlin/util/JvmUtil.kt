package dev.ashtonite365.apex.util

import com.sun.tools.attach.VirtualMachine
import java.nio.file.Path
import kotlin.io.path.absolutePathString

object SelfAttach {

	fun ownPid(): String {
		val id = ProcessHandle.current().pid()
		val text = id.toString()
		val fitsInt = id in Int.MIN_VALUE..Int.MAX_VALUE
		if (!fitsInt) Faults.reportFault("Process id $text is larger than an Int")
		return text
	}

	fun attachToSelf(): VirtualMachine {
		val pid = ownPid()
		return VirtualMachine.attach(pid)
	}

	fun withSelf(use: (VirtualMachine) -> Unit) {
		val machine = attachToSelf()
		use(machine)
		machine.detach()
	}

	fun loadAgent(jar: Path, options: String? = null) {
		val where = jar.absolutePathString()
		withSelf { it.loadAgent(where, options) }
	}
}
