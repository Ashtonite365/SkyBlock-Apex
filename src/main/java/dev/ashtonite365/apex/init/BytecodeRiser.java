// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.init;

import java.util.function.Consumer;

import me.shedaniel.mm.api.ClassTinkerers;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

abstract class BytecodeRiser {

	abstract void register();

	static void patchClass(Intermediary.InterClass target, boolean afterMixins, Consumer<ClassNode> patch) {
		ClassTinkerers.addTransformation(target.mapped().getClassName(), patch, afterMixins);
	}

	static MethodNode declaredMethod(ClassNode owner, String name, String descriptor) {
		return owner.methods.stream()
			.filter(candidate -> candidate.name.equals(name) && candidate.desc.equals(descriptor))
			.findFirst()
			.orElse(null);
	}

	static InsnList receiverAndParameters(Type methodType) {
		InsnList loads = new InsnList();
		loads.add(new VarInsnNode(Opcodes.ALOAD, 0));
		int slot = 1;
		for (Type parameter : methodType.getArgumentTypes()) {
			loads.add(new VarInsnNode(parameter.getOpcode(Opcodes.ILOAD), slot));
			slot += parameter.getSize();
		}
		return loads;
	}
}
