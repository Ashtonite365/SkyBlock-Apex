// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.init;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class MixinPlugin
	implements IMixinConfigPlugin {

	public static List<MixinPlugin> instances = new ArrayList<>(1);

	static final String[][] MOD_ONLY_PACKAGES = {{"sodium.", "sodium"}, {"compat.jei.", "jei"}};

	private static final String MIXINS_SEGMENT = ".mixins.";

	public String mixinPackage;

	public List<String> appliedMixins = new ArrayList<>(256);
	private final AutoDiscoveryPlugin discovery = new AutoDiscoveryPlugin();

	public static boolean requiredModPresent(String mixinClassName, Predicate<String> isModLoaded) {
		if (mixinClassName == null) return true;
		int segment = mixinClassName.indexOf(MIXINS_SEGMENT);
		String relative = segment >= 0 ? mixinClassName.substring(segment + MIXINS_SEGMENT.length()) : mixinClassName;
		for (String[] rule : MOD_ONLY_PACKAGES) {
			if (relative.startsWith(rule[0]) && !isModLoaded.test(rule[1])) return false;
		}
		return true;
	}

	private static boolean modInstalled(String modId) {
		try {
			return FabricLoader.getInstance().isModLoaded(modId);
		} catch (Throwable notReady) {
			return false;
		}
	}

	@Override
	public void onLoad(final String configPackage) {
		MixinExtrasBootstrap.init();
		MixinPlugin.instances.add(this);
		this.mixinPackage = configPackage;
		this.discovery.setMixinPackage(configPackage);
	}

	@Override
	public String getRefMapperConfig() { return null; }

	@Override
	public boolean shouldApplyMixin(final String target, final String mixin) {
		boolean debugBuild = Boolean.getBoolean("apex.debug");
		if (!debugBuild && mixin.contains("devenv.")) return false;
		return requiredModPresent(mixin, MixinPlugin::modInstalled);
	}

	@Override
	public void acceptTargets(final Set<String> ours, final Set<String> others) {  }

	@Override
	public List<String> getMixins() {
		List<String> selected = new ArrayList<>();
		for (String mixin : discovery.getMixins()) {
			if (shouldApplyMixin(null, mixin)) selected.add(mixin);
		}
		return List.copyOf(selected);
	}

	@Override
	public void preApply(final String target, final ClassNode node, final String mixin, final IMixinInfo info) {  }

	@Override
	public void postApply(final String target, final ClassNode node, final String mixin, final IMixinInfo info) { this.appliedMixins.add(mixin); }

	public Set<String> getAppliedFullPathMixins() { return new HashSet<>(this.appliedMixins); }

	public Set<String> getExpectedFullPathMixins() {
		return getMixins().stream().map(relative -> mixinPackage + "." + relative).collect(Collectors.toSet());
	}
}
