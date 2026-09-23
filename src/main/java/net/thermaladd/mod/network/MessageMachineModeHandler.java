package net.thermaladd.mod.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.thermaladd.mod.tileentity.TileSingularityMachine;

/** Range check before the lookup, as in MessageCycleSideHandler; the tile validates the mode itself. */
public class MessageMachineModeHandler implements IMessageHandler<MessageMachineMode, IMessage> {

    @Override
    public IMessage onMessage(MessageMachineMode message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        if (player.getDistanceSq(message.getX() + 0.5, message.getY() + 0.5, message.getZ() + 0.5) > 64.0) {
            return null;
        }
        TileEntity te = player.worldObj.getTileEntity(message.getX(), message.getY(), message.getZ());
        if (te instanceof TileSingularityMachine) {
            ((TileSingularityMachine) te).setMachineMode(message.getMode());
        }
        return null;
    }
}
