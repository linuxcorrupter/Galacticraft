/*
 * Copyright (c) 2019-2021 HRZN LTD
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.hrznstudio.galacticraft.block.entity;

import alexiil.mc.lib.attributes.Simulation;
import alexiil.mc.lib.attributes.fluid.FluidAttributes;
import alexiil.mc.lib.attributes.fluid.FluidExtractable;
import alexiil.mc.lib.attributes.fluid.FluidVolumeUtil;
import alexiil.mc.lib.attributes.fluid.amount.FluidAmount;
import alexiil.mc.lib.attributes.fluid.filter.FluidFilter;
import alexiil.mc.lib.attributes.fluid.impl.SimpleFixedFluidInv;
import com.google.common.collect.ImmutableList;
import com.hrznstudio.galacticraft.Constants;
import com.hrznstudio.galacticraft.api.block.SideOption;
import com.hrznstudio.galacticraft.api.block.entity.ConfigurableMachineBlockEntity;
import com.hrznstudio.galacticraft.block.GalacticraftBlocks;
import com.hrznstudio.galacticraft.block.special.rocketlaunchpad.RocketLaunchPadBlock;
import com.hrznstudio.galacticraft.block.special.rocketlaunchpad.RocketLaunchPadBlockEntity;
import com.hrznstudio.galacticraft.entity.GalacticraftBlockEntities;
import com.hrznstudio.galacticraft.entity.RocketEntity;
import com.hrznstudio.galacticraft.tag.GalacticraftTags;
import com.hrznstudio.galacticraft.util.EnergyUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * @author <a href="https://github.com/StellarHorizons">StellarHorizons</a>
 */
public class FuelLoaderBlockEntity extends ConfigurableMachineBlockEntity {
    private static final int CHARGE_SLOT = 0;
    private static final int FUEL_INPUT_SLOT = 1;
    private BlockPos connectionPos = null;
    private Direction check = null;

    public FuelLoaderBlockEntity() {
        super(GalacticraftBlockEntities.FUEL_LOADER_TYPE);
    }

    @Nullable
    public BlockPos getConnectionPos() {
        return connectionPos;
    }

    @Override
    public int getInventorySize() {
        return 2;
    }

    @Override
    public int getFluidTankSize() {
        return 1;
    }

    @Override
    public List<SideOption> validSideOptions() {
        return ImmutableList.of(SideOption.DEFAULT, SideOption.POWER_INPUT, SideOption.FLUID_INPUT);
    }

    @Override
    protected MachineStatus getStatusById(int index) {
        return Status.values()[index];
    }

    @Override
    public boolean canPipeInsertFluid(int tank) {
        return true;
    }

    @Override
    public FluidFilter getFilterForTank(int tank) {
        return key -> GalacticraftTags.FUEL.contains(key.getRawFluid());
    }

    @Override
    public Predicate<ItemStack> getFilterForSlot(int slot) {
        if (slot == 0) {
            return EnergyUtils::isEnergyExtractable;
        } else if (slot == 1) {
            return stack -> FluidAttributes.EXTRACTABLE.getFirstOrNull(stack) != null;
        }
        return Constants.Misc.alwaysFalse();
    }

    @Override
    public boolean canInsertEnergy() {
        return true;
    }

    @Override
    public void updateComponents() {
        super.updateComponents();
        this.attemptChargeFromStack(CHARGE_SLOT);
    }

    @Override
    public @NotNull MachineStatus updateStatus() {
        if (!this.hasEnergyToWork()) return Status.NOT_ENOUGH_ENERGY;
        if (this.getFluidTank().extractFluid(0, key -> GalacticraftTags.FUEL.contains(key.getRawFluid()), FluidVolumeUtil.EMPTY, FluidAmount.ONE, Simulation.SIMULATE).isEmpty()) return Status.NOT_ENOUGH_FUEL;
        if (this.connectionPos == null) return Status.NO_ROCKET;
        BlockEntity be = this.world.getBlockEntity(connectionPos);
        if (be instanceof RocketLaunchPadBlockEntity) {
            if (!((RocketLaunchPadBlockEntity) be).hasRocket()) return Status.NO_ROCKET;
            Entity e = world.getEntityById(((RocketLaunchPadBlockEntity) be).getRocketEntityId());
            if (!(e instanceof RocketEntity)) return Status.NO_ROCKET;
            if (((RocketEntity) e).getTank().getInvFluid(0).getAmount_F().compareTo(((RocketEntity) e).getTank().getMaxAmount_F(0)) >= 0) return Status.ROCKET_IS_FULL;
        } else {
            return Status.NO_ROCKET;
        }

        return Status.LOADING;
    }

    @Override
    public void tickWork() {
        if (check != null) {
            BlockPos launchPad = this.pos.offset(check);
            if (world.getBlockState(launchPad).getBlock() == GalacticraftBlocks.ROCKET_LAUNCH_PAD) {
                launchPad = launchPad.add(RocketLaunchPadBlock.partToCenterPos(world.getBlockState(launchPad).get(RocketLaunchPadBlock.PART)));
                if (world.getBlockState(launchPad).getBlock() instanceof RocketLaunchPadBlock
                        && world.getBlockState(launchPad).get(RocketLaunchPadBlock.PART) == RocketLaunchPadBlock.Part.CENTER
                        && world.getBlockEntity(launchPad) instanceof RocketLaunchPadBlockEntity) {
                    connectionPos = launchPad;
                }
            }
            check = null;
        }

        if (!this.isTankFull(0)) {
            FluidExtractable extractable = FluidAttributes.EXTRACTABLE.getFirstOrNull(this.getInventory().getSlot(FUEL_INPUT_SLOT));
            if (extractable != null) {
                if (!extractable.attemptExtraction(key -> GalacticraftTags.FUEL.contains(key.getRawFluid()), FluidAmount.of(1, 20), Simulation.ACTION).isEmpty()) {
                    this.getFluidTank().insertFluid(0, extractable.extract(key -> GalacticraftTags.FUEL.contains(key.getRawFluid()), FluidAmount.of(1, 20)), Simulation.ACTION);
                }
            }
        }

        if (this.getStatus().getType().isActive()) {
            SimpleFixedFluidInv inv = ((RocketEntity) this.world.getEntityById(((RocketLaunchPadBlockEntity) world.getBlockEntity(connectionPos)).getRocketEntityId())).getTank();
            this.getFluidTank().insertFluid(0, inv.insertFluid(0, this.getFluidTank().extractFluid(0, key -> GalacticraftTags.FUEL.contains(key.getRawFluid()), FluidVolumeUtil.EMPTY, FluidAmount.of(1, 50), Simulation.ACTION), Simulation.ACTION), Simulation.ACTION);
        }
    }

    @Override
    public CompoundTag toTag(CompoundTag tag) {
        if (connectionPos != null) {
            tag.putBoolean("has_connection" , true);
            tag.putLong("connection_pos", connectionPos.asLong());
        }
        return super.toTag(tag);
    }

    @Override
    public void fromTag(BlockState state, CompoundTag tag) {
        super.fromTag(state, tag);
        if (tag.getBoolean("has_connection")) {
            connectionPos = BlockPos.fromLong(tag.getLong("connection_pos"));
        }
    }

    public void updateConnections(Direction direction) {
        this.check = direction;
    }

    @Environment(EnvType.CLIENT)
    public void setConnectionPos(BlockPos connectionPos) {
        this.connectionPos = connectionPos;
    }

    /**
     * @author <a href="https://github.com/StellarHorizons">StellarHorizons</a>
     */
    private enum Status implements MachineStatus {
        /**
         * The fuel loader is loading fuel into the rocket.
         */
        LOADING(new TranslatableText("ui.galacticraft-rewoven.machinestatus.loading"), Formatting.GREEN, StatusType.WORKING),

        /**
         * The fuel loader has enough fuel to load but not enough energy.
         */
        NOT_ENOUGH_ENERGY(new TranslatableText("ui.galacticraft-rewoven.machinestatus.not_enough_energy"), Formatting.RED, StatusType.MISSING_ENERGY),

        /**
         * The fuel loader doesn't have any fuel.
         */
        NOT_ENOUGH_FUEL(new TranslatableText("ui.galacticraft-rewoven.machinestatus.not_enough_fuel"), Formatting.GOLD, StatusType.MISSING_FLUIDS),

        /**
         * The fuel loader doesn't have a rocket
         */
        NO_ROCKET(new TranslatableText("ui.galacticraft-rewoven.machinestatus.no_rocket"), Formatting.RED, StatusType.MISSING_RESOURCE),

        /**
         * The sun is not visible.
         */
        ROCKET_IS_FULL(new TranslatableText("ui.galacticraft-rewoven.machinestatus.rocket_is_full"), Formatting.GOLD, StatusType.OUTPUT_FULL);

        private final Text name;
        private final StatusType type;

        Status(TranslatableText name, Formatting color, StatusType type) {
            this.type = type;
            this.name = name.setStyle(Style.EMPTY.withColor(color));
        }

        public static Status get(int index) {
            if (index < 0) index = 0;
            return Status.values()[index % Status.values().length];
        }

        @Override
        public @NotNull Text getName() {
            return name;
        }

        @Override
        public @NotNull StatusType getType() {
            return type;
        }

        @Override
        public int getIndex() {
            return ordinal();
        }
    }
}