// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.feature;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ashtonite365.apex.features.skills.SkillXpBar;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ExperienceBar.class)
public class SkillXpBarPatch {
	@ModifyExpressionValue(method = "extractBackground", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;experienceProgress:F", opcode = Opcodes.GETFIELD))
	private float apex$skillProgress(float original) {
		if (!ModuleGates.skillOverlay) return original;
		return SkillXpBar.progress(original);
	}

	@ModifyExpressionValue(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXpNeededForNextLevel()I"))
	private int apex$skillNeeded(int original) {
		if (!ModuleGates.skillOverlay) return original;
		return SkillXpBar.xpNeeded(original);
	}
}
