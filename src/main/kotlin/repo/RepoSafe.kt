// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.repo

import io.github.moulberry.repo.NEUItems
import io.github.moulberry.repo.data.NEUItem

val NEUItems.itemsSafe: Map<String, NEUItem> get() = (items as Map<String, NEUItem>?) ?: emptyMap()
