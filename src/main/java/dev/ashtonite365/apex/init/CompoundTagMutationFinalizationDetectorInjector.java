// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.init;

import dev.ashtonite365.apex.util.Faults;
import dev.ashtonite365.apex.util.mc.NbtSnapshotGuard;
import net.minecraft.nbt.CompoundTag;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class CompoundTagMutationFinalizationDetectorInjector extends BytecodeRiser {

	public static final String MUTATION_FIELD_NAME = "_apex_snapshot_source";

	private static final String FINALIZER = "finalize";
	private static final String FINALIZER_DESC = Type.getMethodDescriptor(Type.VOID_TYPE);
	private static final Intermediary.InterClass TAG = Intermediary.<CompoundTag>intermediaryClass();
	private static final Intermediary.InterClass CHECKER = Intermediary.ofClass(NbtSnapshotGuard.class);
	private static final Intermediary.InterMethod CHECK = Intermediary.ofMethod(
		"compareOnFinalize", CHECKER.unmapped().getClassName(), Intermediary.ofClass(void.class), TAG, TAG);

	@Override
	void register() {
		if (Faults.strictFaults) patchClass(TAG, false, this::addSnapshotFinalizer);
	}

	private void addSnapshotFinalizer(org.objectweb.asm.tree.ClassNode tag) {
		String tagDesc = TAG.mapped().getDescriptor();
		tag.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, MUTATION_FIELD_NAME, tagDesc, null, null));
		tag.methods.removeIf(method -> method.name.equals(FINALIZER));

		String tagOwner = TAG.mapped().getInternalName();
		LabelNode chain = new LabelNode();
		InsnList body = new InsnList();

		body.add(new VarInsnNode(Opcodes.ALOAD, 0));
		body.add(new FieldInsnNode(Opcodes.GETFIELD, tagOwner, MUTATION_FIELD_NAME, tagDesc));
		body.add(new JumpInsnNode(Opcodes.IFNULL, chain));
		body.add(new VarInsnNode(Opcodes.ALOAD, 0));
		body.add(new VarInsnNode(Opcodes.ALOAD, 0));
		body.add(new FieldInsnNode(Opcodes.GETFIELD, tagOwner, MUTATION_FIELD_NAME, tagDesc));
		body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CHECKER.mapped().getInternalName(), CHECK.mapped(),
			CHECK.mappedDesc().getDescriptor(), false));

		body.add(chain);
		body.add(new VarInsnNode(Opcodes.ALOAD, 0));
		body.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, Type.getInternalName(Object.class), FINALIZER, FINALIZER_DESC, false));
		body.add(new InsnNode(Opcodes.RETURN));

		MethodNode finalizer = new MethodNode(Opcodes.ACC_PUBLIC, FINALIZER, FINALIZER_DESC, null, null);
		finalizer.instructions = body;
		tag.methods.add(finalizer);
	}
}
