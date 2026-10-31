// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSignEditScreen.class)
public interface AccessorSignEditScreen {
	@Accessor("sign")
	SignBlockEntity getSign_Apex();

	@Accessor("slot")
	SignTextSlot getSlot_Apex();

	@Accessor("messages")
	String[] getMessages_Apex();
}
