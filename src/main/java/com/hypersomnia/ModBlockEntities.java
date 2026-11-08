package com.hypersomnia;

import com.hypersomnia.dream.DreamClockBlockEntity;
import com.hypersomnia.lab.CabinetBlockEntity;
import com.hypersomnia.lab.LabSignBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Блок-сущности лаборатории: содержимое шкафа и текст таблички. */
public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hypersomnia.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CabinetBlockEntity>> CABINET =
            BLOCK_ENTITIES.register("cabinet", () -> BlockEntityType.Builder.of(CabinetBlockEntity::new, ModBlocks.CABINET.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LabSignBlockEntity>> LAB_SIGN =
            BLOCK_ENTITIES.register("lab_sign", () -> BlockEntityType.Builder.of(LabSignBlockEntity::new, ModBlocks.LAB_SIGN.get()).build(null));

    /** [новое] Часы сна: обе половины несут пустую сущность ради анимации. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DreamClockBlockEntity>> DREAM_CLOCK =
            BLOCK_ENTITIES.register("dream_clock", () -> BlockEntityType.Builder.of(DreamClockBlockEntity::new, ModBlocks.DREAM_CLOCK.get()).build(null));
}
