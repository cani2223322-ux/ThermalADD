package net.thermaladd.mod.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.thermaladd.mod.tileentity.TileSingularityCell;

/** Handed to the client thread for the same reason as MessageTileRenderSyncHandler - see that class. */
public class MessageEnergyCellSyncHandler implements IMessageHandler<MessageEnergyCellSync, IMessage> {

    @Override
    public IMessage onMessage(final MessageEnergyCellSync message, MessageContext ctx) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                apply(message);
            }
        });
        return null;
    }

    private static void apply(MessageEnergyCellSync message) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) {
            return;
        }
        TileEntity te = world.getTileEntity(message.getX(), message.getY(), message.getZ());
        if (te instanceof TileSingularityCell) {
            ((TileSingularityCell) te).setEnergyStoredClient(message.getEnergy(), message.getEnergyIn(), message.getEnergyOut());
        }
    }
}
