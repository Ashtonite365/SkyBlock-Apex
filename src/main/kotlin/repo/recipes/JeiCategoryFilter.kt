// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo.recipes

object JeiCategoryFilter {
	fun hide(uidNamespace: String, apexModId: String): Boolean = uidNamespace != apexModId
}
