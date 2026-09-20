package net.thermaladd.mod.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * Server -> client: the Singularity Cell's true (beyond int-range) charge, sent only to the
 * player with its GUI open - see ContainerSingularityCell#detectAndSendChanges for why this
 * can't just ride the normal windowProperty/progress-bar channel like this mod's other machines'
 * stats do.
 */
public class MessageEnergyCellSync implements IMessage {

    private int x, y, z;
    private long energy;

    public MessageEnergyCellSync() {
    }

    public MessageEnergyCellSync(int x, int y, int z, long energy) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.energy = energy;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeLong(energy);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        energy = buf.readLong();
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

    public long getEnergy() {
        return energy;
    }
}
