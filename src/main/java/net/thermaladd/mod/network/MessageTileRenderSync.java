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
    /**
     * Whether the tile is actively processing right now - carried so the client can both
     * repaint the idle/active face icon and (for the 3 machines with a real TE ambient sound -
     * see MessageTileRenderSyncHandler) start/stop the looping machine sound, without needing a
     * player to have the GUI open (windowProperty sync only reaches a client whose GUI is open;
     * this packet reaches every nearby client). Machines/blocks with no such concept (the
     * Assembler, the Singularity Cell) just use the short constructor, which defaults this to
     * false and is otherwise ignored on the receiving end.
     */
    private boolean active;

    public MessageTileRenderSync() {
    }

    public MessageTileRenderSync(int x, int y, int z, byte facing, byte[] sideCache) {
        this(x, y, z, facing, sideCache, false);
    }

    public MessageTileRenderSync(int x, int y, int z, byte facing, byte[] sideCache, boolean active) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
        this.sideCache = sideCache;
        this.active = active;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeByte(facing);
        buf.writeBytes(sideCache);
        buf.writeBoolean(active);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        facing = buf.readByte();
        sideCache = new byte[6];
        buf.readBytes(sideCache);
        active = buf.readBoolean();
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

    public boolean isActive() {
        return active;
    }
}
