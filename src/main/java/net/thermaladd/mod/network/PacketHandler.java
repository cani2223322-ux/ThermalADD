package net.thermaladd.mod.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.thermaladd.mod.ThermalADD;

public class PacketHandler {

    public static final SimpleNetworkWrapper INSTANCE =
            NetworkRegistry.INSTANCE.newSimpleChannel(ThermalADD.MODID);

    public static void init() {
        INSTANCE.registerMessage(MessageCycleSideHandler.class, MessageCycleSide.class, 0, Side.SERVER);
        INSTANCE.registerMessage(MessageTileRenderSyncHandler.class, MessageTileRenderSync.class, 1, Side.CLIENT);
    }
}
