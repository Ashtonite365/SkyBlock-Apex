package dev.ashtonite365.apex.util

import java.util.Optional

fun <T : Any> T?.intoOptional(): Optional<T> = if (this == null) Optional.empty() else Optional.of(this)
