package net.thermaladd.mod.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * Client -> server: a click on the Redstone Control tab (see client.gui.TabRedstoneControl),
 * picking one of the 3 real cofh.api.tileentity.IRedstoneControl.ControlMode values (Disabled/
 * Low/High) by its ordinal.
 */
public class MessageSetRedstoneControl implements IMessage {

    private int x, y, z;
    private int mode;

    public MessageSetRedstoneControl() {
    }

    public MessageSetRedstoneControl(int x, int y, int z, int mode) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.mode = mode;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeByte(mode);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        mode = buf.readByte();
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

    public int getMode() {
        return mode;
    }
}
