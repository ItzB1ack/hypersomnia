package com.hypersomnia.diary;

import java.util.Map;

/** Числа дневника. Общий код, без клиентских импортов. Страница 0 = хронология, 1..PAGES = свободные страницы. */
public final class DiaryTuning {
    private DiaryTuning() {}

    public static final int PAGES = 11;
    /** Длительность анимации раскрытия книги (мс) до открытия GUI. Было 360, линейно. */
    public static final long OPEN_MS = 460L;
    /** Книга в руке закрывается после выхода из GUI (мс). */
    public static final long CLOSE_MS = 300L;
    /** Появление GUI: фон проявляется, разворот чуть вырастает до полного размера (мс). */
    public static final long INTRO_MS = 220L;
    /** Одна страница книги (разворот = две). */
    public static final int PAPER_W = 170;
    public static final int PAPER_H = 230;
    /** Размер положенного листа на странице (единицы бумаги). */
    public static final int THUMB_W = 54;
    public static final int THUMB_H = 72;
    /** Минимальный размер вписанной миниатюры (для ограничения на сервере). */
    public static final int THUMB_MIN = 8;
    /** Фотографии как отдельные элементы листа (не страницы): id элемента -> печать. */
    public record Photo(String tex, String source, int texW, int texH) {}
    public static final Map<String, Photo> PHOTOS = Map.of(
            "photo_staff", new Photo("photo_staff", "mem_1_photo", 352, 264));
    /** Ширина печати на листе в единицах бумаги; высота считается по пропорции текстуры. */
    public static final int PHOTO_W = 130;
    public static final int MAX_ENTRIES = 48;
    /** Жест листания: сдвиг мыши (GUI px), с которого лист отрывается от страницы. */
    public static final float DRAG_START_PX = 6f;
    /** Доля пути, начиная с которой отпущенный лист доходит до конца, а не возвращается. */
    public static final float COMMIT_FRACTION = 0.3f;
    /** Шелест листа при завершённом перевороте. Ориентиры, не проверенный баланс. */
    public static final float TURN_SOUND_VOLUME = 0.35f;
    public static final float TURN_SOUND_PITCH = 1.0f;
    /** Минимум между двумя шелестами (мс): частые развороты не складываются в очередь звуков. */
    public static final long TURN_SOUND_MIN_INTERVAL_MS = 250L;
    /** Длительность полного переворота (с). Раньше было 1 / 2.4 ≈ 0.42. */
    public static final float TURN_SECONDS = 0.42f;
    /** Скорость жеста (доля страницы в секунду): быстрее этого лист долетает или возвращается независимо от пути. */
    public static final float FLICK_SPEED = 2.5f;
    /** Угол страницы (единицы бумаги): клик листает, наведение показывает загиб. */
    public static final int CORNER_SIZE = 22;
    /** Лист в руке чуть крупнее: «приподнят над столом». */
    public static final float HELD_LIFT_SCALE = 1.04f;
    /** Тихие звуки стола: взять и положить лист. */
    public static final float PICK_SOUND_VOLUME = 0.2f;
    public static final float PICK_SOUND_PITCH = 1.5f;
    public static final float DROP_SOUND_VOLUME = 0.3f;
    public static final float DROP_SOUND_PITCH = 1.3f;
    /** Второй разворот, левая страница (2-3): таблица «буква -> знак», которую игрок заполняет сам. Кладут только фотографии. */
    public static final int KEY_PAGE = 2;
    /** Версия таблицы ключа в сохранении ("kv"). 2 — алфавит черт; таблица другой версии при загрузке сбрасывается. */
    public static final int KEY_VERSION = 2;
}