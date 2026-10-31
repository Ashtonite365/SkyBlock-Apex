// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.init;

import java.util.List;
import java.util.NoSuchElementException;

import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class ScreenInputRiser extends BytecodeRiser {
	private static final Intermediary.InterClass SCREEN = Intermediary.<Screen>intermediaryClass();
	private static final Intermediary.InterClass CONTAINER_SCREEN = Intermediary.<AbstractContainerScreen>intermediaryClass();
	private static final Intermediary.InterClass EVENT_HANDLER = Intermediary.<AbstractContainerEventHandler>intermediaryClass();
	private static final Intermediary.InterClass MOUSE_BUTTON = Intermediary.<MouseButtonEvent>intermediaryClass();
	private static final Intermediary.InterClass BOOL = Intermediary.ofClass(boolean.class);
	private static final Intermediary.InterClass DOUBLE = Intermediary.ofClass(double.class);

	private static final Intermediary.InterMethod SCROLL = Intermediary.intermediaryMethod(
		GuiEventListener::mouseScrolled, BOOL, DOUBLE, DOUBLE, DOUBLE, DOUBLE);

	private record Prologue(Intermediary.InterMethod input, List<String> handlers) {
		Prologue(Intermediary.InterMethod input, String... handlers) {
			this(input, List.of(handlers));
		}
	}

	private static final List<Prologue> PROLOGUES = List.of(
		new Prologue(SCROLL, "mouseScrolled_apex"),
		new Prologue(Intermediary.intermediaryMethod(GuiEventListener::keyReleased,
			BOOL, Intermediary.<KeyEvent>intermediaryClass()), "keyReleased_apex"),
		new Prologue(Intermediary.intermediaryMethod(GuiEventListener::charTyped,
			BOOL, Intermediary.<CharacterEvent>intermediaryClass()), "charTyped_apex"),
		new Prologue(Intermediary.intermediaryMethod(GuiEventListener::mouseReleased,
			BOOL, MOUSE_BUTTON), "mouseReleased_apex"),
		new Prologue(Intermediary.intermediaryMethod(GuiEventListener::mouseDragged,
			BOOL, MOUSE_BUTTON, DOUBLE, DOUBLE), "mouseDragged_apex"),
		new Prologue(Intermediary.intermediaryMethod(GuiEventListener::mouseClicked,
			BOOL, MOUSE_BUTTON, BOOL), "onMouseClicked_apex_generic", "mouseClicked_apex_customGui")
	);

	@Override
	void register() {
		patchClass(CONTAINER_SCREEN, true, ScreenInputRiser::chainScrollToScreen);
		patchClass(SCREEN, true, screen -> {
			for (Prologue prologue : PROLOGUES) {
				for (String handler : prologue.handlers()) addPrologue(screen, prologue.input(), handler);
			}
		});
	}

	private static void chainScrollToScreen(ClassNode containerScreen) {
		String name = SCROLL.mapped();
		MethodNode scroll = containerScreen.methods.stream()
			.filter(method -> method.name.equals(name))
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("No value present"));
		AbstractInsnNode finalReturn = null;
		for (AbstractInsnNode insn : scroll.instructions) {
			boolean superCall = insn instanceof MethodInsnNode call
				&& call.getOpcode() == Opcodes.INVOKESPECIAL && call.name.equals(scroll.name);
			if (superCall) return;
			if (insn.getOpcode() == Opcodes.IRETURN) finalReturn = insn;
		}
		InsnList callSuper = receiverAndParameters(Type.getMethodType(scroll.desc));
		callSuper.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, SCREEN.mapped().getInternalName(), scroll.name, scroll.desc));
		scroll.instructions.insertBefore(finalReturn, callSuper);
	}

	private static void addPrologue(ClassNode screen, Intermediary.InterMethod input, String handler) {
		String name = input.mapped();
		String descriptor = input.mappedDesc().getDescriptor();
		MethodNode method = declaredMethod(screen, name, descriptor);
		if (method == null) {
			method = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, new String[0]);
			method.instructions.add(receiverAndParameters(input.mappedDesc()));
			method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, EVENT_HANDLER.mapped().getInternalName(), name, descriptor));
			method.instructions.add(new InsnNode(Opcodes.IRETURN));
			screen.methods.add(method);
		}
		LabelNode notHandled = new LabelNode();
		InsnList prologue = receiverAndParameters(input.mappedDesc());
		prologue.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SCREEN.mapped().getInternalName(), handler, descriptor));
		prologue.add(new JumpInsnNode(Opcodes.IFEQ, notHandled));
		prologue.add(new LdcInsnNode(1));
		prologue.add(new InsnNode(Opcodes.IRETURN));
		prologue.add(notHandled);
		method.instructions.insert(prologue);
	}
}
