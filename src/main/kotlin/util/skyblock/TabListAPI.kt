// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.skyblock

import org.intellij.lang.annotations.Language
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.StringUtil.title
import dev.ashtonite365.apex.util.StringUtil.unwords
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.bareText

object TabListAPI {
	private const val SPACER = "               "

	fun getWidgetLines(
		widgetName: WidgetName,
		includeTitle: Boolean = false,
		from: TabListReader.TabSnapshot = TabListReader.currentSnapshot,
	): List<Component> {
		val widget = from.body.dropWhile { line -> !widgetName.matchesTitle(line) }
			.takeWhile { line -> line.string.isNotBlank() && !line.string.startsWith(SPACER) }
		return if (includeTitle) widget else widget.drop(1)
	}

	enum class WidgetName(titlePattern: Regex?) {
		COMMISSIONS, SKILLS("Skills:( .*)?"), PROFILE("Profile: (.*)"), COLLECTION, ESSENCE, PET, COMPOSTER;

		constructor() : this(titlePattern = null)
		constructor(@Language("RegExp") pattern: String) : this(titlePattern = Regex(pattern))

		val label: String = name.split('_').map { part -> part.lowercase().title() }.unwords()

		val regex: Regex = titlePattern ?: Regex.fromLiteral("$label:")

		fun matchesTitle(it: Component): Boolean = regex.matches(it.bareText)
	}
}
