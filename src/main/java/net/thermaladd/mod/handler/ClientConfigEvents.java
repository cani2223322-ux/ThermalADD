package net.thermaladd.mod.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import net.thermaladd.mod.config.ModConfig;

/**
 * Client side of the config sync: after leaving a server that sent its own machine tunables, put
 * this client's own back - otherwise a single-player world opened next would run on the previous
 * server's numbers. Registered from ClientProxy only.
 */
public class ClientConfigEvents {

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ModConfig.restoreLocalValues();
    }
}
