package omtteam.openmodularturrets.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import omtteam.omlib.util.TrustedPlayer;
import omtteam.openmodularturrets.client.gui.containers.TurretBaseTierOneContainer;
import omtteam.openmodularturrets.reference.OMTNames;
import omtteam.openmodularturrets.tileentity.TurretBase;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Random;

public class TurretBaseTierOneGui extends TurretBaseAbstractGui {
    public TurretBaseTierOneGui(InventoryPlayer inventoryPlayer, TurretBase tileEntity) {
        super(inventoryPlayer, tileEntity, new TurretBaseTierOneContainer(inventoryPlayer, tileEntity));
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void drawGuiContainerForegroundLayer(int param1, int param2) {
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRendererObj;

        fontRenderer.drawString("Ammo", 8, 6, 0);
        fontRenderer.drawString("Inventory", 8, ySize - 97 + 4, 0);
        fontRenderer.drawStringWithShadow("" + base.getyAxisDetect(), 127, 39, 40000);
        fontRenderer.drawString("-Y", 123, 6, 0);

        int k = (this.width - this.xSize) / 2;
        int l = (this.height - this.ySize) / 2;
        if (mouseX > k + 153 && mouseX < k + 153 + 14) {
            if (mouseY > l + 17 && mouseY < l + 17 + 51) {
                ArrayList list = new ArrayList();
                list.add(base.getEnergyStored(EnumFacing.DOWN) + "/" + base.getMaxEnergyStored(
                        EnumFacing.DOWN) + " RF");
                this.drawHoveringText(list, mouseX - k, mouseY - l, fontRenderer);
            }
        }

        ArrayList targetInfo = new ArrayList();

        targetInfo.add("\u00A76Owner: \u00A7f" + base.getOwnerName());
        boolean isCurrentlyOn = base.isActive();
        targetInfo.add("\u00A76Active: " + (isCurrentlyOn ? "\u00A72Yes" : "\u00A7cNo"));
        targetInfo.add("");
        targetInfo.add("\u00A75-Trusted Players-");

        for (TrustedPlayer trusted_player : base.getTrustedPlayers()) {
            targetInfo.add("\u00A7b" + trusted_player.name);
        }

        targetInfo.add("");
        targetInfo.add("\u00A77Attack Mobs: \u00A7b" + base.isAttacksMobs());
        targetInfo.add("\u00A77Attack Neutrals: \u00A7b" + base.isAttacksNeutrals());
        targetInfo.add("\u00A77Attack Players: \u00A7b" + base.isAttacksPlayers());

        this.drawHoveringText(targetInfo, -128, 17, fontRenderer);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float par1, int par2, int par3) {
        ResourceLocation texture = (new ResourceLocation(OMTNames.Textures.turretBaseTierOneGUI));
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.renderEngine.bindTexture(texture);
        int x = (width - xSize) / 2;
        int y = (height - ySize) / 2;
        this.drawTexturedModalRect(x, y, 0, 0, xSize, ySize);
        int expression = (base.getEnergyStored(EnumFacing.DOWN) * 51) / base.getMaxEnergyStored(
                EnumFacing.DOWN);
        drawTexturedModalRect(x + 153, y + 17, 178, 17, 14, 51);

        int next = new Random().nextInt(3);

        if (next == 0) {
            drawTexturedModalRect(x + 153, y + 17 + 51 - expression, 196, 68 - expression, 14, expression);
        }

        if (next == 1) {
            drawTexturedModalRect(x + 153, y + 17 + 51 - expression, 215, 68 - expression, 14, expression);
        }

        if (next == 2) {
            drawTexturedModalRect(x + 153, y + 17 + 51 - expression, 234, 68 - expression, 14, expression);
        }
    }
}