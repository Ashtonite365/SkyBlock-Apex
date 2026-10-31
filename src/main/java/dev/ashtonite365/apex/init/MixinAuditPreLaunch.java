package dev.ashtonite365.apex.init;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.spongepowered.asm.mixin.MixinEnvironment;

public class MixinAuditPreLaunch implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		if (!Boolean.getBoolean("apex.mixinAudit")) return;
		int code = 0;
		try {
			MixinEnvironment.getCurrentEnvironment().audit();
			System.out.println("APEX_MIXIN_AUDIT: OK");
		} catch (Throwable t) {
			t.printStackTrace();
			System.out.println("APEX_MIXIN_AUDIT: FAILED " + t);
			code = 1;
		}
		Runtime.getRuntime().halt(code);
	}
}
