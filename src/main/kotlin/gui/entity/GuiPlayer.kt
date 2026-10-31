package dev.ashtonite365.apex.gui.entity

import net.minecraft.client.entity.ClientMannequin
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.level.Level
import java.util.function.Supplier
import dev.ashtonite365.apex.util.Client

@Suppress("UNUSED_PARAMETER")
fun makeGuiPlayer(world: Level): GuiPlayer = GuiPlayer(Client.game.level!!)

class GuiPlayer(world: ClientLevel) : ClientMannequin(world, Client.game.playerSkinRenderCache()) {

	var skinTextures: PlayerSkin = DefaultPlayerSkin.get(uuid)

	var hashSkin: Supplier<PlayerSkin>? = null

	override fun getSkin(): PlayerSkin {
		val fetched = hashSkin?.get() ?: return skinTextures
		return PlayerSkin(fetched.body, skinTextures.cape, skinTextures.elytra, skinTextures.model, true)
	}
	override fun isSpectator(): Boolean = false
	override fun shouldShowName(): Boolean = false
}
