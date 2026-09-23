package net.thermaladd.mod.proxy;

import cpw.mods.fml.common.FMLCommonHandler;
import net.thermaladd.mod.handler.ClientConfigEvents;

public class ClientProxy extends CommonProxy {

    @Override
    public void registerClientEvents() {
        FMLCommonHandler.instance().bus().register(new ClientConfigEvents());
    }

    @Override
    public void registerRenderers() {
        // block renders as a normal full cube (BlockContainer + renderAsNormalBlock); the
        // GUI is opened through GuiHandler, no custom TileEntitySpecialRenderer is needed.
    }
}
