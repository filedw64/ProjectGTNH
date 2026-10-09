package moze_intel.projecte.gameObjs.items.tools;
public class RedBow extends PEBowBase {
	public RedBow() { super("rm_bow"); }
	@Override public float getVelocityMultiplier() { return 5.0F; }
	@Override public int getDrawTime() { return 5; }
	@Override public int getOverlayColor() { return 0x8B0000; }
	@Override public boolean ignoresGravity() { return true; }
	@Override public boolean spawnsLightning() { return false; }
	@Override public int getEmcCost() { return 1024; }
}
