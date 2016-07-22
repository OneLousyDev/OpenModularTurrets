package openmodularturrets.tileentity.turretbase;

import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyReceiver;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergyEmitter;
import ic2.api.energy.tile.IEnergySink;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.Packet;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import openmodularturrets.compatability.ModCompatibility;
import openmodularturrets.handler.ConfigHandler;
import openmodularturrets.handler.NetworkingHandler;
import openmodularturrets.items.blocks.ItemBlockTurretBase;
import openmodularturrets.network.messages.MessageTurretBase;
import openmodularturrets.tileentity.TileEntityContainer;
import openmodularturrets.util.MathUtil;
import openmodularturrets.util.TurretHeadUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static openmodularturrets.util.PlayerUtil.*;

@Optional.InterfaceList({
        @Optional.Interface(iface = "dan200.computercraft.api.peripheral.IPeripheral", modid = "ComputerCraft"),
        @Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers"),
        @Optional.Interface(iface = "thaumcraft.api.aspects.IAspectContainer", modid = "Thaumcraft"),
        @Optional.Interface(iface = "thaumcraft.api.aspects.IEssentiaTransport", modid = "Thaumcraft"),
        @Optional.Interface(iface = "ic2.api.energy.tile.IEnergySink", modid = "IC2")})

public class TurretBase extends TileEntityContainer implements IEnergyReceiver, SimpleComponent, IPeripheral, IEnergySink, ITickable {
    public int trustedPlayerIndex = 0;
    public ItemStack camoStack;

    //For concealment
    public boolean shouldConcealTurrets;

    //For multiTargeting
    private boolean multiTargeting = false;

    private EnergyStorage storage = new EnergyStorage(10, 10);;
    private int yAxisDetect;
    private boolean attacksMobs;
    private boolean attacksNeutrals;
    private boolean attacksPlayers;
    private String owner = "";
    private String ownerName = "";
    private List<TrustedPlayer> trustedPlayers;
    private int ticks;
    private int tier;
    private boolean active;
    private boolean inverted;
    private boolean redstone;
    private boolean checkRedstone = false;
    private boolean computerAccessible = false;
    private boolean dropBase = false;
    //private float amountOfPotentia = 0F;
    //private final float maxAmountOfPotentia = ConfigHandler.getPotentiaAddonCapacity();
    private ArrayList<IComputerAccess> comp;
    private double storageEU;
    private boolean wasAddedToEnergyNet = false;

    public TurretBase() {
        super();
        this.trustedPlayers = new ArrayList<>();
        this.inv = new ItemStack[13];
    }

    public TurretBase(int MaxEnergyStorage, int MaxIO, int tier) {
        super();
        this.yAxisDetect = 2;
        this.storage = new EnergyStorage(MaxEnergyStorage, MaxIO);
        this.attacksMobs = true;
        this.attacksNeutrals = true;
        this.attacksPlayers = false;
        this.trustedPlayers = new ArrayList<>();
        this.inv = new ItemStack[tier == 5 ? 13 : tier == 4 ? 12 : tier == 3 ? 12 : tier == 2 ? 12 : 9];
        this.inverted = true;
        this.active = true;
        this.tier = tier;
    }

    @Override
    public String getName() {
        return super.getName().concat(ItemBlockTurretBase.subNames[tier-1]);
    }

    private static void updateRedstoneReactor(TurretBase base) {
        if (!TurretHeadUtil.hasRedstoneReactor(base)) {
            return;
        }

        if (ConfigHandler.getRedstoneReactorAddonGen() < (base.getMaxEnergyStored(
                EnumFacing.DOWN) - base.getEnergyStored(EnumFacing.DOWN))) {

            //Prioritise redstone blocks
            ItemStack redstoneBlock = TurretHeadUtil.useSpecificItemStackBlockFromBase(base, new ItemStack(
                    Blocks.redstone_block));

            if (redstoneBlock == null) {
                redstoneBlock = TurretHeadUtil.getSpecificItemFromInvExpanders(base.getWorld(),
                        new ItemStack(Blocks.redstone_block),
                        base);
            }

            if (redstoneBlock != null && ConfigHandler.getRedstoneReactorAddonGen() * 9 < (base.getMaxEnergyStored(
                    EnumFacing.DOWN) - base.getEnergyStored(EnumFacing.DOWN))) {
                base.storage.modifyEnergyStored(ConfigHandler.getRedstoneReactorAddonGen() * 9);
                return;
            }

            ItemStack redstone = TurretHeadUtil.useSpecificItemStackItemFromBase(base, Items.redstone);

            if (redstone == null) {
                redstone = TurretHeadUtil.getSpecificItemFromInvExpanders(base.getWorld(),
                        new ItemStack(Items.redstone), base);
            }

            if (redstone != null) {
                base.storage.modifyEnergyStored(ConfigHandler.getRedstoneReactorAddonGen());
            }
        }
    }

    @Optional.Method(modid = "IC2")
    @Override
    public double injectEnergy(EnumFacing facing, double v, double v1) {
        storageEU += v;
        return 0.0D;
    }

    @Optional.Method(modid = "IC2")
    @Override
    public int getSinkTier() {
        return 4;
    }

    @Optional.Method(modid = "IC2")
    @Override
    public double getDemandedEnergy() {
        return Math.max(4000D - storageEU, 0.0D);
    }

    @Optional.Method(modid = "IC2")
    @Override
    public boolean acceptsEnergyFrom(IEnergyEmitter tileEntity, EnumFacing facing) {
        return true;
    }

    @Optional.Method(modid = "IC2")
    private void addToIc2EnergyNetwork() {
        if (!worldObj.isRemote) {
            EnergyTileLoadEvent event = new EnergyTileLoadEvent(this);
            MinecraftForge.EVENT_BUS.post(event);
        }
    }

    @Optional.Method(modid = "IC2")
    @Override
    public void invalidate() {
        super.invalidate();
        if (!worldObj.isRemote) {
            EnergyTileUnloadEvent event = new EnergyTileUnloadEvent(this);
            MinecraftForge.EVENT_BUS.post(event);
        }
    }

    private int getMaxEnergyStorageWithExtenders() {
        int tier = getBaseTier();
        switch (tier) {
            case 1:
                return ConfigHandler.getBaseTierOneMaxCharge() + TurretHeadUtil.getPowerExpanderTotalExtraCapacity(
                        this.worldObj, this.pos);
            case 2:
                return ConfigHandler.getBaseTierTwoMaxCharge() + TurretHeadUtil.getPowerExpanderTotalExtraCapacity(
                        this.worldObj, this.pos);
            case 3:
                return ConfigHandler.getBaseTierThreeMaxCharge() + TurretHeadUtil.getPowerExpanderTotalExtraCapacity(
                        this.worldObj, this.pos);
            case 4:
                return ConfigHandler.getBaseTierFourMaxCharge() + TurretHeadUtil.getPowerExpanderTotalExtraCapacity(
                        this.worldObj, this.pos);
            case 5:
                return ConfigHandler.getBaseTierFiveMaxCharge() + TurretHeadUtil.getPowerExpanderTotalExtraCapacity(
                        this.worldObj, this.pos);
        }
        return 0;
    }

    public boolean addTrustedPlayer(String name) {
        TrustedPlayer trustedPlayer = new TrustedPlayer(name);
        trustedPlayer.uuid = getPlayerUUID(name);
        if (trustedPlayer.uuid != null) {
            for (TrustedPlayer player : trustedPlayers) {
                if (player.getName().toLowerCase().equals(name.toLowerCase()) || trustedPlayer.uuid.toString().equals(
                        owner)) {
                    return true;
                }
            }
            trustedPlayers.add(trustedPlayer);
            worldObj.markBlockForUpdate(this.pos);
            return true;
        }
        return false;
    }

    public boolean removeTrustedPlayer(String name) {
        for (TrustedPlayer player : trustedPlayers) {
            if (player.getName().equals(name)) {
                trustedPlayers.remove(player);
                worldObj.markBlockForUpdate(this.pos);
                return true;
            }
        }
        return false;
    }

    public List<TrustedPlayer> getTrustedPlayers() {
        return trustedPlayers;
    }

    public TrustedPlayer getTrustedPlayer(String name) {
        for (TrustedPlayer trustedPlayer : trustedPlayers) {
            if (trustedPlayer.name.equals(name)) {
                return trustedPlayer;
            }
        }
        return null;
    }

    public TrustedPlayer getTrustedPlayer(UUID uuid) {
        for (TrustedPlayer trustedPlayer : trustedPlayers) {
            if (trustedPlayer.uuid.equals(uuid)) {
                return trustedPlayer;
            }
        }
        return null;
    }

    public void setTrustedPlayers(List<TrustedPlayer> list) {
        this.trustedPlayers = list;
    }

    private NBTTagList getTrustedPlayersAsNBT() {
        NBTTagList nbt = new NBTTagList();
        for (TrustedPlayer trustedPlayer : trustedPlayers) {
            NBTTagCompound nbtPlayer = new NBTTagCompound();
            nbtPlayer.setString("name", trustedPlayer.name);
            nbtPlayer.setBoolean("canOpenGUI", trustedPlayer.canOpenGUI);
            nbtPlayer.setBoolean("canChangeTargeting", trustedPlayer.canChangeTargeting);
            nbtPlayer.setBoolean("admin", trustedPlayer.admin);
            if (trustedPlayer.uuid != null) {
                nbtPlayer.setString("UUID", trustedPlayer.uuid.toString());
            } else if (getPlayerUUID(trustedPlayer.name) != null) {
                nbtPlayer.setString("UUID", getPlayerUUID(trustedPlayer.name).toString());
            }
            nbt.appendTag(nbtPlayer);
        }
        return nbt;
    }

    private void buildTrustedPlayersFromNBT(NBTTagList nbt) {
        trustedPlayers.clear();
        for (int i = 0; i < nbt.tagCount(); i++) {
            if (!nbt.getCompoundTagAt(i).getString("name").equals("")) {
                NBTTagCompound nbtPlayer = nbt.getCompoundTagAt(i);
                TrustedPlayer trustedPlayer = new TrustedPlayer(nbtPlayer.getString("name"));
                trustedPlayer.canOpenGUI = nbtPlayer.getBoolean("canOpenGUI");
                trustedPlayer.canChangeTargeting = nbtPlayer.getBoolean("canChangeTargeting");
                trustedPlayer.admin = nbtPlayer.getBoolean("admin");
                if (nbtPlayer.hasKey("UUID")) {
                    trustedPlayer.uuid = getPlayerUIDUnstable(nbtPlayer.getString("UUID"));
                } else {
                    trustedPlayer.uuid = getPlayerUUID(trustedPlayer.name);
                }
                if (trustedPlayer.uuid != null) {
                    trustedPlayers.add(trustedPlayer);
                }
            } else if (nbt.getCompoundTagAt(i).getString("name").equals("")) {
                TrustedPlayer trustedPlayer = new TrustedPlayer(nbt.getStringTagAt(i));
                Logger.getGlobal().info("found legacy trusted Player: " + nbt.getStringTagAt(i));
                trustedPlayer.uuid = getPlayerUUID(trustedPlayer.name);
                if (trustedPlayer.uuid != null) {
                    trustedPlayers.add(trustedPlayer);
                }
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound par1) {
        super.writeToNBT(par1);

        par1.setInteger("maxStorage", this.storage.getMaxEnergyStored());
        par1.setInteger("energyStored", this.getEnergyStored(EnumFacing.DOWN));
        //par1.setFloat("amountOfPotentia", amountOfPotentia);
        par1.setInteger("maxIO", this.storage.getMaxReceive());
        par1.setInteger("yAxisDetect", this.yAxisDetect);
        par1.setBoolean("attacksMobs", attacksMobs);
        par1.setBoolean("attacksNeutrals", attacksNeutrals);
        par1.setBoolean("attacksPlayers", attacksPlayers);
        par1.setString("owner", owner);
        if (ownerName.isEmpty() && getPlayerNameFromUUID(owner) != null) {
            ownerName = getPlayerNameFromUUID(owner);
        }
        par1.setString("ownerName", ownerName);
        par1.setTag("trustedPlayers", getTrustedPlayersAsNBT());
        par1.setBoolean("active", active);
        par1.setBoolean("inverted", inverted);
        par1.setBoolean("redstone", redstone);
        par1.setBoolean("computerAccessible", computerAccessible);
        par1.setBoolean("shouldConcealTurrets", shouldConcealTurrets);
        par1.setBoolean("multiTargeting", multiTargeting);
        par1.setDouble("storageEU", storageEU);
        par1.setInteger("tier", tier);

        if (camoStack != null) {
            NBTTagCompound tag2 = new NBTTagCompound();
            camoStack.writeToNBT(tag2);
            par1.setTag("CamoStack", tag2);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound par1) {
        this.storage.setCapacity(par1.getInteger("maxStorage"));
        this.storage.setEnergyStored(par1.getInteger("energyStored"));
        this.storage.setMaxReceive(par1.getInteger("maxIO"));
        //this.amountOfPotentia = par1.getFloat("amountOfPotentia");
        this.yAxisDetect = par1.getInteger("yAxisDetect");
        this.attacksMobs = par1.getBoolean("attacksMobs");
        this.attacksNeutrals = par1.getBoolean("attacksNeutrals");
        this.attacksPlayers = par1.getBoolean("attacksPlayers");
        this.shouldConcealTurrets = par1.getBoolean("shouldConcealTurrets");
        this.multiTargeting = par1.getBoolean("multiTargeting");
        if (getPlayerUIDUnstable(par1.getString("owner")) != null) {
            this.owner = getPlayerUIDUnstable(par1.getString("owner")).toString();
        } else if (getPlayerUUID(par1.getString("owner")) != null) {
            this.owner = getPlayerUUID(par1.getString("owner")).toString();
        } else {
            Logger.getGlobal().info("Found non existent owner: " + par1.getString(
                    "owner") + "at coordinates: " + this.pos.getX() + "," + this.pos.getY() + "," + this.pos.getZ() + ". Dropping Turretbase");
            dropBase = true;
            return;
        }
        if (par1.hasKey("ownerName")) {
            this.ownerName = par1.getString("ownerName");
        }
        buildTrustedPlayersFromNBT(par1.getTagList("trustedPlayers", 10));
        if (trustedPlayers.size() == 0) {
            buildTrustedPlayersFromNBT(par1.getTagList("trustedPlayers", 8));
        }
        if (par1.hasKey("active")) {
            this.active = par1.getBoolean("active");
        } else {
            active = true;
        }
        if (par1.hasKey("inverted")) {
            this.inverted = par1.getBoolean("inverted");
        } else {
            inverted = true;
        }
        if (par1.hasKey("redstone")) {
            this.redstone = par1.getBoolean("redstone");
        } else {
            checkRedstone = true;
        }
        if (par1.hasKey("computerAccessible")) {
            this.computerAccessible = par1.getBoolean("computerAccessible");
        } else {
            computerAccessible = false;
        }
        if (par1.hasKey("storageEU")) {
            this.storageEU = par1.getDouble("storageEU");
        } else {
            storageEU = 0;
        }
        if (par1.hasKey("tier")) {
            this.tier = par1.getInteger("tier");
        } else {
            Logger.getGlobal().info("Found bugged turretBase (no tier) at coordinates: " + this.pos.getX() + "," + this.pos.getY() + "," + this.pos.getZ() + ". Dropping Turretbase");
            dropBase = true;
        }
        this.inv = new ItemStack[tier == 5 ? 13 : tier == 4 ? 12 : tier == 3 ? 12 : tier == 2 ? 12 : 9];

        NBTTagCompound tag2 = par1.getCompoundTag("CamoStack");
        if (tag2 != null) {
            camoStack = ItemStack.loadItemStackFromNBT(tag2);
        }
        super.readFromNBT(par1);
    }

    /*
    @Optional.Method(modid = "Thaumcraft")
    private IEssentiaTransport getConnectableTileWithoutOrientation() {
        if (worldObj.getTileEntity(this.xCoord + 1, this.yCoord, this.zCoord) instanceof IEssentiaTransport) {
            return (IEssentiaTransport) worldObj.getTileEntity(this.xCoord + 1, this.yCoord, this.zCoord);
        }

        if (worldObj.getTileEntity(this.xCoord - 1, this.yCoord, this.zCoord) instanceof IEssentiaTransport) {
            return (IEssentiaTransport) worldObj.getTileEntity(this.xCoord - 1, this.yCoord, this.zCoord);
        }

        if (worldObj.getTileEntity(this.xCoord, this.yCoord + 1, this.zCoord) instanceof IEssentiaTransport) {
            return (IEssentiaTransport) worldObj.getTileEntity(this.xCoord, this.yCoord + 1, this.zCoord);
        }

        if (worldObj.getTileEntity(this.xCoord, this.yCoord - 1, this.zCoord) instanceof IEssentiaTransport) {
            return (IEssentiaTransport) worldObj.getTileEntity(this.xCoord, this.yCoord - 1, this.zCoord);
        }

        if (worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord + 1) instanceof IEssentiaTransport) {
            return (IEssentiaTransport) worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord + 1);
        }

        if (worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord - 1) instanceof IEssentiaTransport) {
            return (IEssentiaTransport) worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord - 1);
        }
        return null;
    }

    @Optional.Method(modid = "Thaumcraft")
    private int drawEssentia() {
        IEssentiaTransport ic = getConnectableTileWithoutOrientation();
        if (ic != null) {
            if (ic.takeEssentia(Aspect.ENERGY, 1, ForgeDirection.UP) == 1) {
                return 1;
            }
        }
        return 0;
    }*/

    @Override
    public void update() {
        if (!worldObj.isRemote && dropBase) {
            worldObj.destroyBlock(this.pos, true);
            return;
        } else if (ModCompatibility.IC2Loaded && ConfigHandler.EUSupport && !wasAddedToEnergyNet && !worldObj.isRemote) {
            addToIc2EnergyNetwork();
            wasAddedToEnergyNet = true;
        }
        if (!worldObj.isRemote && ticks % 5 == 0) {

            //Concealment
            this.shouldConcealTurrets = TurretHeadUtil.hasConcealmentAddon(this);

            //Extenders
            this.storage.setCapacity(getMaxEnergyStorageWithExtenders());

            //Thaumcraft
            /*if (ModCompatibility.ThaumcraftLoaded && TurretHeadUtil.hasPotentiaUpgradeAddon(this)) {
                if (amountOfPotentia > 0.05F && !(storage.getMaxEnergyStored() - storage.getEnergyStored() == 0)) {
                    if (VisNetHandler.drainVis(worldObj, xCoord, yCoord, zCoord, Aspect.ORDER, 5) == 5) {
                        this.amountOfPotentia = this.amountOfPotentia - 0.05F;
                        this.storage.modifyEnergyStored(Math.round(ConfigHandler.getPotentiaToRFRatio() * 5));
                    } else {
                        this.amountOfPotentia = this.amountOfPotentia - 0.05F;
                        this.storage.modifyEnergyStored(Math.round(ConfigHandler.getPotentiaToRFRatio() / 2));
                    }
                }
            }*/

            if (ModCompatibility.IC2Loaded && ConfigHandler.EUSupport) {
                if (storage.getMaxEnergyStored() != storage.getEnergyStored() && storageEU > 0) {
                    storage.modifyEnergyStored(MathUtil.truncateDoubleToInt(
                            Math.min(storage.getMaxEnergyStored() - storage.getEnergyStored(),
                                    storageEU * ConfigHandler.EUtoRFRatio)));
                    storageEU -= Math.min(
                            (storage.getMaxEnergyStored() - storage.getEnergyStored()) / ConfigHandler.EUtoRFRatio,
                            storageEU * ConfigHandler.EUtoRFRatio);
                }
            }

            //Syncing
            worldObj.markBlockForUpdate(this.pos);

            if (ticks % 20 == 0) {

                //General
                ticks = 0;
                updateRedstoneReactor(this);

                //Thaumcraft
                /*if (ModCompatibility.ThaumcraftLoaded && amountOfPotentia <= maxAmountOfPotentia) {
                    amountOfPotentia = amountOfPotentia + drawEssentia();
                } */

                //Computers
                this.computerAccessible = (ModCompatibility.OpenComputersLoaded || ModCompatibility.ComputercraftLoaded) && TurretHeadUtil.hasSerialPortAddon(
                        this);
            }
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        return NetworkingHandler.INSTANCE.getPacketFrom((IMessage) new MessageTurretBase(this));
    }

    public int getBaseTier() {
        return tier;
    }

    public boolean isAttacksMobs() {
        return attacksMobs;
    }

    public void setAttacksMobs(boolean attacksMobs) {
        this.attacksMobs = attacksMobs;
    }

    public boolean isAttacksNeutrals() {
        return attacksNeutrals;
    }

    public void setAttacksNeutrals(boolean attacksNeutrals) {
        this.attacksNeutrals = attacksNeutrals;
    }

    public boolean isAttacksPlayers() {
        return attacksPlayers;
    }

    public void setAttacksPlayers(boolean attacksPlayers) {
        this.attacksPlayers = attacksPlayers;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String name) {
        ownerName = name;
    }


    public boolean isMultiTargeting() {
        return multiTargeting;
    }

    public void setMultiTargeting(boolean multiTargeting) {
        this.multiTargeting = multiTargeting;
    }

    @Override
    public int receiveEnergy(EnumFacing from, int maxReceive, boolean simulate) {
        return storage.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public int getEnergyStored(EnumFacing from) {
        return storage.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored(EnumFacing from) {
        return storage.getMaxEnergyStored();
    }

    public void setEnergyStored(int energy) {
        storage.setEnergyStored(energy);
    }

    @Override
    public boolean isItemValidForSlot(int i, ItemStack itemstack) {
        return true;
    }

    public int getyAxisDetect() {
        return yAxisDetect;
    }

    public void setyAxisDetect(int yAxisDetect) {
        this.yAxisDetect = yAxisDetect;

        if (this.yAxisDetect > 9) {
            this.yAxisDetect = 9;
        }

        if (this.yAxisDetect < 0) {
            this.yAxisDetect = 0;
        }
    }

    @Override
    public boolean canConnectEnergy(EnumFacing from) {
        return true;
    }

    @Override
    public int[] getSlotsForFace(EnumFacing side) {
        return new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
    }


    @Override
    public boolean canInsertItem(int index, ItemStack itemStackIn, EnumFacing direction) {
        return isItemValidForSlot(index, itemStackIn);
    }

    @Override
    public boolean canExtractItem(int index, ItemStack itemStackIn, EnumFacing direction) {
        return true;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    private boolean getInverted() {
        return this.inverted;
    }

    private void setInverted(boolean inverted) {
        this.inverted = inverted;
        this.active = redstone ^ this.inverted;
        worldObj.markBlockForUpdate(this.pos);
    }

    private boolean getRedstone() {
        return this.redstone;
    }

    public void setRedstone(boolean redstone) {
        this.redstone = redstone;
        this.active = this.redstone ^ inverted;
        worldObj.markBlockForUpdate(this.pos);
    }
    /*
    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean isConnectable(ForgeDirection face) {
        return TurretHeadUtil.hasPotentiaUpgradeAddon(this);
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean canInputFrom(ForgeDirection face) {
        return TurretHeadUtil.hasPotentiaUpgradeAddon(this);
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean canOutputTo(ForgeDirection face) {
        return false;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public void setSuction(Aspect aspect, int amount) {
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public Aspect getSuctionType(ForgeDirection face) {
        return null;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public int takeEssentia(Aspect aspect, int amount, ForgeDirection face) {
        return 0;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public Aspect getEssentiaType(ForgeDirection face) {
        return null;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public int getEssentiaAmount(ForgeDirection face) {
        return 0;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public int getMinimumSuction() {
        return 0;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean renderExtendedTube() {
        return false;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public AspectList getAspects() {
        if (TurretHeadUtil.hasPotentiaUpgradeAddon(this)) {
            return new AspectList().add(Aspect.ENERGY, (int) Math.floor(amountOfPotentia));
        } else {
            return null;
        }
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public void setAspects(AspectList aspects) {
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean doesContainerAccept(Aspect tag) {
        return tag.equals(Aspect.ENERGY);
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public int addToContainer(Aspect tag, int amount) {
        return 0;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public int getSuctionAmount(ForgeDirection face) {
        return 64;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public int addEssentia(Aspect aspect, int amount, ForgeDirection face) {
        return 0;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean takeFromContainer(Aspect tag, int amount) {
        return false;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean takeFromContainer(AspectList ot) {
        return false;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean doesContainerContainAmount(Aspect tag, int amount) {
        return false;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean doesContainerContain(AspectList ot) {
        return false;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public int containerContains(Aspect tag) {
        if (tag.equals(Aspect.ENERGY)) {
            return Math.round(amountOfPotentia);
        }
        return 0;
    } */

    @Optional.Method(modid = "OpenComputers")
    @Override
    public String getComponentName() {
        return "turretBase";
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():string; returns owner of turret base.")
    public Object[] getTier(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{this.getBaseTier()};
        }
        return new Object[]{this.getOwner()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():string; returns owner of turret base.")
    public Object[] getOwner(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.getOwner()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; returns if the turret is currently set to attack hostile mobs.")
    public Object[] isAttacksMobs(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.isAttacksMobs()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean;  sets to attack hostile mobs or not.")
    public Object[] setAttacksMobs(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        this.setAttacksMobs(args.checkBoolean(0));
        worldObj.markBlockForUpdate(this.pos);
        return null;
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; returns if the turret is currently set to attack neutral mobs.")
    public Object[] isAttacksNeutrals(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.isAttacksNeutrals()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; sets to attack neutral mobs or not.")
    public Object[] setAttacksNeutrals(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        this.setAttacksNeutrals(args.checkBoolean(0));
        worldObj.markBlockForUpdate(this.pos);
        return null;
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; returns if the turret is currently set to attack players.")
    public Object[] isAttacksPlayers(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.isAttacksPlayers()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; sets to attack players or not.")
    public Object[] setAttacksPlayers(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        this.setAttacksPlayers(args.checkBoolean(0));
        worldObj.markBlockForUpdate(this.pos);
        return null;
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():table; returns a table of trusted players on this base.")
    public Object[] getTrustedPlayers(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.getTrustedPlayers()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function(name:String, [canOpenGUI:boolean , canChangeTargeting:boolean , " + "admin:boolean]):string; adds Trusted player to Trustlist.")
    public Object[] addTrustedPlayer(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        if (!this.addTrustedPlayer(args.checkString(0))) {
            return new Object[]{"Name not valid!"};
        }
        TrustedPlayer trustedPlayer = this.getTrustedPlayer(args.checkString(0));
        trustedPlayer.canOpenGUI = args.optBoolean(1, false);
        trustedPlayer.canChangeTargeting = args.optBoolean(1, false);
        trustedPlayer.admin = args.optBoolean(1, false);
        trustedPlayer.uuid = getPlayerUUID(args.checkString(0));
        worldObj.markBlockForUpdate(this.pos);
        return null;
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():string; removes Trusted player from Trustlist.")
    public Object[] removeTrustedPlayer(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        this.removeTrustedPlayer(args.checkString(0));
        worldObj.markBlockForUpdate(this.pos);
        return null;
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():int; returns maxiumum energy storage.")
    public Object[] getMaxEnergyStorage(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.storage.getMaxEnergyStored()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():int; returns current energy stored.")
    public Object[] getCurrentEnergyStorage(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.getEnergyStored(EnumFacing.DOWN)};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; returns if the turret is currently active.")
    public Object[] getActive(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.isActive()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; toggles turret redstone inversion state.")
    public Object[] setInverted(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        this.setInverted(args.checkBoolean(0));
        worldObj.markBlockForUpdate(this.pos);
        return null;
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; shows redstone inversion state.")
    public Object[] getInverted(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.getInverted()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; shows redstone state.")
    public Object[] getRedstone(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.getRedstone()};
    }

    @Optional.Method(modid = "ComputerCraft")
    @Override
    public String getType() {
        // peripheral.getType returns whaaaaat?
        return "OMTBase";
    }

    @Optional.Method(modid = "ComputerCraft")
    @Override
    public String[] getMethodNames() {
        // list commands you want..
        return new String[]{commands.getOwner.toString(), commands.attacksPlayers.toString(),
                commands.setAttacksPlayers.toString(), commands.attacksMobs.toString(),
                commands.setAttacksMobs.toString(), commands.attacksNeutrals.toString(),
                commands.setAttacksNeutrals.toString(), commands.getTrustedPlayers.toString(),
                commands.addTrustedPlayer.toString(), commands.removeTrustedPlayer.toString(),
                commands.getActive.toString(), commands.getInverted.toString(),
                commands.getRedstone.toString(), commands.setInverted.toString(),
                commands.getType.toString()};
    }

    @Optional.Method(modid = "ComputerCraft")
    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) throws LuaException, InterruptedException {
        // method is command
        boolean b;
        int i;
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        switch (commands.values()[method]) {
            case getOwner:
                return new Object[]{this.getOwner()};
            case attacksPlayers:
                return new Object[]{this.attacksPlayers};
            case setAttacksPlayers:
                if (!(arguments[0].toString().equals("true") || arguments[0].toString().equals("false"))) {
                    return new Object[]{"wrong arguments"};
                }
                b = (arguments[0].toString().equals("true"));
                this.attacksPlayers = b;
                return new Object[]{true};
            case attacksMobs:
                return new Object[]{this.attacksMobs};
            case setAttacksMobs:
                if (!(arguments[0].toString().equals("true") || arguments[0].toString().equals("false"))) {
                    return new Object[]{"wrong arguments"};
                }
                b = (arguments[0].toString().equals("true"));
                this.attacksMobs = b;
                return new Object[]{true};
            case attacksNeutrals:
                return new Object[]{this.attacksNeutrals};
            case setAttacksNeutrals:
                if (!(arguments[0].toString().equals("true") || arguments[0].toString().equals("false"))) {
                    return new Object[]{"wrong arguments"};
                }
                b = (arguments[0].toString().equals("true"));
                this.attacksNeutrals = b;
                return new Object[]{true};
            case getTrustedPlayers:
                HashMap<String, Integer> result = new HashMap<>();
                if (this.getTrustedPlayers() != null && this.getTrustedPlayers().size() > 0) {
                    for (TrustedPlayer trustedPlayer : this.getTrustedPlayers()) {
                        result.put(trustedPlayer.name,
                                (trustedPlayer.canOpenGUI ? 1 : 0) + (trustedPlayer.canChangeTargeting ? 2 : 0) + (trustedPlayer.admin ? 4 : 0));
                    }
                }
                return new Object[]{result};
            case addTrustedPlayer:
                if (arguments[0].toString().equals("")) {
                    return new Object[]{"wrong arguments"};
                }
                if (!this.addTrustedPlayer(arguments[0].toString())) {
                    return new Object[]{"Name not valid!"};
                }
                if (arguments[1].toString().equals("")) {
                    return new Object[]{"successfully added"};
                }
                for (i = 1; i <= 4; i++) {
                    if (arguments.length > i && !(arguments[i].toString().equals(
                            "true") || arguments[i].toString().equals("false"))) {
                        return new Object[]{"wrong arguments"};
                    }
                }
                TrustedPlayer trustedPlayer = this.getTrustedPlayer(arguments[0].toString());
                trustedPlayer.canOpenGUI = arguments[1].toString().equals("true");
                trustedPlayer.canChangeTargeting = arguments[2].toString().equals("true");
                trustedPlayer.admin = arguments[3].toString().equals("true");
                trustedPlayer.uuid = getPlayerUUID(arguments[0].toString());
                worldObj.markBlockForUpdate(this.pos);
                return new Object[]{"succesfully added player to trust list with parameters"};
            case removeTrustedPlayer:
                if (arguments[0].toString().equals("")) {
                    return new Object[]{"wrong arguments"};
                }
                this.removeTrustedPlayer(arguments[0].toString());
                worldObj.markBlockForUpdate(this.pos);
                return new Object[]{"removed player from trusted list"};
            case getActive:
                return new Object[]{this.active};
            case getInverted:
                return new Object[]{this.inverted};
            case getRedstone:
                return new Object[]{this.redstone};
            case setInverted:
                if (!(arguments[0].toString().equals("true") || arguments[0].toString().equals("false"))) {
                    return new Object[]{"wrong arguments"};
                }
                b = (arguments[0].toString().equals("true"));
                this.setInverted(b);
                worldObj.markBlockForUpdate(this.pos);
                return new Object[]{true};
            case getType:
                return new Object[]{this.getType()};
            default:
                break;
        }
        return new Object[]{false};
    }

    @Optional.Method(modid = "ComputerCraft")
    @Override
    public void attach(IComputerAccess computer) {
        if (comp == null) {
            comp = new ArrayList<IComputerAccess>();
        }
        comp.add(computer);
    }

    @Optional.Method(modid = "ComputerCraft")
    @Override
    public void detach(IComputerAccess computer) {
        if (comp == null) {
            comp = new ArrayList<IComputerAccess>();
        }
        comp.remove(computer);
    }

    @Optional.Method(modid = "ComputerCraft")
    @Override
    public boolean equals(IPeripheral other) {
        return other.getType().equals(getType());
    }

    public enum commands {
        getOwner, attacksPlayers, setAttacksPlayers, attacksMobs, setAttacksMobs, attacksNeutrals, setAttacksNeutrals,
        getTrustedPlayers, addTrustedPlayer, removeTrustedPlayer, getActive, getInverted, getRedstone, setInverted,
        getType
    }
}
