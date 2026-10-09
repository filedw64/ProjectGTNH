package moze_intel.projecte.gameObjs.items.tools;

public class GemBow extends PEBowBase {
	public GemBow() { super("storm_bow"); }
	@Override public float getVelocityMultiplier() { return 10.0F; }
	@Override public int getDrawTime() { return 3; }
	@Override public int getOverlayColor() { return 0x4444FF; }
	@Override public boolean ignoresGravity() { return true; }
	@Override public boolean spawnsLightning() { return true; }
	@Override public int getEmcCost() { return 4096; }
}
