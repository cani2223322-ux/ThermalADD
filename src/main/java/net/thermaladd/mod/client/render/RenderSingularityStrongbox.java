package net.thermaladd.mod.client.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import cofh.core.render.RenderUtils;
import cofh.thermalexpansion.render.model.ModelStrongbox;
import net.thermaladd.mod.ThermalADD;
import net.thermaladd.mod.tileentity.TileSingularityStrongbox;

/**
 * Real TE's own strongbox model ({@link ModelStrongbox}) with this mod's recoloured texture, drawn
 * exactly the way TE's RenderStrongbox does - upside-down model space, turned to the facing, the
 * lid swung by the tile's eased angle - as a tile renderer in the world and as an item renderer
 * everywhere else. The knob is TE's "public" one, since the box has no access control.
 */
public class RenderSingularityStrongbox extends TileEntitySpecialRenderer implements IItemRenderer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ThermalADD.MODID, "textures/blocks/strongbox/SingularityStrongbox.png");
    private static final int KNOB_PUBLIC = 0;

    private final ModelStrongbox model = new ModelStrongbox();

    private void render(int facing, double x, double y, double z) {
        bindTexture(TEXTURE);
        GL11.glPushMatrix();
        GL11.glTranslated(x, y + 1.0, z + 1.0);
        GL11.glScalef(1.0F, -1.0F, -1.0F);
        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
        GL11.glRotatef(RenderUtils.facingAngle[facing], 0.0F, 1.0F, 0.0F);
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        model.render(KNOB_PUBLIC);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
    }

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileSingularityStrongbox)) {
            return;
        }
        TileSingularityStrongbox box = (TileSingularityStrongbox) te;
        model.boxLid.rotateAngleX = (float) box.getRadianLidAngle(partialTicks);
        int facing = box.getFacing();
        render(facing >= 2 && facing <= 5 ? facing : 3, x, y, z);
    }

    // ---------------------------------------------------------------- item

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return true;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return true;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        double offset = type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON ? 0.0 : -0.5;
        model.boxLid.rotateAngleX = 0.0F;
        render(5, offset, offset, offset);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
    }

    /** TileEntitySpecialRenderer#bindTexture needs a dispatcher, which the item path does not have. */
    @Override
    protected void bindTexture(ResourceLocation location) {
        net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(location);
    }
}
