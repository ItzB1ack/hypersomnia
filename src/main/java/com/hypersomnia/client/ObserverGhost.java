package com.hypersomnia.client;

import com.hypersomnia.Hypersomnia;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * «Фигура игрока» для наблюдателя. Не добавляется в мир: только рендерится на клиенте,
 * поэтому у неё нет коллизии, её не видят другие игроки и она не мешает мобам.
 * Силуэт и пропорции как у игрока (модель Steve), отличается только лицо.
 */
public final class ObserverGhost extends RemotePlayer {
    public static final int BODY = 0, EYES = 1, ECHO = 2;
    /** Какой скин рисовать сейчас; ставит ObserverClient перед каждым проходом рендера. */
    public int variant = BODY;

    private static PlayerSkin skin(String file) {
        return new PlayerSkin(
                ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/entity/" + file),
                null, null, null, PlayerSkin.Model.WIDE, true);
    }

    private static final PlayerSkin SKIN_BODY = skin("observer.png");
    private static final PlayerSkin SKIN_EYES = skin("observer.png"); // глаза рисует ObserverClient
    private static final PlayerSkin SKIN_ECHO = skin("observer_echo.png");

    public ObserverGhost(ClientLevel level) {
        super(level, new GameProfile(
                UUID.nameUUIDFromBytes("hypersomnia:observer".getBytes(StandardCharsets.UTF_8)), "Observer"));
    }

    @Override
    public PlayerSkin getSkin() {
        return switch (variant) {
            case EYES -> SKIN_EYES;
            case ECHO -> SKIN_ECHO;
            default -> SKIN_BODY;
        };
    }

    /**
     * Скрывает ник: LivingEntityRenderer не рисует имя у сущности, у которой есть пассажиры.
     * Больше нигде isVehicle для этого призрака не используется (в мир он не добавляется).
     */
    @Override
    public boolean isVehicle() {
        return true;
    }
}
