package net.thermaladd.mod.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import net.thermaladd.mod.config.ModConfig;
import net.thermaladd.mod.tileentity.TileSingularityCell;

/**
 * Server -> client on login: the server's machine tunables, so a client whose own config differs
 * shows the server's real numbers rather than its own. See ModConfig#snapshotMachineValues.
 */
public class MessageConfigSync implements IMessage {

    int[] machineValues;
    long cellCapacity;

    public MessageConfigSync() {
    }

    public static MessageConfigSync ofCurrentConfig() {
        MessageConfigSync message = new MessageConfigSync();
        message.machineValues = ModConfig.snapshotMachineValues();
        message.cellCapacity = TileSingularityCell.CAPACITY;
        return message;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(machineValues.length);
        for (int i = 0; i < machineValues.length; i++) {
            buf.writeInt(machineValues[i]);
        }
        buf.writeLong(cellCapacity);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readUnsignedByte();
        machineValues = new int[count];
        for (int i = 0; i < count; i++) {
            machineValues[i] = buf.readInt();
        }
        cellCapacity = buf.readLong();
    }
}
