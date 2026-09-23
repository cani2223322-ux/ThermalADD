package net.thermaladd.mod.proxy;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import net.thermaladd.mod.network.MessageEnergyCellSync;
import net.thermaladd.mod.network.MessageIgnoreHandler;
import net.thermaladd.mod.network.MessageTileRenderSync;

public class CommonProxy {

    public void registerRenderers() {
        // no-op on the dedicated server
    }

    public void registerClientEvents() {
        // no-op on the dedicated server
    }

    /**
     * Handlers for the server-to-client messages. The real ones reference client-only classes, so
     * the dedicated server gets do-nothing stand-ins - see MessageIgnoreHandler for why the class
     * itself must never be handed to FML there.
     */
    public IMessageHandler<MessageTileRenderSync, IMessage> tileRenderSyncHandler() {
        return new MessageIgnoreHandler<MessageTileRenderSync>();
    }

    public IMessageHandler<MessageEnergyCellSync, IMessage> energyCellSyncHandler() {
        return new MessageIgnoreHandler<MessageEnergyCellSync>();
    }
}
