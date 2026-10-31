// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.accessor;

import java.util.Map;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelManager.class)
public interface AccessorModelManager {
	@Accessor("bakedItemStackModels")
	Map<Identifier, ItemModel> getBakedItemStackModels_Apex();
}
