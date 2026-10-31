// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.init;

public class EarlyRiser
	implements Runnable {
	@Override
	public void run() {
		for (BytecodeRiser riser : new BytecodeRiser[]{new ScreenInputRiser(), new CompoundTagMutationFinalizationDetectorInjector()}) {
			riser.register();
		}
	}
}
