package dev.ashtonite365.apex.api.v1;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;

import java.util.Collection;
import java.util.List;

public interface ApexExtension {

	String ENTRYPOINT_NAME = "apex:v1";

	default void onLoad() {}

	default boolean shouldHideInventoryButtons(Screen screen) { return false; }

	default Collection<? extends ScreenRectangle> getExclusionZones(Screen screen) { return List.of(); }
}
