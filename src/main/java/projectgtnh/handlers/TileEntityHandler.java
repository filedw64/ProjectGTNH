package projectgtnh.handlers;

import com.google.common.collect.Sets;
import projectgtnh.gameObjs.tiles.CondenserTile;
import projectgtnh.utils.PELogger;
import net.minecraft.world.World;

import java.util.Set;

public class TileEntityHandler
{
	// The set of all (chunk)loaded condensers on the server.
	private static final Set<CondenserTile> CONDENSERS = Sets.newHashSet();

	public static void addCondenser(CondenserTile tile)
	{
		PELogger.logDebug(String.format("Added condenser at coords %s, %s, %s", Integer.toString(tile.xCoord), Integer.toString(tile.yCoord), Integer.toString(tile.zCoord)));
		CONDENSERS.add(tile);
	}

	public static void removeCondenser(CondenserTile tile)
	{
		if (CONDENSERS.remove(tile))
		{
			PELogger.logDebug(String.format("Removed condenser at coords %s, %s, %s", Integer.toString(tile.xCoord), Integer.toString(tile.yCoord), Integer.toString(tile.zCoord)));
		}
	}

	public static void checkAllCondensers()
	{
		for (CondenserTile t : CONDENSERS)
		{
			t.checkLockAndUpdate();
		}
	}

	public static void clearAll()
	{
		CONDENSERS.clear();
	}
}

