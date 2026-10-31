package dev.ashtonite365.apex.util.mc;

import dev.ashtonite365.apex.init.CompoundTagMutationFinalizationDetectorInjector;
import dev.ashtonite365.apex.util.Faults;
import io.github.notenoughupdates.moulconfig.internal.InitUtil;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import kotlin.Unit;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NbtSnapshotGuard {
	private static final Logger LOG = LoggerFactory.getLogger(NbtSnapshotGuard.class);

	private NbtSnapshotGuard() {
	}

	public static void compareOnFinalize(CompoundTag handedOut, CompoundTag source) {
		if (handedOut.equals(source)) return;
		LOG.error("Some code-path modified a not for mutation CompoundTag!\n\nOriginal:\n\n{}\n\nModifier:\n\n{}\n",
			source, handedOut);
	}

	public static final class SetterHolder {
		public static MethodHandle snapshotSetter;

		static {
			try {
				snapshotSetter = resolveSetter();
			} catch (ReflectiveOperationException e) {
				throw new ExceptionInInitializerError(e);
			}
		}

		private SetterHolder() {
		}

		private static MethodHandle resolveSetter() throws ReflectiveOperationException {
			return MethodHandles.publicLookup().findSetter(CompoundTag.class,
				CompoundTagMutationFinalizationDetectorInjector.MUTATION_FIELD_NAME, CompoundTag.class);
		}
	}

	public static CompoundTag watchedCopy(CompoundTag tag) {
		if (!Faults.strictFaults) return tag;
		CompoundTag copy = tag.copy();
		Faults.attemptJava("Could not insert mutation check", (InitUtil.ThrowingSupplier<Unit, Throwable>) () -> linkToSource(copy, tag));
		return copy;
	}

	private static Unit linkToSource(CompoundTag copy, CompoundTag source) throws Throwable {
		SetterHolder.snapshotSetter.invoke(copy, source);
		return Unit.INSTANCE;
	}
}
