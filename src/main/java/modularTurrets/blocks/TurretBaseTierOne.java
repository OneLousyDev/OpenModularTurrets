package modularTurrets.blocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import modularTurrets.ModInfo;
import modularTurrets.ModularTurrets;
import modularTurrets.misc.ConfigHandler;
import modularTurrets.network.SetTurretOwnerMessage;
import modularTurrets.tileentity.turretBase.TurretBase;
import modularTurrets.tileentity.turretBase.TurretBaseTierOneTileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.Random;

public class TurretBaseTierOne extends BlockContainer {

    public final int MaxCharge = ConfigHandler.getBaseTierOneMaxCharge();
    public final int MaxIO = ConfigHandler.getBaseTierOneMaxIo();

    public TurretBaseTierOne() {
        super(Material.rock);
        this.setBlockName(BlockNames.unlocalisedTurretBaseTierOne);
        this.setCreativeTab(ModularTurrets.modularTurretsTab);
        this.setHardness(-1F);
        this.setResistance(20F);
        this.setStepSound(Block.soundTypeStone);
    }

    @Override
    public void registerBlockIcons(IIconRegister p_149651_1_) {
        blockIcon = p_149651_1_.registerIcon(ModInfo.ID.toLowerCase() + ":turretBaseTierOne");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int par2) {
	    return new TurretBaseTierOneTileEntity(this.MaxCharge, this.MaxIO);
    }
    
    @Override
    public void onBlockPlacedBy(World par1World, int par2, int par3, int par4,
	    EntityLivingBase par5EntityLivingBase, ItemStack par6ItemStack) {
        if (par1World.isRemote) {
            SetTurretOwnerMessage message = new SetTurretOwnerMessage(par2, par3, par4, Minecraft.getMinecraft().getSession()
                    .getUsername());

            ModularTurrets.networking.sendToServer(message);
        }
    }
    

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z,
	    EntityPlayer player, int metadata, float what, float these,
	    float are) {
        if (!world.isRemote) {
            TurretBase base = (TurretBase) world.getTileEntity(x, y, z);
            if (player.getDisplayName().equals(base.getOwner())) {
                player.openGui(ModularTurrets.instance, 1, world, x, y, z);
            } else {
                player.addChatMessage(new ChatComponentText("You do not own this turret."));
            }
        }
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block par5, int par6) {
        if (!world.isRemote) {
            dropItems(world, x, y, z);
            super.breakBlock(world, x, y, z, par5, par6);
        }
    }
    
    private void dropItems(World world, int x, int y, int z) {
        if (world.getTileEntity(x, y, z) instanceof TurretBase) {
            TurretBase base = (TurretBase) world.getTileEntity(x, y, z);
            Random rand = new Random();
            for (int i = 0; i < base.getSizeInventory(); i++) {
            ItemStack item = base.getStackInSlot(i);

                if (item != null && item.stackSize > 0) {
                    float rx = rand.nextFloat() * 0.8F + 0.1F;
                    float ry = rand.nextFloat() * 0.8F + 0.1F;
                    float rz = rand.nextFloat() * 0.8F + 0.1F;

                    EntityItem entityItem = new EntityItem(world, x + rx, y
                        + ry, z + rz, new ItemStack(item.getItem(),
                        item.stackSize, item.getItemDamage()));

                    if (item.hasTagCompound()) {
                    entityItem.getEntityItem().setTagCompound(
                        (NBTTagCompound) item.getTagCompound().copy());
                    }

                    float factor = 0.05F;
                    entityItem.motionX = rand.nextGaussian() * factor;
                    entityItem.motionY = rand.nextGaussian() * factor + 0.2F;
                    entityItem.motionZ = rand.nextGaussian() * factor;
                    world.spawnEntityInWorld(entityItem);
                    item.stackSize = 0;
                }
            }
        }
    }

}
