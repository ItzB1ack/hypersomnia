package com.hypersomnia.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypersomnia.Hypersomnia;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Тонкий шрифт субтитров осколка. Ванильный шрифт пиксельный и тонко не умеет, поэтому свой атлас
 * (tools/make_fragment_font.py: Montserrat ExtraLight, OFL) с мягкой фильтрацией (blur в .mcmeta):
 * гладкий в любом масштабе. Каждая буква рисуется отдельно — так её можно трясти.
 */
public final class FragmentFont {
    private FragmentFont() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "textures/font/fragment.png");
    private static final ResourceLocation METRICS =
            ResourceLocation.fromNamespaceAndPath(Hypersomnia.MOD_ID, "voice/font.json");

    /** Картинка глифа в атласе (x, y, w, h), её сдвиг от пера на базовой линии (ox, oy) и ширина шага. */
    private record Glyph(int x, int y, int w, int h, int ox, int oy, float adv) {}

    private static Map<Integer, Glyph> glyphs;
    private static boolean tried;
    private static int size = 64;
    private static int atlasW = 1;
    private static int atlasH = 1;

    /** Метрики загружены (лениво, один раз до перезагрузки ресурсов). */
    static boolean ready() {
        if (!tried) load();
        return glyphs != null;
    }

    /** Перезагрузка ресурсов (F3+T, смена пакета): метрики перечитаются при следующем кадре. */
    static void clear() {
        tried = false;
        glyphs = null;
    }

    private static void load() {
        tried = true;
        try (Reader r = Minecraft.getInstance().getResourceManager().openAsReader(METRICS)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            size = o.get("size").getAsInt();
            JsonArray atlas = o.getAsJsonArray("atlas");
            atlasW = atlas.get(0).getAsInt();
            atlasH = atlas.get(1).getAsInt();
            Map<Integer, Glyph> m = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("glyphs").entrySet()) {
                JsonArray v = e.getValue().getAsJsonArray();
                m.put(e.getKey().codePointAt(0), new Glyph(v.get(0).getAsInt(), v.get(1).getAsInt(),
                        v.get(2).getAsInt(), v.get(3).getAsInt(), v.get(4).getAsInt(), v.get(5).getAsInt(),
                        v.get(6).getAsFloat()));
            }
            glyphs = m;
        } catch (Exception ex) {
            LOGGER.warn("Fragment font metrics not loaded ({}): {}", METRICS, ex.toString());
            glyphs = null;
        }
    }

    /** Есть ли у шрифта такой символ (иначе субтитр рисуется ванильным шрифтом). */
    static boolean has(int cp) {
        return glyphs != null && glyphs.containsKey(cp);
    }

    /** Шаг пера после символа при кегле px (без разрядки). */
    static float advance(int cp, float px) {
        Glyph gl = glyphs == null ? null : glyphs.get(cp);
        return (gl != null ? gl.adv() : size * 0.3f) * px / size;
    }

    /** Символ с пером в (x, y) на базовой линии; цвет и прозрачность — через GuiGraphics.setColor. */
    static void glyph(GuiGraphics g, int cp, float x, float y, float px) {
        Glyph gl = glyphs == null ? null : glyphs.get(cp);
        if (gl == null || gl.w() == 0) return;
        float s = px / size;
        g.pose().pushPose();
        g.pose().translate(x + gl.ox() * s, y + gl.oy() * s, 0f);
        g.pose().scale(s, s, 1f);
        g.blit(ATLAS, 0, 0, gl.w(), gl.h(), gl.x(), gl.y(), gl.w(), gl.h(), atlasW, atlasH);
        g.pose().popPose();
    }
}
