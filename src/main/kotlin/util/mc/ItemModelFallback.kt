// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.mc

object ItemModelFallback {
	fun <T : Any> choose(stackModel: T?, itemDefault: T?, hasModel: (T) -> Boolean): T? =
		if (stackModel == null || stackModel == itemDefault || hasModel(stackModel)) stackModel else itemDefault ?: stackModel
}
