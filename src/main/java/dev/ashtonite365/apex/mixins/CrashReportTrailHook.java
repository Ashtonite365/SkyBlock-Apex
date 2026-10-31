/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex error trail hook: after Minecraft saves a crash report, write the buffered trail next to the logs.
 */
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.util.trail.Trail;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrashReport.class)
public abstract class CrashReportTrailHook {
	@Shadow
	public abstract String getTitle();

	@Shadow
	public abstract Throwable getException();

	@Inject(method = "saveToFile(Ljava/nio/file/Path;Lnet/minecraft/ReportType;Ljava/util/List;)Z", at = @At("RETURN"))
	private void apex$writeErrorTrail(Path file, ReportType type, List<String> extraInfo, CallbackInfoReturnable<Boolean> cir) {
		try {
			Trail.onCrashReport(getTitle(), getException(), file);
		} catch (Throwable ignored) {

		}
	}
}
