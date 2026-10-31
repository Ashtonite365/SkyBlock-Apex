// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compat.jade

import dev.ashtonite365.apex.util.SkyBlockSession

fun isOnMiningIsland(): Boolean = SkyBlockSession.skyblockIsland?.hasCustomMining == true
