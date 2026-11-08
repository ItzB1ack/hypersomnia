package com.hypersomnia.lab;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.ModSounds;
import com.hypersomnia.dream.DreamDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Шифр 9: за заложенной дверью палаты 2D (B4; строка knock чертежа, KIND_KNOCK) стучат, пока рядом кто-то есть.
 * Стук слышен и в реальной лаборатории, и во сне: сон — необязательная копия, ключ к B5 находится в реальном B4
 * (у лифта, в стене коридора палаты 2D). Каждая цифра кода — как кнопка панели: удары по номеру ряда, пауза,
 * удары по номеру столбца (LabVariants.keyCell), длинная пауза до следующей цифры; после четырёх цифр — тишина
 * и повтор. Отошли и вернулись — счёт с начала.
 * Звук без источника в мире: никого не видно, только стук (субтитр hypersomnia.subtitle.lab_knock).
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID)
public final class LabKnocks {
    private LabKnocks() {}

    /** Измерение -> (стена (BlockPos.asLong) -> тик внутри цикла стука). Только пока рядом игрок; не сохраняется. */
    private static final Map<ResourceKey<Level>, Map<Long, int[]>> RUNNING = new HashMap<>();

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        tick(server, server.overworld());
        tick(server, server.getLevel(DreamDimension.LEVEL));
    }

    private static void tick(MinecraftServer server, ServerLevel level) {
        if (level == null) return;
        Map<Long, int[]> running = RUNNING.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        if (level.players().isEmpty()) {
            running.clear();
            return;
        }
        LabSavedData d = LabSavedData.get(level);
        if (!d.built) {
            running.clear();
            return;
        }
        if (server.getTickCount() % LabTuning.KNOCK_SCAN_TICKS == 0) scan(level, d, running);
        if (running.isEmpty()) return;
        long seed = LabSavedData.cipherSeed(server);
        Iterator<Map.Entry<Long, int[]>> it = running.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, int[]> e = it.next();
            Integer v = d.inter.get(e.getKey());
            if (v == null || v / 1000 != LabSavedData.KIND_KNOCK) {
                it.remove();
                continue;
            }
            int[] run = e.getValue();
            List<Integer> times = times(LabVariants.knockCode(seed));
            int cycle = times.get(times.size() - 1);
            if (times.subList(0, times.size() - 1).contains(run[0])) {
                BlockPos p = BlockPos.of(e.getKey());
                level.playSound(null, p, ModSounds.LAB_KNOCK.get(), SoundSource.AMBIENT, LabTuning.KNOCK_VOLUME, LabTuning.KNOCK_PITCH);
            }
            run[0] = run[0] + 1 >= cycle ? LabTuning.KNOCK_START_TICKS : run[0] + 1;
        }
    }

    /** Кто подошёл — у той стены стук начинается с начала; от кого все отошли — стихает. */
    private static void scan(ServerLevel level, LabSavedData d, Map<Long, int[]> running) {
        double r2 = (double) LabTuning.KNOCK_RADIUS * LabTuning.KNOCK_RADIUS;
        List<Long> walls = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : d.inter.entrySet())
            if (e.getValue() / 1000 == LabSavedData.KIND_KNOCK) walls.add(e.getKey());
        for (long key : walls) {
            BlockPos p = BlockPos.of(key);
            boolean near = false;
            for (ServerPlayer pl : level.players())
                if (!pl.isSpectator() && pl.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= r2) near = true;
            if (near) running.putIfAbsent(key, new int[]{0});
            else running.remove(key);
        }
    }

    private static String lastCode;
    private static List<Integer> lastTimes;

    private static List<Integer> times(String code) {
        if (!code.equals(lastCode)) {
            lastTimes = schedule(code);
            lastCode = code;
        }
        return lastTimes;
    }

    /**
     * Тики ударов от начала цикла по коду; последний элемент — длина цикла.
     * Цифра: ряд ударов, KNOCK_PAUSE_TICKS, столбец ударов, KNOCK_DIGIT_TICKS до следующей; в конце KNOCK_REPEAT_TICKS.
     */
    static List<Integer> schedule(String code) {
        List<Integer> out = new ArrayList<>();
        int t = LabTuning.KNOCK_START_TICKS;
        for (int i = 0; i < code.length(); i++) {
            int[] cell = LabVariants.keyCell(code.charAt(i) - '0');
            for (int part = 0; part < 2; part++) {
                for (int k = 0; k < cell[part]; k++) {
                    out.add(t);
                    t += LabTuning.KNOCK_GAP_TICKS;
                }
                t += (part == 0 ? LabTuning.KNOCK_PAUSE_TICKS : LabTuning.KNOCK_DIGIT_TICKS) - LabTuning.KNOCK_GAP_TICKS;
            }
        }
        out.add(t + LabTuning.KNOCK_REPEAT_TICKS - LabTuning.KNOCK_DIGIT_TICKS);
        return out;
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        RUNNING.clear();
    }
}
