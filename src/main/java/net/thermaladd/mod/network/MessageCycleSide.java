package net.thermaladd.mod.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * Client -> server: a click on the Advanced Pulverizer's Configuration tab side-cross,
 * mirroring Thermal Expansion's own TabConfiguration interaction model (left-click = cycle
 * forward, right-click = cycle backward, shift-click = reset).
 */
public class MessageCycleSide implements IMessage {

    public static final int ACTION_FORWARD = 0;
    public static final int ACTION_BACKWARD = 1;
    public static final int ACTION_RESET_ONE = 2;
    public static final int ACTION_RESET_ALL = 3;

    private int x, y, z, side, action;

    public MessageCycleSide() {
    }

    public MessageCycleSide(int x, int y, int z, int side, int action) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.side = side;
        this.action = action;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeByte(side);
        buf.writeByte(action);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        side = buf.readByte();
        action = buf.readByte();
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

    public int getSide() {
        return side;
    }

    public int getAction() {
        return action;
    }
}
