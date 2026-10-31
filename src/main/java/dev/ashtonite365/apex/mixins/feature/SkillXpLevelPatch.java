// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.feature;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ashtonite365.apex.features.skills.SkillXpBar;
import net.minecraft.client.gui.Hud;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public class SkillXpLevelPatch {
	@ModifyExpressionValue(method = "extractHotbarAndDecorations", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I", opcode = Opcodes.GETFIELD))
	private int apex$skillLevel(int original) {
		if (!ModuleGates.skillOverlay) return original;
		return SkillXpBar.level(original);
	}
}
