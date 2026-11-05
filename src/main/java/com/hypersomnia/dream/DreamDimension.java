package com.hypersomnia.dream;

import com.hypersomnia.Hypersomnia;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class DreamDimension {
    private DreamDimension() {}

    public static final ResourceKey<Level> LEVEL =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "dream"));
}
