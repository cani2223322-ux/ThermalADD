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
        // Client-bound handlers come from the proxy: FML instantiates a handler passed by class on
        // both sides, and the real ones cannot even be loaded on a dedicated server.
        INSTANCE.registerMessage(ThermalADD.proxy.tileRenderSyncHandler(), MessageTileRenderSync.class, 1, Side.CLIENT);
        INSTANCE.registerMessage(MessageSetRedstoneControlHandler.class, MessageSetRedstoneControl.class, 2, Side.SERVER);
        INSTANCE.registerMessage(ThermalADD.proxy.energyCellSyncHandler(), MessageEnergyCellSync.class, 3, Side.CLIENT);
        INSTANCE.registerMessage(MessageConfigSyncHandler.class, MessageConfigSync.class, 4, Side.CLIENT);
        INSTANCE.registerMessage(MessageMachineModeHandler.class, MessageMachineMode.class, 5, Side.SERVER);
    }
}
