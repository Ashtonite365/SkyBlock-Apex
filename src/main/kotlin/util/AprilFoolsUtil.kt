package dev.ashtonite365.apex.util

import java.time.LocalDate
import java.time.MonthDay

object AprilFoolsUtil {
	private val jokeDay: MonthDay = MonthDay.of(4, 1)

	val isAprilFoolsDay: Boolean = MonthDay.from(LocalDate.now()) == jokeDay
}
