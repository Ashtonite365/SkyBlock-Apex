// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import java.lang.reflect.Modifier
import net.minecraft.client.Minecraft
import net.minecraft.client.User

object FakeMinecraft {
	private val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").also { it.isAccessible = true }.get(null) as sun.misc.Unsafe
	private val instanceField = Minecraft::class.java.declaredFields.first { Modifier.isStatic(it.modifiers) && it.type == Minecraft::class.java }
	private val userField = Minecraft::class.java.declaredFields.first { !Modifier.isStatic(it.modifiers) && it.type == User::class.java }
	private val nameField = User::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) && it.type == String::class.java }
		.let { fs -> fs.firstOrNull { it.name == "name" } ?: fs.first() }

	@Suppress("DEPRECATION")
	private fun setInstance(mc: Minecraft?) {
		unsafe.putObject(unsafe.staticFieldBase(instanceField), unsafe.staticFieldOffset(instanceField), mc)
	}

	private val guiField = Minecraft::class.java.declaredFields.first { !Modifier.isStatic(it.modifiers) && it.type == net.minecraft.client.gui.Gui::class.java }
	private val screenField = net.minecraft.client.gui.Gui::class.java.declaredFields.first { !Modifier.isStatic(it.modifiers) && it.type == net.minecraft.client.gui.screens.Screen::class.java }
	private val inTestField = dev.ashtonite365.apex.util.TestUtil::class.java.getDeclaredField("isInTest")

	@Suppress("DEPRECATION")
	private fun setInTest(v: Boolean) = unsafe.putBoolean(unsafe.staticFieldBase(inTestField), unsafe.staticFieldOffset(inTestField), v)

	@Suppress("DEPRECATION")
	fun <T> withScreen(screen: net.minecraft.client.gui.screens.Screen?, playerName: String = "Steve", block: () -> T): T = with(playerName) {
		check(System.getProperty("apex.screenJvm") == "true") { "withScreen needs the interpreter-only JVM: tag the test @Tag(\"screen\") (screenGoldenTest)" }
		val mc = Minecraft.getInstance()
		val gui = unsafe.allocateInstance(net.minecraft.client.gui.Gui::class.java)
		unsafe.putObject(gui, unsafe.objectFieldOffset(screenField), screen)
		unsafe.putObject(mc, unsafe.objectFieldOffset(guiField), gui)
		setInTest(false)
		try {
			check(dev.ashtonite365.apex.util.Client.screen === screen) { "fake screen not visible" }
			block()
		} finally { setInTest(true) }
	}

	@Suppress("DEPRECATION")
	fun <T> with(playerName: String, block: () -> T): T {
		instanceField.isAccessible = true
		val before = instanceField.get(null) as Minecraft?
		val mc = unsafe.allocateInstance(Minecraft::class.java) as Minecraft
		val user = unsafe.allocateInstance(User::class.java) as User
		unsafe.putObject(user, unsafe.objectFieldOffset(nameField), playerName)
		unsafe.putObject(mc, unsafe.objectFieldOffset(userField), user)
		setInstance(mc)
		try { return block() } finally { setInstance(before) }
	}
}
