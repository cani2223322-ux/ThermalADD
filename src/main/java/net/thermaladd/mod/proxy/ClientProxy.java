package net.thermaladd.mod.proxy;

public class ClientProxy extends CommonProxy {

    @Override
    public void registerRenderers() {
        // block renders as a normal full cube (BlockContainer + renderAsNormalBlock); the
        // GUI is opened through GuiHandler, no custom TileEntitySpecialRenderer is needed.
    }
}
