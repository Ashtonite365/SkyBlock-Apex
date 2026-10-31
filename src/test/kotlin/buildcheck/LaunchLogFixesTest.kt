// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.buildcheck

import java.io.File
import java.util.UUID
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.repo.LearnedSkullTextures
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.mc.applyEncodedSkin

@ExtendWith(AutoBootstrapExtension::class)
class LaunchLogFixesTest {
	private val project = File(System.getProperty("apex.projectDir") ?: error("apex.projectDir not set by the build"))

	@Test fun noPrintingLocalCaptures() {
		val offenders = File(project, "src").walkTopDown()
			.filter { it.isFile && (it.extension == "java" || it.extension == "kt") && "test" !in it.relativeTo(project).invariantSeparatorsPath.split('/') }
			.filter { f -> Regex("""@Local\([^)]*print\s*=\s*true""").containsMatchIn(f.readText()) }
			.map { it.relativeTo(project).invariantSeparatorsPath }
			.toList()
		assertEquals(emptyList<String>(), offenders)
	}

	@Test fun fontResourcePathsAreValid() {
		val fontDir = File(project, "src/main/resources/assets/apex/font")
		assertTrue(fontDir.isDirectory)
		val bad = fontDir.walkTopDown().filter { it.isFile }
			.map { it.relativeTo(fontDir).invariantSeparatorsPath }
			.filterNot { Regex("[a-z0-9_./-]+").matches(it) }
			.toList()
		assertEquals(emptyList<String>(), bad)
		assertFalse(File(fontDir, "wiki/OFL.txt").exists())
		assertTrue(File(project, "LICENSES/OFL-1.1.txt").isFile, "the OFL licence still ships from LICENSES/")
	}

	@Test fun learnedSkullProfileMatchesEncodedSkullOwner() {
		val learned = LearnedSkullTextures.Learned("5144d635-41a3-5652-893a-948f9a31aa59", "abc")
		val built = LearnedSkullTextures.profileOf(learned).partialProfile()
		val reference = ItemStack(Items.PLAYER_HEAD)
			.apply { applyEncodedSkin(UUID.fromString(learned.uuid), learned.value) }
			.get(DataComponents.PROFILE)!!.partialProfile()
		assertEquals(reference.id(), built.id())
		assertEquals(reference.name(), built.name())
		assertEquals(reference.properties()["textures"].map { it.value() }, built.properties()["textures"].map { it.value() })
		assertEquals(listOf("abc="), built.properties()["textures"].map { it.value() })
	}

	@Test fun learnedSkullProfileWithBadUuidFallsBackToNameUuid() {
		val built = LearnedSkullTextures.profileOf(LearnedSkullTextures.Learned("not-a-uuid", "abcd")).partialProfile()
		assertEquals(UUID.nameUUIDFromBytes("abcd".toByteArray()), built.id())
	}
}
