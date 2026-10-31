// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.testutil

import com.google.auto.service.AutoService
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import dev.ashtonite365.apex.test.ApexTestBootstrap

@AutoService(Extension::class)
class AutoBootstrapExtension : BeforeAllCallback {
	override fun beforeAll(context: ExtensionContext) = ApexTestBootstrap.bootstrapMinecraft()
}
