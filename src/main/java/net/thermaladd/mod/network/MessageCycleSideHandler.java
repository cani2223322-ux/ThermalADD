package net.thermaladd.mod.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;

/**
 * One shared "cycle this side's mode" message for every machine in the mod that has a
 * Configuration tab - both {@link TileAdvancedPulverizer} and {@link TileImprovedAssembler}
 * expose the same cycleSideMode/resetSideMode/resetAllSideModes shape, so a single message
 * type dispatches by whichever tile is actually found at (x, y, z) instead of needing one
 * packet class per machine.
 */
public class MessageCycleSideHandler implements IMessageHandler<MessageCycleSide, IMessage> {

    @Override
    public IMessage onMessage(MessageCycleSide message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        TileEntity te = player.worldObj.getTileEntity(message.getX(), message.getY(), message.getZ());
        if (player.getDistanceSq(message.getX() + 0.5, message.getY() + 0.5, message.getZ() + 0.5) > 64.0) {
            return null;
        }

        if (te instanceof TileAdvancedPulverizer) {
            TileAdvancedPulverizer tile = (TileAdvancedPulverizer) te;
            switch (message.getAction()) {
                case MessageCycleSide.ACTION_FORWARD:
                    tile.cycleSideMode(message.getSide(), 1);
                    break;
                case MessageCycleSide.ACTION_BACKWARD:
                    tile.cycleSideMode(message.getSide(), -1);
                    break;
                case MessageCycleSide.ACTION_RESET_ONE:
                    tile.resetSideMode(message.getSide());
                    break;
                case MessageCycleSide.ACTION_RESET_ALL:
                    tile.resetAllSideModes();
                    break;
                default:
                    break;
            }
        } else if (te instanceof TileImprovedAssembler) {
            TileImprovedAssembler tile = (TileImprovedAssembler) te;
            switch (message.getAction()) {
                case MessageCycleSide.ACTION_FORWARD:
                    tile.cycleSideMode(message.getSide(), 1);
                    break;
                case MessageCycleSide.ACTION_BACKWARD:
                    tile.cycleSideMode(message.getSide(), -1);
                    break;
                case MessageCycleSide.ACTION_RESET_ONE:
                    tile.resetSideMode(message.getSide());
                    break;
                case MessageCycleSide.ACTION_RESET_ALL:
                    tile.resetAllSideModes();
                    break;
                default:
                    break;
            }
        } else if (te instanceof TileAdvancedFurnace) {
            TileAdvancedFurnace tile = (TileAdvancedFurnace) te;
            switch (message.getAction()) {
                case MessageCycleSide.ACTION_FORWARD:
                    tile.cycleSideMode(message.getSide(), 1);
                    break;
                case MessageCycleSide.ACTION_BACKWARD:
                    tile.cycleSideMode(message.getSide(), -1);
                    break;
                case MessageCycleSide.ACTION_RESET_ONE:
                    tile.resetSideMode(message.getSide());
                    break;
                case MessageCycleSide.ACTION_RESET_ALL:
                    tile.resetAllSideModes();
                    break;
                default:
                    break;
            }
        }
        return null;
    }
}
