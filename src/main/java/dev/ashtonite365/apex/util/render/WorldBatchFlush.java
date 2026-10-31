package dev.ashtonite365.apex.util.render;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class WorldBatchFlush {
	private WorldBatchFlush() {}

	public interface Staging<D, I> {
		void requestIndexCount(D draw);
		void upload();

		@Nullable I executeInfo(D draw);
		int indexCount(I info);

		void resizeIndexBuffers();

		void endDraw();

		void endFrame();
	}

	public interface Drawer<D, I, P, U> {

		@Nullable U prepare(D draw, I info);
		P openPass();
		void issue(P pass, D draw, I info, U prepared);
		void closePass(P pass);
	}

	public static <D, I, P, U> int flush(List<D> draws, Staging<D, I> staging, Drawer<D, I, P, U> drawer) {
		try {
			if (draws.isEmpty()) return 0;
			for (D d : draws) staging.requestIndexCount(d);
			staging.upload();
			staging.resizeIndexBuffers();
			List<Object[]> ready = new ArrayList<>(draws.size());
			for (D d : draws) {
				I info = staging.executeInfo(d);
				if (info == null || staging.indexCount(info) == 0) continue;
				U u = drawer.prepare(d, info);
				if (u == null) continue;
				ready.add(new Object[] {d, info, u});
			}
			if (ready.isEmpty()) return 0;
			P pass = drawer.openPass();
			try {
				for (Object[] r : ready) {
					@SuppressWarnings("unchecked") D d = (D) r[0];
					@SuppressWarnings("unchecked") I info = (I) r[1];
					@SuppressWarnings("unchecked") U u = (U) r[2];
					drawer.issue(pass, d, info, u);
				}
			} finally {
				drawer.closePass(pass);
			}
			return ready.size();
		} finally {
			staging.endDraw();
			staging.endFrame();
		}
	}
}
