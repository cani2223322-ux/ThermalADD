package net.thermaladd.mod.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * Server -> client: "this machine's facing/side modes changed, repaint it."
 *
 * Why this exists: {@code facing} and the per-side mode cache live on the TileEntity, not in
 * block metadata, so vanilla's normal block-change networking (which only fires for
 * setBlock/setBlockMetadataWithNotify) never reaches them. Calling
 * {@code World#markBlockForUpdate} on the SERVER's own World instance is a no-op for the
 * client's renderer - that method only schedules a chunk remesh on whichever World it's
 * called against, and the server's World and the client's World are different objects (even
 * in singleplayer). Without this packet, a face only repaints once some unrelated nearby
 * block change happens to force a full chunk-section remesh anyway - which is exactly the
 * symptom this fixes.
 */
public class MessageTileRenderSync implements IMessage {

    private int x, y, z;
    private byte facing;
    private byte[] sideCache;

    public MessageTileRenderSync() {
    }

    public MessageTileRenderSync(int x, int y, int z, byte facing, byte[] sideCache) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
        this.sideCache = sideCache;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeByte(facing);
        buf.writeBytes(sideCache);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        facing = buf.readByte();
        sideCache = new byte[6];
        buf.readBytes(sideCache);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public byte getFacing() {
        return facing;
    }

    public byte[] getSideCache() {
        return sideCache;
    }
}
