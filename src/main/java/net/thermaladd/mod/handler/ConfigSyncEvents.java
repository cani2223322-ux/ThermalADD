package net.thermaladd.mod.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.thermaladd.mod.network.MessageConfigSync;
import net.thermaladd.mod.network.PacketHandler;

/** Server side of the config sync: every joining player gets the server's machine tunables. */
public class ConfigSyncEvents {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            PacketHandler.INSTANCE.sendTo(MessageConfigSync.ofCurrentConfig(), (EntityPlayerMP) event.player);
        }
    }
}
