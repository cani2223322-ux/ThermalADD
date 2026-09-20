package net.thermaladd.mod.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;
import net.thermaladd.mod.tileentity.TileSingularityCell;

/**
 * Runs on the client, applying the synced facing/side-cache and repainting the block on the
 * client's own World - which World#markBlockForUpdate actually reaches from here (unlike on
 * the server, see TileAdvancedPulverizer#syncRenderState). Plain field writes + a render-flag
 * call, same as CoFH's own tile-sync packets do in this same version - no main-thread
 * scheduling needed for that.
 */
public class MessageTileRenderSyncHandler implements IMessageHandler<MessageTileRenderSync, IMessage> {

    @Override
    public IMessage onMessage(MessageTileRenderSync message, MessageContext ctx) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) {
            return null;
        }
        TileEntity te = world.getTileEntity(message.getX(), message.getY(), message.getZ());
        byte[] sides = message.getSideCache();

        if (te instanceof TileAdvancedPulverizer) {
            TileAdvancedPulverizer tile = (TileAdvancedPulverizer) te;
            tile.setFacingClient(message.getFacing());
            for (int i = 0; i < sides.length; i++) {
                tile.setSideModeClient(i, sides[i]);
            }
        } else if (te instanceof TileImprovedAssembler) {
            TileImprovedAssembler tile = (TileImprovedAssembler) te;
            for (int i = 0; i < sides.length; i++) {
                tile.setSideModeClient(i, sides[i]);
            }
        } else if (te instanceof TileAdvancedFurnace) {
            TileAdvancedFurnace tile = (TileAdvancedFurnace) te;
            tile.setFacingClient(message.getFacing());
            for (int i = 0; i < sides.length; i++) {
                tile.setSideModeClient(i, sides[i]);
            }
        } else if (te instanceof TileSingularityCell) {
            TileSingularityCell tile = (TileSingularityCell) te;
            for (int i = 0; i < sides.length; i++) {
                tile.setSideModeClient(i, sides[i]);
            }
        } else {
            return null;
        }

        world.markBlockForUpdate(message.getX(), message.getY(), message.getZ());
        return null;
    }
}
