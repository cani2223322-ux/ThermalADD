package net.thermaladd.mod.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/**
 * Stand-in for a client-only message handler on the dedicated server. FML's
 * SimpleNetworkWrapper#registerMessage(Class, ...) instantiates the handler class on BOTH sides,
 * whatever Side it is registered for, and loading the real client handlers there fails - they
 * touch net.minecraft.client.Minecraft, which the server jar does not have. The server never
 * receives these messages, so a handler that does nothing is all it needs.
 */
public class MessageIgnoreHandler<REQ extends IMessage> implements IMessageHandler<REQ, IMessage> {

    @Override
    public IMessage onMessage(REQ message, MessageContext ctx) {
        return null;
    }
}
