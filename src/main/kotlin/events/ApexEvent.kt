// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

abstract class ApexEvent  {

	abstract class Cancellable
		: ApexEvent() {
		var cancelled: Boolean = false

		fun cancel() = run { this.cancelled = true }
	}
}
