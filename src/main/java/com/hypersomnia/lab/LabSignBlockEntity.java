package com.hypersomnia.lab;

import com.hypersomnia.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Строки таблички лаборатории (до LINES), английский текст из чертежа.
 * Перевод: ключ hypersomnia.lab.sign.&lt;key()&gt;, строки в значении через «|» (ru_ru); нет ключа — английский текст.
 */
public final class LabSignBlockEntity extends BlockEntity {
    public static final int LINES = 4;
    private final String[] lines = new String[LINES];

    public LabSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAB_SIGN.get(), pos, state);
        java.util.Arrays.fill(lines, "");
    }

    public String line(int i) {
        return lines[i];
    }

    /** Непустые строки по порядку (пустые в конце чертежа отбрасываются). */
    public List<String> text() {
        List<String> out = new ArrayList<>();
        int last = -1;
        for (int i = 0; i < LINES; i++) if (!lines[i].isBlank()) last = i;
        for (int i = 0; i <= last; i++) out.add(lines[i]);
        return out;
    }

    /** Ключ перевода: непустые строки через «_», только a-z и цифры (SECURITY|OFFICE -> security_office). */
    public String key() {
        String s = String.join(" ", text()).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return s.replaceAll("^_+|_+$", "");
    }

    public void setLines(String... text) {
        for (int i = 0; i < LINES; i++) lines[i] = i < text.length && text[i] != null ? text[i] : "";
        setChanged();
        if (level != null) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < LINES; i++) tag.putString("line" + i, lines[i]);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < LINES; i++) lines[i] = tag.getString("line" + i);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
