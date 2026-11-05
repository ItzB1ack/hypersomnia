package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModParticles;
import com.hypersomnia.ObserverTuning;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Пар изо рта (BreathClient): полупрозрачное облачко вылетает изо рта, растёт, тормозит, чуть поднимается и тает.
 * Ближе к камере - прозрачнее: когда идёшь сквозь своё дыхание, экран не вспыхивает белым.
 * Текстуры textures/particle/breath_0..3.png (tools/make_breath.py), список - particles/breath.json.
 */
public final class BreathParticle extends TextureSheetParticle {
    private final float size0;
    private final float size1;
    private final float alpha0;
    private final float spin;

    BreathParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);   // без случайного разброса скорости, который добавляет конструктор с 7 числами
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = ObserverTuning.BREATH_FRICTION;
        this.gravity = 0f;
        this.lifetime = Mth.randomBetweenInclusive(random, ObserverTuning.BREATH_LIFE_MIN, ObserverTuning.BREATH_LIFE_MAX);
        this.size0 = ObserverTuning.BREATH_SIZE_START * (0.8f + 0.4f * random.nextFloat());
        this.size1 = ObserverTuning.BREATH_SIZE_END * (0.8f + 0.4f * random.nextFloat());
        this.quadSize = size0;
        this.alpha0 = ObserverTuning.BREATH_ALPHA * (0.75f + 0.5f * random.nextFloat());
        this.alpha = 0f;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        this.spin = (random.nextFloat() - 0.5f) * 0.06f;
        float tint = 0.94f + 0.06f * random.nextFloat();
        setColor(tint, tint, Math.min(1f, tint + 0.03f));
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        yd += ObserverTuning.BREATH_RISE;                 // тёплый пар поднимается
        xd += (random.nextDouble() - 0.5) * 0.0016;      // лёгкое завихрение
        zd += (random.nextDouble() - 0.5) * 0.0016;
        move(xd, yd, zd);
        xd *= friction;
        yd *= friction;
        zd *= friction;
        roll += spin;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        float u = Mth.clamp((age + partialTicks) / lifetime, 0f, 1f);
        float fade = u < 0.12f ? u / 0.12f : (float) Math.pow((1f - u) / 0.88f, 1.5);
        Vec3 cam = camera.getPosition();
        double dx = Mth.lerp(partialTicks, xo, x) - cam.x;
        double dy = Mth.lerp(partialTicks, yo, y) - cam.y;
        double dz = Mth.lerp(partialTicks, zo, z) - cam.z;
        float near = (float) Mth.clamp(Math.sqrt(dx * dx + dy * dy + dz * dz) / ObserverTuning.BREATH_NEAR_FADE, 0.0, 1.0);
        alpha = alpha0 * fade * near * near;
        if (alpha < 0.004f) return;
        super.render(buffer, camera, partialTicks);
    }

    @Override
    public float getQuadSize(float partialTicks) {
        float u = Mth.clamp((age + partialTicks) / lifetime, 0f, 1f);
        return size0 + (size1 - size0) * (1f - (1f - u) * (1f - u));
    }

    /** Ночью пар виден, но не светится: свет блока не ниже BREATH_MIN_LIGHT. */
    @Override
    protected int getLightColor(float partialTicks) {
        int packed = super.getLightColor(partialTicks);
        return LightTexture.pack(Math.max(LightTexture.block(packed), ObserverTuning.BREATH_MIN_LIGHT),
                LightTexture.sky(packed));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new BreathParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    @EventBusSubscriber(modid = Hypersomnia.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {}

        @SubscribeEvent
        public static void providers(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticles.BREATH.get(), Provider::new);
        }
    }
}
