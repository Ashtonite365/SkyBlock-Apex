package dev.ashtonite365.apex.api.v1;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface ApexItemWidget {

	@ApiStatus.NonExtendable
	interface Placement {
		String name();

		Placement ITEM_LIST = labelled("ITEM_LIST");
		Placement RECIPE_SCREEN = labelled("RECIPE_SCREEN");

		private static Placement labelled(String label) { return () -> label; }
	}

	Placement getPlacement();

	ItemStack getItemStack();

	String getSkyBlockId();
}
