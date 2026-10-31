package dev.ashtonite365.apex.test

import org.junit.jupiter.api.Test
import org.spongepowered.asm.mixin.MixinEnvironment

class MixinAuditTest {
	@Test
	fun auditAllMixins() {
		val env = MixinEnvironment.getCurrentEnvironment()
		env.setOption(MixinEnvironment.Option.DEBUG_INJECTORS, true)
		env.audit()
	}
}
