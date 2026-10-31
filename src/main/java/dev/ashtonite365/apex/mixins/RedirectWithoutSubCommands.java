package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.tree.CommandNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommandDispatcher.class) public abstract class RedirectWithoutSubCommands<S> {
	@Inject(method = "parseNodes", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/context/CommandContextBuilder;withCommand(Lcom/mojang/brigadier/Command;)Lcom/mojang/brigadier/context/CommandContextBuilder;", shift = At.Shift.AFTER))
	private void apex$commandFromRedirect(CommandNode<S> at, StringReader in, CommandContextBuilder<S> outer, CallbackInfoReturnable<ParseResults<S>> result,
		@Local(name = "context") CommandContextBuilder<S> ctx, @Local(name = "child") CommandNode<S> picked) {
		CommandNode<S> target = picked.getRedirect();
		if (target != null && ctx.getCommand() == null) ctx.withCommand(target.getCommand());
	}
}
