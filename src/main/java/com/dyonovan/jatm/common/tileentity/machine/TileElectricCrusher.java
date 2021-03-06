package com.dyonovan.jatm.common.tileentity.machine;

import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyReceiver;
import com.dyonovan.jatm.common.blocks.BlockMachine;
import com.dyonovan.jatm.common.blocks.IExpellable;
import com.dyonovan.jatm.common.tileentity.BaseMachine;
import com.dyonovan.jatm.common.tileentity.InventoryTile;
import com.dyonovan.jatm.crafting.OreProcessingRegistry;
import com.dyonovan.jatm.handlers.BlockHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.gui.IUpdatePlayerListBox;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;

import java.util.List;

public class TileElectricCrusher extends BaseMachine implements IUpdatePlayerListBox, IEnergyReceiver, IExpellable {

    public int currentProcessTime;
    private ItemStack input, output;
    public EnergyStorage energyRF;

    private static final int RF_TICK = 20;
    public static final int TOTAL_PROCESS_TIME = 150;
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;

    public TileElectricCrusher() {
        energyRF = new EnergyStorage(10000);
        currentProcessTime = 0;
        inventory = new InventoryTile(2);
    }

    @Override
    public void update() {
        if (!this.hasWorldObj()) return;
        World world = this.getWorld();
        if (world.isRemote) return;

        if (currentProcessTime > 0 || canWork()) {
            if (currentProcessTime == 0) {
                input = inventory.getStackInSlot(INPUT_SLOT);
                currentProcessTime = 1;
                BlockMachine.setState(world, pos, BlockHandler.electricCrusherActive);
            }
            if (currentProcessTime > 0 && currentProcessTime < TOTAL_PROCESS_TIME) {
                if (inventory.getStackInSlot(INPUT_SLOT) == null || !inventory.getStackInSlot(INPUT_SLOT).isItemEqual(input)) {
                    doReset();
                    world.markBlockForUpdate(this.pos);
                    return;
                }
                if (energyRF.getEnergyStored() >= RF_TICK ) {
                    energyRF.modifyEnergyStored(-RF_TICK);
                    ++currentProcessTime;
                }
            }
            if (currentProcessTime >= TOTAL_PROCESS_TIME) {
                inventory.modifyStack(INPUT_SLOT, -1);
                if (inventory.getStackInSlot(OUTPUT_SLOT) == null)
                    inventory.setStackInSlot(output, OUTPUT_SLOT);
                else inventory.getStackInSlot(OUTPUT_SLOT).stackSize += output.stackSize;
                doReset();
            }
            world.markBlockForUpdate(this.pos);
        }
    }

    public boolean canWork() {
        if (inventory.getStackInSlot(INPUT_SLOT) == null) return false;

        output = OreProcessingRegistry.getOutput(inventory.getStackInSlot(INPUT_SLOT));

        return output != null && !(inventory.getStackInSlot(OUTPUT_SLOT) != null &&
                (!inventory.getStackInSlot(OUTPUT_SLOT).isItemEqual(output))) &&
                !(inventory.getStackInSlot(OUTPUT_SLOT) != null &&
                inventory.getStackInSlot(OUTPUT_SLOT).stackSize + output.stackSize >
                        inventory.getStackInSlot(OUTPUT_SLOT).getMaxStackSize());
    }

    public void doReset() {
        currentProcessTime = 0;
        input = null;
        output = null;
        BlockMachine.setState(worldObj, pos, BlockHandler.electricCrusher);
    }

    /*******************************************************************************************************************
     ************************************** Energy Functions ***********************************************************
     *******************************************************************************************************************/

    @Override
    public int receiveEnergy(EnumFacing from, int maxReceive, boolean simulate) {
        int actual = energyRF.receiveEnergy(maxReceive, simulate);
        if (actual > 0) this.getWorld().markBlockForUpdate(this.pos);
        return actual;
    }

    @Override
    public int getEnergyStored(EnumFacing from) {
        return energyRF.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored(EnumFacing from) {
        return energyRF.getMaxEnergyStored();
    }

    @Override
    public boolean canConnectEnergy(EnumFacing from) {
        return true;
    }

    @Override
    public void spawnActiveParticles(double x, double y, double z) {
        worldObj.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, x, y + 0.4, z, 0, 0, 0);
        worldObj.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, x, y + 0.4, z, 0, 0, 0);
    }

    /*******************************************************************************************************************
     ************************************** Inventory Functions ********************************************************
     *******************************************************************************************************************/

    @Override
    public int[] getSlotsForFace(EnumFacing side) {
        return new int[] {0,1};
    }

    @Override
    public boolean canInsertItem(int index, ItemStack itemStackIn, EnumFacing direction) {
        return index == 0;
    }

    @Override
    public boolean canExtractItem(int index, ItemStack stack, EnumFacing direction) {
        return index == 1;
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return true;
    }

    @Override
    public int getField(int id) {
        switch (id) {
            case 0:
                return currentProcessTime;
            default:
                return 0;
        }

    }

    @Override
    public void setField(int id, int value) {
        switch (id) {
            case 0:
                currentProcessTime = value;
        }
    }

    @Override
    public int getFieldCount() {
        return 1;
    }

    @Override
    public void clear() {
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            inventory.setStackInSlot(null, i);
        }
    }

    /*******************************************************************************************************************
     **************************************** Tile Functions ***********************************************************
     *******************************************************************************************************************/

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        energyRF.readFromNBT(tag);
        inventory.readFromNBT(tag, this);
        NBTTagList itemsTag = tag.getTagList("Stacks", 10);
        for (int i = 0; i < itemsTag.tagCount(); i++)
        {
            NBTTagCompound nbtTagCompound1 = itemsTag.getCompoundTagAt(i);
            NBTBase nbt = nbtTagCompound1.getTag("Stack");
            int j;
            if ((nbt instanceof NBTTagByte)) {
                j = nbtTagCompound1.getByte("Stack") & 0xFF;
            } else {
                j = nbtTagCompound1.getShort("Stack");
            }
            switch (j) {
                case 0:
                    input = ItemStack.loadItemStackFromNBT(nbtTagCompound1);
                    break;
                case 1:
                    output = ItemStack.loadItemStackFromNBT(nbtTagCompound1);
                    break;
            }
        }
        currentProcessTime = tag.getInteger("CurrentProcessTime");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        energyRF.writeToNBT(tag);
        inventory.writeToNBT(tag);
        NBTTagList nbtTagList = new NBTTagList();
        for (int i = 0; i < 2; i++) {
            NBTTagCompound nbtTagCompound1 = new NBTTagCompound();
            nbtTagCompound1.setShort("Stack", (short)i);
            switch (i)
            {
                case 0:
                    if (input != null)
                    input.writeToNBT(nbtTagCompound1);
                    break;
                case 1:
                    if (output != null)
                    output.writeToNBT(nbtTagCompound1);
                    break;
            }
            nbtTagList.appendTag(nbtTagCompound1);
        }
        tag.setTag("Stacks", nbtTagList);
        tag.setInteger("CurrentProcessTime", currentProcessTime);
    }
}
