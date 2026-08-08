package moze_intel.projecte.integration;

import com.caedis.duradisplay.overlay.OverlayPECharge;
import com.caedis.duradisplay.render.DurabilityRenderer;

public class DuraDisplayInit {
	public static void init() {
		DurabilityRenderer.addHandlers(new OverlayPECharge());
	}
}
