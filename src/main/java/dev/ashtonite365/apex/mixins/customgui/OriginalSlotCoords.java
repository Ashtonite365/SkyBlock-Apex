
package dev.ashtonite365.apex.mixins.customgui;

import dev.ashtonite365.apex.util.customgui.CoordRememberingSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Slot.class) public abstract class OriginalSlotCoords implements CoordRememberingSlot {
	@Shadow public int x;
	@Shadow public int y;
	@Unique public int savedX;
	@Unique public int savedY;

	@Override public void rememberCoords_apex() { savedX = x; savedY = y; }

	@Override public void restoreCoords_apex() { x = savedX; y = savedY; }

	@Override public int getOriginalX_apex() { return savedX; }

	@Override public int getOriginalY_apex() { return savedY; }
}
