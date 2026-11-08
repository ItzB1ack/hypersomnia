package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.hypersomnia.lab.CapsuleBlock;
import com.hypersomnia.lab.LabTuning;
import com.hypersomnia.lab.SeatEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Лежащий в капсуле B5 рисуется как на ванильной кровати: та же поза (Pose.SLEEPING),
 * тот же сдвиг тела на 1.52 блока к изножью и тот же разворот. Своя поправка нужна потому, что
 * getBedOrientation у игрока без кровати даёт UP (поворот 0), а не FACING капсулы.
 * Голова поворачивается за взглядом не больше CAPSULE_HEAD_LIMIT градусов от оси капсулы.
 */
@EventBusSubscriber(modid = Hypersomnia.MOD_ID, value = Dist.CLIENT)
public final class LabCapsuleRender {
    private LabCapsuleRender() {}

    @SubscribeEvent
    public static void before(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player.getVehicle() instanceof SeatEntity seat) || !seat.isLying()) return;
        if (!player.hasPose(Pose.SLEEPING)) return; // позу ставит SeatEntity.tick

        Direction facing = seat.getLieFacing();
        float lieYaw = CapsuleBlock.sleepYaw(facing);
        player.yBodyRot = lieYaw;
        player.yBodyRotO = lieYaw;
        float head = lieYaw + Mth.clamp(Mth.wrapDegrees(player.getYRot() - lieYaw),
                -LabTuning.CAPSULE_HEAD_LIMIT, LabTuning.CAPSULE_HEAD_LIMIT);
        player.setYHeadRot(head);
        player.yHeadRotO = head;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-facing.getStepX() * LabTuning.CAPSULE_RENDER_SHIFT, 0.0,
                -facing.getStepZ() * LabTuning.CAPSULE_RENDER_SHIFT);
        pose.mulPose(Axis.YP.rotationDegrees(lieYaw));
    }

    @SubscribeEvent
    public static void after(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.getVehicle() instanceof SeatEntity seat) || !seat.isLying()) return;
        if (!player.hasPose(Pose.SLEEPING)) return;
        event.getPoseStack().popPose();
    }
}
