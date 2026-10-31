package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.fixes.Fixes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryMenu.class) public abstract class MixinPlayerScreenHandler {

	@Unique private static final int OFFHAND_INDEX = 40;
	@Unique private static final int PARKED = -1000;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void apex$parkOffhand(Inventory inv, boolean local, Player who, CallbackInfo info) {
		if (!ModuleGates.fixes || !Fixes.TConfig.INSTANCE.getNoOffHandSlot()) return;
		for (var slot : ((InventoryMenu) (Object) this).slots) {
			if (slot.getContainerSlot() != OFFHAND_INDEX) continue;
			slot.x = PARKED;
			slot.y = PARKED;
		}
	}
}
