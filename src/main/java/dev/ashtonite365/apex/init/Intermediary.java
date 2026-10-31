// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.init;

import java.util.Arrays;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import org.objectweb.asm.Type;

public class Intermediary {
	private static final String FROM = "intermediary";
	private static final MappingResolver RESOLVER = FabricLoader.getInstance().getMappingResolver();

	static InterMethod intermediaryMethod(Object methodRef, InterClass returns, InterClass... params) {
		throw neverAtRuntime();
	}

	static <T> InterClass intermediaryClass() {
		throw neverAtRuntime();
	}

	private static AssertionError neverAtRuntime() {
		return new AssertionError("Cannot be called at runtime");
	}

	private static Type slashedType(String className) {
		return Type.getObjectType(className.replace('.', '/'));
	}

	public static InterClass ofIntermediaryClass(final String className) {
		return new InterClass(slashedType(className));
	}

	public static InterClass ofClass(final Class<?> type) {
		return new InterClass(Type.getType(type));
	}

	public static InterMethod ofMethod(final String name, final String owner, final InterClass returns, final InterClass... params) {
		return new InterMethod(name, ofIntermediaryClass(owner), returns, Arrays.asList(params));
	}

	public record InterClass(Type unmapped) {

		public Type mapped() {
			if (unmapped.getSort() != Type.OBJECT) return unmapped;
			return slashedType(RESOLVER.mapClassName(FROM, unmapped.getClassName()));
		}
	}

	public record InterMethod(String name, InterClass owner, InterClass result, List<InterClass> parameters) {
		private Type signature(boolean runtimeNames) {
			Type[] args = new Type[parameters.size()];
			for (int i = 0; i < args.length; i++) {
				InterClass p = parameters.get(i);
				args[i] = runtimeNames ? p.mapped() : p.unmapped();
			}
			return Type.getMethodType(runtimeNames ? result.mapped() : result.unmapped(), args);
		}

		public Type intermediaryDesc() {
			return signature(false);
		}

		public Type mappedDesc() {
			return signature(true);
		}

		public String mapped() {
			return RESOLVER.mapMethodName(FROM, owner.unmapped().getClassName(), name, intermediaryDesc().getDescriptor());
		}
	}
}
