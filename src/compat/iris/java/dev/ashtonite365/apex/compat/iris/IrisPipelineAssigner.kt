// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.compat.iris

import net.irisshaders.iris.api.v0.IrisApi
import net.irisshaders.iris.api.v0.IrisProgram
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.util.render.CustomRenderPipelines as P

object IrisPipelineAssigner {

	private val programFor = listOf(
		P.GUI_TEXTURED_NO_DEPTH_TRIANGLES to IrisProgram.TEXTURED,
		P.OMNIPRESENT_LINES to IrisProgram.LINES,
		P.COLORED_OMNIPRESENT_QUADS to IrisProgram.BASIC,
		P.CIRCLE_FILTER_TRANSLUCENT_GUI_TRIANGLES to IrisProgram.TEXTURED,
		P.GUI_TEXTURED_NO_DEPTH_TRIANGLES_CIRCLE to IrisProgram.TEXTURED,
	)

	@Subscribe
	fun onClientInit(event: ClientInitEvent) {
		val iris = IrisApi.getInstance()
		for ((pipeline, program) in programFor) iris.assignPipeline(pipeline, program)
	}
}
