package dev.ashtonite365.apex.mixins;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.util.Pair;
import java.util.Map;
import net.minecraft.util.datafix.fixes.EntityIdFix;
import net.minecraft.util.datafix.fixes.References;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityIdFix.class)
public abstract class DFUEntityIdFixPatch extends DataFix {
	@Shadow @Final private static Map<String, String> ID_MAP;

	public DFUEntityIdFixPatch(Schema outputSchema, boolean changesType) {
		super(outputSchema, changesType);
	}

	@Inject(method = "makeRule", at = @At("RETURN"), cancellable = true)
	public void apex$replaceRule(CallbackInfoReturnable<TypeRewriteRule> cir) {
		Schema older = getInputSchema();
		Schema newer = getOutputSchema();
		@SuppressWarnings({"unchecked", "rawtypes"})
		TypeRewriteRule renameEntities = fixTypeEverywhere("EntityIdFix",
			older.findChoiceType(References.ENTITY), newer.findChoiceType(References.ENTITY),
			dynamicOps -> idAndData -> ((Pair) idAndData).mapFirst(id -> ID_MAP.getOrDefault(id, (String) id)));
		TypeRewriteRule retypeStacks = convertUnchecked("Fix Type",
			older.getType(References.ITEM_STACK), newer.getType(References.ITEM_STACK));
		cir.setReturnValue(TypeRewriteRule.seq(renameEntities, retypeStacks));
	}
}
