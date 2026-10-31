// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.impl.v1

import java.util.Collections
import java.util.Optional
import net.fabricmc.loader.api.FabricLoader
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.api.v1.ApexAPI
import dev.ashtonite365.apex.api.v1.ApexExtension
import dev.ashtonite365.apex.api.v1.ApexItemWidget
import dev.ashtonite365.apex.util.Client

object ApexAPIImpl : ApexAPI() {
	@JvmField
	val INSTANCE: ApexAPI = ApexAPIImpl

	private val registered = ArrayList<ApexExtension>()
	private val readOnlyView: List<ApexExtension> = Collections.unmodifiableList(registered)

	override fun getExtensions(): List<ApexExtension> = readOnlyView

	override fun getHoveredItemWidget(): Optional<ApexItemWidget> {
		val x = Client.game.mouseHandler.getScaledXPos(Client.window)
		val y = Client.game.mouseHandler.getScaledYPos(Client.window)
		val child = Client.screen?.getChildAt(x, y)?.orElse(null)
		return Optional.ofNullable(child as? ApexItemWidget)
	}

	fun loadExtensions() {
		val containers = FabricLoader.getInstance().getEntrypointContainers(ApexExtension.ENTRYPOINT_NAME, ApexExtension::class.java)
		containers.forEach { found ->
			Apex.logger.info("Loading extension ${found.entrypoint} from ${found.provider.metadata.name}")
			loadExtension(found.entrypoint)
		}
		for (extension in extensions) extension.onLoad()
	}

	fun loadExtension(entrypoint: ApexExtension) {
		registered += entrypoint
	}
}
