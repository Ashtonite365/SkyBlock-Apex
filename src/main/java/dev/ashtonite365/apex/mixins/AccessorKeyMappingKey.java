package dev.ashtonite365.apex.mixins;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface AccessorKeyMappingKey {
	@Accessor("key")
	InputConstants.Key apex$getKey();

	@Accessor("key")
	void apex$setKey(InputConstants.Key key);
}
