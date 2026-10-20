package moze_intel.projecte.primordial;

import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;

public final class EntityEmc {
    public static double price(Entity entity) {
        if (!(entity instanceof EntityLivingBase) || entity instanceof EntityPlayer || entity.isDead) return 0;
        String name = EntityList.getEntityString(entity);
        if (name == null) return 0;
        for (String entry : PrimordialConfig.entityPrices) {
            int split = entry.indexOf('=');
            if (split > 0 && entry.substring(0, split).equals(name)) {
                try { double p = Double.parseDouble(entry.substring(split + 1)); return Double.isFinite(p) && p > 0 ? p : 0; }
                catch (NumberFormatException ignored) { return 0; }
            }
        }
        double health = ((EntityLivingBase) entity).getMaxHealth();
        int multiplier = entity instanceof EntityDragon || entity instanceof EntityWither ? 64 : entity instanceof IMob ? 2 : 1;
        return Math.ceil(Math.max(1, Math.min(1000000, health))) * 128 * multiplier;
    }
    private EntityEmc() {}
}
