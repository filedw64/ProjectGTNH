package com.caedis.duradisplay.overlay;

import com.caedis.duradisplay.config.ConfigPECharge;
import com.caedis.duradisplay.render.OverlayRenderer;
import com.caedis.duradisplay.render.PEChargeBarRenderer;
import moze_intel.projecte.gameObjs.items.ItemCharge;
import moze_intel.projecte.gameObjs.items.KleinStar;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Restores the original charge bar rendering for ProjectE items ({@link ItemCharge} and {@link KleinStar})
 * when DuraDisplay is loaded.
 *
 * @author WindyBye
 * @author filedw64
 */
public class OverlayPECharge extends Overlay<ConfigPECharge> {

    @Override
    @NotNull
	ConfigPECharge config() {
        return new ConfigPECharge(); // never used
    }

    @Override
    public OverlayRenderer getRenderer(ItemStack is) {
        Item item = is.getItem();
        if (!(item instanceof ItemCharge || item instanceof KleinStar)) return null;
        if (!item.showDurabilityBar(is)) return null;

        double per = 1.0D - item.getDurabilityForDisplay(is);

		return PEChargeBarRenderer.instance.setPercentage(per);
    }
}
