package moze_intel.projecte.integration;

import com.caedis.duradisplay.overlay.OverlayCharge;
import com.caedis.duradisplay.render.BarRenderer;
import com.caedis.duradisplay.render.DurabilityRenderer;
import com.caedis.duradisplay.render.OverlayRenderer;
import com.caedis.duradisplay.utils.ColorType;
import moze_intel.projecte.gameObjs.items.ItemCharge;
import moze_intel.projecte.gameObjs.items.KleinStar;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class DuraDisplayInit {
	public static class OverlayPECharge extends OverlayCharge {
		public OverlayPECharge() {
			config.enabled = true;
			config.style = Style.Bar;
		}
		@Override
		public OverlayRenderer getRenderer(ItemStack is) {
			Item item = is.getItem();
			if (!(item instanceof ItemCharge || item instanceof KleinStar))
				return null;
			if (!item.showDurabilityBar(is))
				return null;
			double per = 1.0D - item.getDurabilityForDisplay(is);
			return new BarRenderer(
				ColorType.Vanilla.get(per, null),
				per,
				true,
				1,
				true);
		}
	}
	public static void init() {
		DurabilityRenderer.addHandlers(new OverlayPECharge());
	}
}