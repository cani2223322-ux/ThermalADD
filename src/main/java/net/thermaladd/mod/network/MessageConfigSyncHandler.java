package net.thermaladd.mod.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.thermaladd.mod.config.ModConfig;

/**
 * Client side of MessageConfigSync. In single player the integrated server shares these static
 * fields with the client, but it is sending the very values already there, so this is a no-op.
 */
public class MessageConfigSyncHandler implements IMessageHandler<MessageConfigSync, IMessage> {

    @Override
    public IMessage onMessage(MessageConfigSync message, MessageContext ctx) {
        ModConfig.applyMachineValues(message.machineValues, message.cellCapacity);
        return null;
    }
}
