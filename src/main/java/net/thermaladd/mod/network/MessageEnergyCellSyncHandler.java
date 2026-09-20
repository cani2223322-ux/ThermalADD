package net.thermaladd.mod.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.thermaladd.mod.tileentity.TileSingularityCell;

/** Same plain-field-write pattern as MessageTileRenderSyncHandler - see that class for why no main-thread scheduling is needed here. */
public class MessageEnergyCellSyncHandler implements IMessageHandler<MessageEnergyCellSync, IMessage> {

    @Override
    public IMessage onMessage(MessageEnergyCellSync message, MessageContext ctx) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) {
            return null;
        }
        TileEntity te = world.getTileEntity(message.getX(), message.getY(), message.getZ());
        if (te instanceof TileSingularityCell) {
            ((TileSingularityCell) te).setEnergyStoredClient(message.getEnergy(), message.getEnergyIn(), message.getEnergyOut());
        }
        return null;
    }
}
