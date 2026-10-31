package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.fabric.impl.command.client.ClientCommandInternals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientCommandInternals.class) public abstract class AlwaysDisplayApexClientCommandErrors {
	@ModifyExpressionValue(method = "executeCommand", at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/impl/command/client/ClientCommandInternals;isIgnoredException(Lcom/mojang/brigadier/exceptions/CommandExceptionType;)Z"))
	private static boolean apex$neverIgnoreApex(boolean ignored, @Local(argsOnly = true) String line) {
		boolean ours = line.equals("apex") || line.startsWith("apex ");
		return ignored && !ours;
	}
}
