/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: hook for the camera-tweaks custom F5 distance (features/kit/render/KitCamera.kt).
 */
package dev.ashtonite365.apex.mixins.kit;

import dev.ashtonite365.apex.features.kit.render.KitCamera;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Camera.class)
public class CameraDistanceMixin {
	@ModifyVariable(method = "getMaxZoom", at = @At("HEAD"), argsOnly = true)
	private float apex$customDistance(float original) {
		return KitCamera.modifyCameraDistance(original);
	}
}
