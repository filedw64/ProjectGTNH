package moze_intel.projecte.gameObjs.items.tools;

public class GemBow extends PEBowBase {
	public GemBow() {
		super("storm_bow");
	}

	@Override
	public float getVelocityMultiplier() { return 4.5F; }

	@Override
	public int getDrawTime() { return 8; }

	@Override
	public boolean ignoresGravity() { return true; }

	@Override
	public boolean spawnsLightning() { return true; }

	@Override
	public int getEmcCost() { return 4096; }

	@Override
	public int getHomingTier() { return 2; }

	@Override
	public String getSpecialKey() { return "pe.storm_bow.special"; }
}
