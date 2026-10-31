package dev.ashtonite365.apex.mixins.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AccessorHandledScreen {
    @Accessor("hoveredSlot")
    @Nullable
	Slot getFocusedSlot_Apex();

    @Accessor("imageWidth")
    int getBackgroundWidth_Apex();

    @Accessor("imageWidth")
    void setBackgroundWidth_Apex(int newBackgroundWidth);

    @Accessor("imageHeight")
    int getBackgroundHeight_Apex();

    @Accessor("imageHeight")
    void setBackgroundHeight_Apex(int newBackgroundHeight);

    @Accessor("leftPos")
    int getX_Apex();

    @Accessor("leftPos")
    void setX_Apex(int newX);

    @Accessor("topPos")
    int getY_Apex();

    @Accessor("topPos")
    void setY_Apex(int newY);

}
