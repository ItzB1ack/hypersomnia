package com.hypersomnia;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Частицы мода. Рисует их только клиент (client/BreathParticle), сервер про них не знает ничего, кроме имени. */
public final class ModParticles {
    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Hypersomnia.MOD_ID);

    /** Пар изо рта (client/BreathClient): облачко растёт, тормозит, чуть поднимается и тает.
     *  Конструктор SimpleParticleType защищённый, поэтому анонимный подкласс. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BREATH =
            PARTICLES.register("breath", () -> new SimpleParticleType(false) {});

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}
