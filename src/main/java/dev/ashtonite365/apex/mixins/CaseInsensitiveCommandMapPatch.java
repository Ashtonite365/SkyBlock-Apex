package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Locale;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CommandNode.class, remap = false) public abstract class CaseInsensitiveCommandMapPatch<S> {
	@WrapOperation(method = "getRelevantNodes", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), remap = false)
	public Object apex$lookup(Map children, Object name, Operation<Object> get) {
		Object exact = get.call(children, name);
		return exact != null ? exact : children.get(((String) name).toLowerCase(Locale.ROOT));
	}
}
