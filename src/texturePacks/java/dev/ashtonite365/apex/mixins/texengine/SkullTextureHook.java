/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: player-skin render info (skull blocks, worn heads, skull items) uses the
 * engine's render type when a pack provides a texture for the skin (SPEC §2.11).
 */
package dev.ashtonite365.apex.mixins.texengine;

import com.mojang.authlib.GameProfile;
import dev.ashtonite365.apex.texengine.ApexTextureEngines;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerSkinRenderCache.RenderInfo.class)
public abstract class SkullTextureHook {
	@Shadow
	@Final
	private GameProfile gameProfile;

	@Inject(method = "renderType", at = @At("HEAD"), cancellable = true)
	private void apex$packSkullTexture(CallbackInfoReturnable<RenderType> cir) {
		RenderType packType = ApexTextureEngines.getActive().getSkulls().renderTypeFor(gameProfile);
		if (packType != null) cir.setReturnValue(packType);
	}
}
