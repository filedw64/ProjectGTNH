package projectgtnh.gameObjs.tiles;

import projectgtnh.utils.Constants;

public class CollectorMK3Tile extends CollectorMK1Tile
{
	public CollectorMK3Tile()
	{
		super(Constants.COLLECTOR_MK3_MAX, Constants.COLLECTOR_MK3_GEN, 17, 18);
	}

	@Override
	public String getInventoryName()
	{
		return "tile.pe_collector_MK3.name";
	}
}
