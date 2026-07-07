package projecte.proxies;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;
import projecte.events.KeyPressEvent;
import projecte.events.PlayerRender;
import projecte.events.ToolTipEvent;
import projecte.events.TransmutationRenderingEvent;
import projecte.gameObjs.ObjHandler;
import projecte.gameObjs.entity.EntityFireProjectile;
import projecte.gameObjs.entity.EntityLavaProjectile;
import projecte.gameObjs.entity.EntityLensProjectile;
import projecte.gameObjs.entity.EntityLootBall;
import projecte.gameObjs.entity.EntityMobRandomizer;
import projecte.gameObjs.entity.EntityNovaCataclysmPrimed;
import projecte.gameObjs.entity.EntityNovaCatalystPrimed;
import projecte.gameObjs.entity.EntitySWRGProjectile;
import projecte.gameObjs.entity.EntityWaterProjectile;
import projecte.gameObjs.tiles.AlchChestTile;
import projecte.gameObjs.tiles.CondenserMK2Tile;
import projecte.gameObjs.tiles.CondenserTile;
import projecte.gameObjs.tiles.DMPedestalTile;
import projecte.manual.ManualPageHandler;
import projecte.playerData.AlchBagProps;
import projecte.playerData.Transmutation;
import projecte.playerData.TransmutationProps;
import projecte.rendering.ChestItemRenderer;
import projecte.rendering.ChestRenderer;
import projecte.rendering.CondenserItemRenderer;
import projecte.rendering.CondenserMK2ItemRenderer;
import projecte.rendering.CondenserMK2Renderer;
import projecte.rendering.CondenserRenderer;
import projecte.rendering.NovaCataclysmRenderer;
import projecte.rendering.NovaCatalystRenderer;
import projecte.rendering.PedestalItemRenderer;
import projecte.rendering.PedestalRenderer;
import projecte.utils.ClientKeyHelper;

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
