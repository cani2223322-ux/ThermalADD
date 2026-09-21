package net.thermaladd.mod.network;

import cofh.api.tileentity.IRedstoneControl;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;
import net.thermaladd.mod.tileentity.TileAdvancedSawmill;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;

/** All machines implement the real cofh.api.tileentity.IRedstoneControl, so one handler covers them all. */
public class MessageSetRedstoneControlHandler implements IMessageHandler<MessageSetRedstoneControl, IMessage> {

    @Override
    public IMessage onMessage(MessageSetRedstoneControl message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        if (player.getDistanceSq(message.getX() + 0.5, message.getY() + 0.5, message.getZ() + 0.5) > 64.0) {
            return null;
        }
        TileEntity te = player.worldObj.getTileEntity(message.getX(), message.getY(), message.getZ());

        boolean augmentInstalled = (te instanceof TileAdvancedPulverizer && ((TileAdvancedPulverizer) te).augmentRedstoneControl)
                || (te instanceof TileAdvancedFurnace && ((TileAdvancedFurnace) te).augmentRedstoneControl)
                || (te instanceof TileImprovedAssembler && ((TileImprovedAssembler) te).augmentRedstoneControl)
                || (te instanceof TileAdvancedSawmill && ((TileAdvancedSawmill) te).augmentRedstoneControl);
        if (!augmentInstalled || !(te instanceof IRedstoneControl)) {
            return null;
        }

        int ordinal = message.getMode();
        IRedstoneControl.ControlMode[] modes = IRedstoneControl.ControlMode.values();
        if (ordinal < 0 || ordinal >= modes.length) {
            return null;
        }
        ((IRedstoneControl) te).setControl(modes[ordinal]);
        return null;
    }
}
