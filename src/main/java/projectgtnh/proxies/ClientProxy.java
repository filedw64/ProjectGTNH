package projectgtnh.proxies;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import projectgtnh.events.KeyPressEvent;
import projectgtnh.events.PlayerRender;
import projectgtnh.events.ToolTipEvent;
import projectgtnh.events.TransmutationRenderingEvent;
import projectgtnh.gameObjs.ObjHandler;
import projectgtnh.gameObjs.entity.EntityFireProjectile;
import projectgtnh.gameObjs.entity.EntityLavaProjectile;
import projectgtnh.gameObjs.entity.EntityLensProjectile;
import projectgtnh.gameObjs.entity.EntityLootBall;
import projectgtnh.gameObjs.entity.EntityMobRandomizer;
import projectgtnh.gameObjs.entity.EntityNovaCataclysmPrimed;
import projectgtnh.gameObjs.entity.EntityNovaCatalystPrimed;
import projectgtnh.gameObjs.entity.EntitySWRGProjectile;
import projectgtnh.gameObjs.entity.EntityWaterProjectile;
import projectgtnh.gameObjs.tiles.AlchChestTile;
import projectgtnh.gameObjs.tiles.CondenserMK2Tile;
import projectgtnh.gameObjs.tiles.CondenserTile;
import projectgtnh.gameObjs.tiles.DMPedestalTile;
import projectgtnh.manual.ManualPageHandler;
import projectgtnh.playerData.AlchBagProps;
import projectgtnh.playerData.Transmutation;
import projectgtnh.playerData.TransmutationProps;
import projectgtnh.rendering.ChestItemRenderer;
import projectgtnh.rendering.ChestRenderer;
import projectgtnh.rendering.CondenserItemRenderer;
import projectgtnh.rendering.CondenserMK2ItemRenderer;
import projectgtnh.rendering.CondenserMK2Renderer;
import projectgtnh.rendering.CondenserRenderer;
import projectgtnh.rendering.NovaCataclysmRenderer;
import projectgtnh.rendering.NovaCatalystRenderer;
import projectgtnh.rendering.PedestalItemRenderer;
import projectgtnh.rendering.PedestalRenderer;
import projectgtnh.utils.ClientKeyHelper;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy implements IProxy
{
	// These three following methods are here to prevent a strange crash in the dedicated server whenever packets are received
	// and the wrapped methods are called directly.

	@Override
	public void clearClientKnowledge()
	{
		Transmutation.clearKnowledge(FMLClientHandler.instance().getClientPlayerEntity());
	}

	@Override
	public TransmutationProps getClientTransmutationProps()
	{
		return TransmutationProps.getDataFor(FMLClientHandler.instance().getClientPlayerEntity());
	}

	@Override
	public AlchBagProps getClientBagProps()
	{
		return AlchBagProps.getDataFor(FMLClientHandler.instance().getClientPlayerEntity());
	}

	@Override
	public void registerKeyBinds()
	{
		ClientKeyHelper.registerMCBindings();
	}

	@Override
	public void registerRenderers() 
	{
		//Items
		MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(ObjHandler.alchChest), new ChestItemRenderer());
		MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(ObjHandler.condenser), new CondenserItemRenderer());
		MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(ObjHandler.condenserMk2), new CondenserMK2ItemRenderer());
		MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(ObjHandler.dmPedestal), new PedestalItemRenderer());

		//Blocks
		ClientRegistry.bindTileEntitySpecialRenderer(AlchChestTile.class, new ChestRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(CondenserTile.class, new CondenserRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(CondenserMK2Tile.class, new CondenserMK2Renderer());
		ClientRegistry.bindTileEntitySpecialRenderer(DMPedestalTile.class, new PedestalRenderer());
		
		//Entities
		RenderingRegistry.registerEntityRenderingHandler(EntityWaterProjectile.class, new RenderSnowball(ObjHandler.waterOrb));
		RenderingRegistry.registerEntityRenderingHandler(EntityLavaProjectile.class, new RenderSnowball(ObjHandler.lavaOrb));
		RenderingRegistry.registerEntityRenderingHandler(EntityLootBall.class, new RenderSnowball(ObjHandler.lootBall));
		RenderingRegistry.registerEntityRenderingHandler(EntityMobRandomizer.class, new RenderSnowball(ObjHandler.mobRandomizer));
		RenderingRegistry.registerEntityRenderingHandler(EntityLensProjectile.class, new RenderSnowball(ObjHandler.lensExplosive));
		RenderingRegistry.registerEntityRenderingHandler(EntityNovaCatalystPrimed.class, new NovaCatalystRenderer());
		RenderingRegistry.registerEntityRenderingHandler(EntityNovaCataclysmPrimed.class, new NovaCataclysmRenderer());
		RenderingRegistry.registerEntityRenderingHandler(EntityFireProjectile.class, new RenderSnowball(ObjHandler.fireProjectile));
		RenderingRegistry.registerEntityRenderingHandler(EntitySWRGProjectile.class, new RenderSnowball(ObjHandler.windProjectile));
	}
	
	@Override
	public void registerClientOnlyEvents() 
	{
		MinecraftForge.EVENT_BUS.register(new ToolTipEvent());
		MinecraftForge.EVENT_BUS.register(new TransmutationRenderingEvent());
		FMLCommonHandler.instance().bus().register(new KeyPressEvent());

		PlayerRender pr = new PlayerRender();
		MinecraftForge.EVENT_BUS.register(pr);
		FMLCommonHandler.instance().bus().register(pr);
	}

	@Override
	public void initializeManual()
	{
		ManualPageHandler.init();
	}

	@Override
	public EntityPlayer getClientPlayer()
	{
		return FMLClientHandler.instance().getClientPlayerEntity();
	}

	@Override
	public boolean isJumpPressed()
	{
		return FMLClientHandler.instance().getClient().gameSettings.keyBindJump.getIsKeyPressed();
	}
}

