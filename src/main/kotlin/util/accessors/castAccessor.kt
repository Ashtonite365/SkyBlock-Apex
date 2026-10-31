// SPDX-License-Identifier: GPL-3.0-or-later

@file:OptIn(kotlin.contracts.ExperimentalContracts::class)

package dev.ashtonite365.apex.util.accessors

import kotlin.contracts.contract
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen

inline fun AbstractContainerScreen<*>.castAccessor(): AccessorHandledScreen {
	contract { returns() implies (this@castAccessor is AccessorHandledScreen) }
	return (this as Any) as AccessorHandledScreen
}
