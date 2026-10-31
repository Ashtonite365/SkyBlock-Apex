package dev.ashtonite365.apex.api.v1;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

@ApiStatus.NonExtendable
public abstract class ApexAPI {
	private static final String IMPLEMENTATION = "dev.ashtonite365.apex.impl.v1.ApexAPIImpl";
	private static @Nullable ApexAPI cached;

	public static ApexAPI getInstance() {
		ApexAPI known = cached;
		if (known == null) cached = known = locate();
		return known;
	}

	private static ApexAPI locate() {
		Class<?> impl;
		try {
			impl = Class.forName(IMPLEMENTATION);
		} catch (ClassNotFoundException missing) {
			throw new RuntimeException("Apex API implementation is not present; check FabricLoader.getInstance().isModLoaded(\"apex\") before calling.");
		}
		try {
			return (ApexAPI) impl.getField("INSTANCE").get(null);
		} catch (ReflectiveOperationException | ClassCastException broken) {
			throw new RuntimeException("Apex API implementation present, but its INSTANCE could not be read.", broken);
		}
	}

	public abstract List<? extends ApexExtension> getExtensions();

	public abstract Optional<ApexItemWidget> getHoveredItemWidget();
}
