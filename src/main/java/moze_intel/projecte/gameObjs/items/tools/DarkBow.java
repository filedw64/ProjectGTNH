// DarkBow.java
package moze_intel.projecte.gameObjs.items.tools;
public class DarkBow extends PEBowBase {
	public DarkBow() { super("dm_bow"); }
	@Override public float getVelocityMultiplier() { return 3.0F; }
	@Override public int getDrawTime() { return 10; }
	@Override public int getOverlayColor() { return 0x222222; }
	@Override public boolean ignoresGravity() { return false; }
	@Override public boolean spawnsLightning() { return false; }
	@Override public int getEmcCost() { return 256; }
}
