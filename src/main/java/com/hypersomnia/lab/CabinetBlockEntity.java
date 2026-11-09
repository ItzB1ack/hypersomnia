package com.hypersomnia.lab;

import com.hypersomnia.ModBlockEntities;
import com.hypersomnia.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;

/** Содержимое картотечного шкафа: LabTuning.CABINET_ROWS ящиков по 9 ячеек. Открытие выдвигает верхний ящик (CabinetBlock.OPEN). */
public final class CabinetBlockEntity extends BaseContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(LabTuning.CABINET_ROWS * 9, ItemStack.EMPTY);
    /** Шприцы из директивы чертежа уже разложены (для старых миров — ленивое досевание при первом открытии). */
    private boolean syringeSeeded;

    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            sound(SoundEvents.IRON_TRAPDOOR_OPEN);
            setOpen(state, true);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            sound(SoundEvents.IRON_TRAPDOOR_CLOSE);
            setOpen(state, false);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int count, int openCount) {}

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof ChestMenu m && m.getContainer() == CabinetBlockEntity.this;
        }
    };

    public CabinetBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABINET.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.hypersomnia.cabinet");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new ChestMenu(ModMenus.CABINET.get(), id, inventory, this, LabTuning.CABINET_ROWS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putBoolean("syringeSeeded", syringeSeeded);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(LabTuning.CABINET_ROWS * 9, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        syringeSeeded = tag.getBoolean("syringeSeeded");
    }

    public boolean isSyringeSeeded() { return syringeSeeded; }
    public void setSyringeSeeded(boolean value) { syringeSeeded = value; }

    @Override
    public void startOpen(Player player) {
        if (!remove && !player.isSpectator() && level != null) openers.incrementOpeners(player, level, getBlockPos(), getBlockState());
    }

    @Override
    public void stopOpen(Player player) {
        if (!remove && !player.isSpectator() && level != null) openers.decrementOpeners(player, level, getBlockPos(), getBlockState());
    }

    /** Запечатанный шкаф (LabLocks.sealed): воронка снизу не вытягивает содержимое, пока замок закрыт. */
    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return !(level instanceof ServerLevel sl) || !LabLocks.sealed(sl, getBlockPos());
    }

    public void recheckOpen() {
        if (!remove && level != null) openers.recheckOpeners(level, getBlockPos(), getBlockState());
    }

    private void setOpen(BlockState state, boolean open) {
        if (level != null && state.hasProperty(CabinetBlock.OPEN)) level.setBlock(getBlockPos(), state.setValue(CabinetBlock.OPEN, open), 3);
    }

    private void sound(SoundEvent s) {
        if (level != null) level.playSound(null, getBlockPos(), s, SoundSource.BLOCKS, LabTuning.CABINET_SOUND_VOLUME, LabTuning.CABINET_SOUND_PITCH);
    }
}
