package net.thermaladd.mod.proxy;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;
import net.thermaladd.mod.client.render.RenderSingularityStrongbox;
import net.thermaladd.mod.client.render.RenderSingularityTank;
import net.thermaladd.mod.handler.ClientConfigEvents;
import net.thermaladd.mod.init.ModBlocks;
import net.thermaladd.mod.tileentity.TileSingularityStrongbox;
import net.thermaladd.mod.tileentity.TileSingularityTank;

public class ClientProxy extends CommonProxy {

    @Override
    public void registerClientEvents() {
        FMLCommonHandler.instance().bus().register(new ClientConfigEvents());
    }

    /**
     * The machines render as plain cubes. The Singularity Tank draws its fluid and inner frame
     * with a tile renderer on top of its block, and the Singularity Strongbox is TE's strongbox
     * model throughout - in the world and as an item.
     */
    @Override
    public void registerRenderers() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileSingularityTank.class, new RenderSingularityTank());
        RenderSingularityStrongbox strongbox = new RenderSingularityStrongbox();
        ClientRegistry.bindTileEntitySpecialRenderer(TileSingularityStrongbox.class, strongbox);
        MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(ModBlocks.singularityStrongbox), strongbox);
    }
}
