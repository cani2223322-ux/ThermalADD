package net.thermaladd.mod.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.thermaladd.mod.tileentity.TileAdvancedCharger;
import net.thermaladd.mod.tileentity.TileAdvancedFurnace;
import net.thermaladd.mod.tileentity.TileAdvancedPulverizer;
import net.thermaladd.mod.tileentity.TileAdvancedSawmill;
import net.thermaladd.mod.tileentity.TileImprovedAssembler;
import net.thermaladd.mod.tileentity.TileSingularityCell;

import cofh.lib.audio.ISoundSource;
import cofh.lib.audio.SoundTile;

/**
 * Runs on the client, applying the synced facing/side-cache/active state and repainting the
 * block on the client's own World - which World#markBlockForUpdate actually reaches from here
 * (unlike on the server, see TileAdvancedPulverizer#syncRenderState). Plain field writes + a
 * render-flag call, same as CoFH's own tile-sync packets do in this same version - no
 * main-thread scheduling needed for that.
 *
 * This is also the ONLY place the 3 processing machines' ambient "working" sound gets started
 * (see {@link #playMachineSoundOnStart}) - deliberately kept entirely out of the tile entity
 * classes themselves. Real Thermal Expansion's own TileRSControl directly {@code implements
 * ISoundSource} (a client-only type, since its one method returns {@code net.minecraft.client.
 * audio.ISound}), which only avoids crashing a dedicated server because CoFH's own ASM
 * transformer strips that interface back off server-side via an {@code @Strippable} annotation -
 * a CoFH-internal mechanism this addon has no access to. This message handler, by contrast, is
 * already registered {@code Side.CLIENT}-only (see PacketHandler#init), so FML never even loads
 * this class on a dedicated server in the first place - the same already-proven-safe pattern
 * this file already relied on for referencing {@code Minecraft.getMinecraft()} directly.
 */
public class MessageTileRenderSyncHandler implements IMessageHandler<MessageTileRenderSync, IMessage> {

    /**
     * FML runs onMessage on the NETWORK IO thread, not the client thread. Everything below touches
     * client-thread-owned state: World#getTileEntity and #markBlockForUpdate, and - worse -
     * SoundHandler#playSound, which mutates the same playingSounds/tickableSounds maps
     * SoundManager#updateAllSounds iterates every client tick. Starting a machine while other
     * sounds were playing could therefore throw a ConcurrentModificationException out of the
     * client's own sound tick. Minecraft#func_152344_a hands the work to the client thread, which
     * runs it at the start of its next tick.
     */
    @Override
    public IMessage onMessage(final MessageTileRenderSync message, MessageContext ctx) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                apply(message);
            }
        });
        return null;
    }

    private static void apply(MessageTileRenderSync message) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) {
            return;
        }
        TileEntity te = world.getTileEntity(message.getX(), message.getY(), message.getZ());
        byte[] sides = message.getSideCache();
        boolean active = message.isActive();

        if (te instanceof TileAdvancedPulverizer) {
            TileAdvancedPulverizer tile = (TileAdvancedPulverizer) te;
            tile.setFacingClient(message.getFacing());
            for (int i = 0; i < sides.length; i++) {
                tile.setSideModeClient(i, sides[i]);
            }
            playMachineSoundOnStart(tile.isActive(), active, message.getX(), message.getY(), message.getZ(), TileAdvancedPulverizer.SOUND_NAME);
            tile.setActiveClient(active);
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
            playMachineSoundOnStart(tile.isActive(), active, message.getX(), message.getY(), message.getZ(), TileAdvancedFurnace.SOUND_NAME);
            tile.setActiveClient(active);
        } else if (te instanceof TileSingularityCell) {
            TileSingularityCell tile = (TileSingularityCell) te;
            for (int i = 0; i < sides.length; i++) {
                tile.setSideModeClient(i, sides[i]);
            }
        } else if (te instanceof TileAdvancedSawmill) {
            TileAdvancedSawmill tile = (TileAdvancedSawmill) te;
            tile.setFacingClient(message.getFacing());
            for (int i = 0; i < sides.length; i++) {
                tile.setSideModeClient(i, sides[i]);
            }
            playMachineSoundOnStart(tile.isActive(), active, message.getX(), message.getY(), message.getZ(), TileAdvancedSawmill.SOUND_NAME);
            tile.setActiveClient(active);
        } else if (te instanceof TileAdvancedCharger) {
            TileAdvancedCharger tile = (TileAdvancedCharger) te;
            tile.setFacingClient(message.getFacing());
            for (int i = 0; i < sides.length; i++) {
                tile.setSideModeClient(i, sides[i]);
            }
            // No sound for the Charger - real TE has none either, see TileAdvancedCharger's own
            // field comment - just keep its idle/active face icon in sync.
            tile.setActiveClient(active);
        } else {
            return;
        }

        world.markBlockForUpdate(message.getX(), message.getY(), message.getZ());
    }

    /**
     * Starts the looping ambient machine sound exactly like real Thermal Expansion's own
     * TileRSControl#handleTilePacket does: only on the false->true rising edge (never
     * re-triggered while already active, and never explicitly stopped either - {@link
     * cofh.lib.audio.SoundTile} fades itself out once {@link ISoundSource#shouldPlaySound()}
     * goes false, the same graceful behavior real TE's own machines have). {@code wasActive}
     * must be read from the tile BEFORE this sync's new value is applied to it - see the call
     * sites above.
     */
    private static void playMachineSoundOnStart(boolean wasActive, boolean nowActive, final int x, final int y, final int z, final String soundName) {
        if (!nowActive || wasActive) {
            return;
        }
        ISoundSource source = new ISoundSource() {
            @Override
            public boolean shouldPlaySound() {
                World world = Minecraft.getMinecraft().theWorld;
                if (world == null) {
                    return false;
                }
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof TileAdvancedPulverizer) {
                    return ((TileAdvancedPulverizer) te).isActive();
                }
                if (te instanceof TileAdvancedFurnace) {
                    return ((TileAdvancedFurnace) te).isActive();
                }
                if (te instanceof TileAdvancedSawmill) {
                    return ((TileAdvancedSawmill) te).isActive();
                }
                return false;
            }

            @Override
            public ISound getSound() {
                return new SoundTile(this, soundName, 1.0F, 1.0F, true, 0, x, y, z);
            }
        };
        Minecraft.getMinecraft().getSoundHandler().playSound(source.getSound());
    }
}
