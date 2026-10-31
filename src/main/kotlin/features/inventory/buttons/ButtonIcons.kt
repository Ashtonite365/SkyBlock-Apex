package dev.ashtonite365.apex.features.inventory.buttons

import com.mojang.brigadier.StringReader
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.arguments.item.ItemArgument
import net.minecraft.resources.Identifier
import net.minecraft.world.flag.FeatureFlags
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.ItemCache.asItemStack
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.collections.rememberRecent
import dev.ashtonite365.apex.util.mc.KeptStack
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.placeholderHeadUuid
import dev.ashtonite365.apex.util.mc.skullWithSkin
import dev.ashtonite365.apex.util.mc.keepable

object ButtonIcons {
	private const val SKULL_PREFIX = "skull:"
	private val background = Identifier.parse("apex:inventory_button_background")

	private val itemSyntax by lazy { ItemArgument.item(CommandBuildContext.simple(Client.builtinRegistries, FeatureFlags.VANILLA_SET)) }

	fun itemArgument(icon: String): String {
		val looksLikeGive = icon.startsWith("give") || icon.startsWith("/give")
		return if (looksLikeGive) icon.split(" ", limit = 3).getOrElse(2) { icon } else icon
	}

	private fun build(icon: String): KeptStack? =
		if (icon.startsWith(SKULL_PREFIX)) skullWithSkin(placeholderHeadUuid, "https://textures.minecraft.net/texture/" + icon.removePrefix(SKULL_PREFIX))
		else runCatching { itemSyntax.parse(StringReader(itemArgument(icon))).createItemStack(1) }.getOrNull()?.keepable()

	private val built = ::build.rememberRecent(500)

	@OptIn(ExpensiveItemCacheApi::class)
	fun resolve(icon: String): KeptStack {
		val id = ItemId(icon)
		val repoItem = RepoManager.getCoreItem(id)
		val own = if (repoItem == null) built(icon) else null
		return own ?: repoItem.asItemStack(idHint = id)
	}

	@OptIn(NeedsComponents::class)
	fun draw(context: GuiGraphicsExtractor, button: InventoryButton) {
		val size = button.size

		if (!dev.ashtonite365.apex.features.menus.style.InventoryWidgetStyle.draw(context, 0, 0, size.width, size.height, false))
			context.blitSprite(RenderPipelines.GUI_TEXTURED, background, 0, 0, size.width, size.height)
		val stack = button.iconStack().current()
		if (!button.isGigantic) {
			context.item(stack, 1, 1)
			return
		}

		val pose = context.pose()
		pose.pushMatrix()
		pose.translate(size.width / 2F, size.height / 2F)
		pose.scale(2F)
		context.item(stack, -8, -8)
		pose.popMatrix()
	}
}
