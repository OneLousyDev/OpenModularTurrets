package openmodularturrets.tileentity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;

/**
 * Created by Keridos on 05/12/2015.
 * This Class
 */
public abstract class TileEntityContainer extends TileEntityOMT implements ISidedInventory {
    protected ItemStack[] inv;

    @Override
    public ItemStack decrStackSize(int slot, int amt) {
        ItemStack stack = getStackInSlot(slot);

        if (stack != null) {
            if (stack.stackSize <= amt) {
                setInventorySlotContents(slot, null);
            } else {
                stack = stack.splitStack(amt);
                if (stack.stackSize == 0) {
                    setInventorySlotContents(slot, null);
                }
            }
        }
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        inv[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
    }

    @Override
    public int getSizeInventory() {
        return inv.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inv[slot];
    }


    @Override
    public int getInventoryStackLimit() {
        return 64;
    }


    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(pos) == this && player.getDistanceSq(this.pos.getX()  + 0.5,
                this.pos.getY() + 0.5,
                this.pos.getZ() + 0.5) < 64;
    }

    @Override
    public boolean isItemValidForSlot(int p_94041_1_, ItemStack p_94041_2_) {
        return false;
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {

    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public ItemStack removeStackFromSlot(int slot) {
        ItemStack itemstack = getStackInSlot(slot);
        setInventorySlotContents(slot, null);
        return itemstack;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public IChatComponent getDisplayName() {
        return null;
    }

    @Override
    public void openInventory(EntityPlayer player) {

    }

    @Override
    public void closeInventory(EntityPlayer player) {

    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public void clear() {

    }

}
