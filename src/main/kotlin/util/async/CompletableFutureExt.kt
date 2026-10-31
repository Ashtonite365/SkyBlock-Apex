// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.async

import java.util.concurrent.CompletableFuture

fun CompletableFuture<*>.discard(): CompletableFuture<Void?> = thenAccept { _ -> }
