package com.hypersomnia.chase;

/**
 * Числа погони (ChaseHandler на сервере, ObserverClient и ChaseClient на клиенте). ГИПОТЕЗА для ручного прохождения.
 * Массивы *_BY_STAGE индексируются стадией 0..5. Скорости — блоков за тик (шаг игрока ~0.216, бег ~0.281).
 * Взгляд в погоне его не прогоняет (в отличие от обычной фигуры): только пока стоит на следе, заметил — сразу бежит.
 */
public final class ChaseTuning {
    private ChaseTuning() {}

    // ---- Когда бывает ----
    /** С какой стадии бывает погоня. До CATCH_MIN_STAGE и в самый первый раз — учебная: не догоняет. */
    public static final int MIN_STAGE = 2;
    public static final int CATCH_MIN_STAGE = 3;
    /** С этой стадии поимка ранит (тип урона hypersomnia:fragment); раньше — забирает вещь из руки. */
    public static final int DAMAGE_MIN_STAGE = 5;
    /** Не чаще раза в столько дней осколка (ObserverProgressData.days). */
    public static final int MIN_DAYS_BETWEEN = 2;
    /** Верхний мир: ночь (время суток) или темно на месте (пещера). Мир снов — всегда. */
    public static final long NIGHT_FROM = 13000L;
    public static final long NIGHT_TO = 23000L;
    public static final int DARK_LIGHT = 4;

    // ---- Безопасный старт (иначе отказ) ----
    public static final double SAFE_MOB_RADIUS = 16.0;
    public static final int RECENT_HURT_TICKS = 200;
    public static final int SAFE_LAVA_RADIUS = 4;
    /** Обрыв: в радиусе CLIFF_RADIUS есть колонка, где под уровнем ног CLIFF_DEPTH блоков пустоты. */
    public static final int CLIFF_RADIUS = 3;
    public static final int CLIFF_DEPTH = 5;

    // ---- След игрока (по нему он бежит) ----
    /** Точка следа каждые TRAIL_STEP блоков (только на земле, лестнице или в воде), не больше TRAIL_MAX точек. */
    public static final double TRAIL_STEP = 0.5;
    public static final int TRAIL_MAX = 240;
    /** Скачок дальше этого за тик — телепорт: след стирается, погоня прерывается. */
    public static final double TRAIL_TELEPORT = 8.0;
    /** Старт — точка следа на столько блоков пути позади, не ближе START_DIST_MIN по прямой и вне взгляда игрока. */
    public static final double START_PATH_MIN = 14.0;
    public static final double START_PATH_MAX = 30.0;
    public static final double START_DIST_MIN = 9.0;
    /** «Вне взгляда» при выборе старта: дальше этого угла от направления взгляда (с запасом на широкий FOV). */
    public static final double START_VIEW_HALF_DEG = 75.0;

    // На последней стадии сильнее паника и короткие реплики ярости [новое].
    public static final float RAGE_MIN_PANIC = .70f;
    public static final int RAGE_FIRST_TICK = 80;
    public static final int RAGE_PERIOD_TICKS = 180;
    public static final int RAGE_TEXT_TICKS = 26;
    public static final int RAGE_TEXT_VARIANTS = 3;

    // ---- Фазы ----
    /** Предупреждение: стоит на старте (тики). Если его заметить и выдержать взгляд — исчезает, погони нет. */
    public static final int WARN_MIN = 60;
    public static final int WARN_MAX = 120;
    public static final double[] SPEED_BY_STAGE = {0.20, 0.20, 0.20, 0.25, 0.29, 0.31};
    /** Учебная погоня: медленнее шага и исчезает в TEACH_VANISH_DIST от игрока. */
    public static final double TEACH_SPEED = 0.20;
    public static final double TEACH_VANISH_DIST = 4.0;
    /** Сколько он бежит (тики); продержался — он останавливается и исчезает. */
    public static final int[] RUN_TICKS_BY_STAGE = {300, 300, 300, 400, 500, 600};
    /** Ближе этого, при свободной прямой и полу без ям бежит прямо к игроку, а не по пути. */
    public static final double DIRECT_DIST = 8.0;
    // ---- Поиск пути (ChasePath, A*): бежит к игроку кратчайшей дорогой, а не по следу ----
    /** Путь пересчитывается раз в REPATH_TICKS; если игрок ушёл от цели дальше REPATH_MOVE или путь кончился — раньше,
     *  но не чаще раза в REPATH_MIN_TICKS. Пути нет совсем — следующая попытка через REPATH_FAIL_TICKS (а пока по следу). */
    public static final int REPATH_TICKS = 10;
    public static final int REPATH_MIN_TICKS = 4;
    public static final int REPATH_FAIL_TICKS = 20;
    public static final double REPATH_MOVE = 2.0;
    /** Срез углов на пути: сколько точек вперёд смотреть и на сколько отнести боковые лучи тела (блоки). */
    public static final int CORNER_LOOKAHEAD = 4;
    public static final double CORNER_SIDE = 0.3;
    /** Клиентское сглаживание позиции фигуры: доля до цели за тик по горизонтали и по высоте (ступени). */
    public static final double SHOWN_XZ = 0.55;
    public static final double SHOWN_Y = 0.25;
    /** Работа одного поиска: клеток не больше PATH_MAX_NODES и не дальше PATH_RANGE блоков от него по горизонтали. */
    public static final int PATH_MAX_NODES = 1500;
    public static final int PATH_RANGE = 48;
    /** Пути к игроку нет: столько попыток дойти до точки его следа — с конца следа, через TRAIL_JOIN_STEP точек. */
    public static final int TRAIL_JOIN_TRIES = 2;
    public static final int TRAIL_JOIN_STEP = 10;
    public static final int TRAIL_JOIN_NODES = 600;
    /** Цена ходов (шаг по ровному = 1): диагональ; доплата за шаг на блок вверх, за каждый блок спрыгивания, за прыжок
     *  через яму, лестница или вплавь на блок вверх-вниз, вода, закрытая дверь (обход не длиннее — пойдёт в обход). */
    public static final double PATH_DIAG_COST = 1.4142;
    public static final double PATH_UP_COST = 0.5;
    public static final double PATH_DROP_COST = 0.3;
    public static final double PATH_JUMP_COST = 1.5;
    public static final double PATH_CLIMB_COST = 1.5;
    public static final double PATH_WATER_COST = 2.0;
    public static final double PATH_DOOR_COST = 20.0;
    /** Спрыгивает не больше чем с такой высоты (блоки). */
    public static final int PATH_DROP_MAX = 4;
    /** Лезет по стене вверх до стольких блоков (игрок на столбе или уступе). 0-1: не лазает, стоит внизу. */
    public static final int PATH_SCRAMBLE_MAX = 3;
    public static final double PATH_SCRAMBLE_COST = 1.0;
    /** Цель недостижима, но ближайшая клетка не дальше стольких блоков по горизонтали: стоит под игроком. */
    public static final double PATH_UNDER_DIST = 3.0;
    /** Вес эвристики A*: больше 1 — ищет быстрее, путь чуть длиннее кратчайшего. */
    public static final double PATH_HEURISTIC = 1.2;
    /** Перед закрытой дверью встаёт на таком расстоянии от центра её клетки. */
    public static final double DOOR_STOP = 0.8;
    public static final double CATCH_DIST = 1.3;
    /** Рывок: ближе LUNGE_DIST блоков он быстрее в LUNGE_SPEED раз (на стадии 3 — 6 бл/с, быстрее бега игрока):
     *  подпустил вплотную — не уйдёшь. Учебная погоня без рывка. */
    public static final double LUNGE_DIST = 3.0;
    public static final double LUNGE_SPEED = 1.2;
    /** Упреждение на прямой: бежит в точку, где игрок будет через (расстояние / скорость) тиков, но не дальше
     *  LEAD_MAX_TICKS; вплотную — LEAD_NEAR_TICKS. Скорость игрока сглажена: доля нового замера за тик VEL_SMOOTH. */
    public static final double LEAD_MAX_TICKS = 10.0;
    public static final double LEAD_NEAR_TICKS = 2.0;
    public static final double VEL_SMOOTH = 0.5;
    /** Упёрся в закрытую дверь или стену на следе: стучит столько тиков и уходит (если не откроют). */
    public static final int BLOCKED_TICKS = 70;
    /** Высоты лучей проверки прохода над точкой следа: проход свободен, если свободен хотя бы один. */
    public static final double RAY_LOW = 0.9;
    public static final double RAY_HIGH = 1.4;
    /** Дальше этого от игрока — погоня прервана (телепорт, лифт, падение). */
    public static final double ABORT_DIST = 64.0;
    /** Взгляд на него (сервер): угол от направления взгляда и точки тела над ногами (фигура выше игрока). */
    public static final double STARE_HALF_DEG = 30.0;
    /** Пока стоит на следе (WARN): столько тиков взгляда на него — и он сразу бежит (взгляд в погоне не прогоняет). */
    public static final int WARN_SEEN_TICKS = 6;
    public static final double[] STARE_POINTS = {0.9, 1.6, 2.2};
    /** Клиент не получил ответа на просьбу о погоне (тики). */
    public static final int REQUEST_TIMEOUT = 40;

    // ---- Поимка ----
    /** Чёрный кадр: сервер держит погоню столько тиков, потом конец — фигура пропадает, когда кадр начинает светлеть. */
    public static final int CAUGHT_TICKS = ChaseTuning.BLACK_IN + ChaseTuning.BLACK_HOLD;
    public static final float FATIGUE_ON_CATCH = 0.25f;
    public static final int MADNESS_ON_CATCH = 1;
    public static final float DAMAGE = 8.0f;
    /** Забранная вещь возвращается при пробуждении через столько дней (случайно в пределах), рядом с кроватью. */
    public static final int RETURN_DAYS_MIN = 1;
    public static final int RETURN_DAYS_MAX = 3;
    public static final int RETURN_RADIUS = 3;
    public static final int RETURN_PICKUP_DELAY = 20;
    /** Больше стольких забранных вещей не копится: дальше поимка вещь не трогает. */
    public static final int TAKEN_MAX = 8;

    // ---- Клиент: сердце, шаги, кадр ----
    /** Интервал ударов сердца (тики, дробный) при панике 0 и 1: ~70 и ~126 ударов в минуту. */
    public static final float HEART_SLOW_TICKS = 17f;
    public static final float HEART_FAST_TICKS = 9.5f;
    /** Живой ритм: каждый интервал и высота удара случайно гуляют на столько (доля). */
    public static final float HEART_TIME_JITTER = 0.04f;
    public static final float HEART_PITCH_JITTER = 0.02f;
    public static final float HEART_VOLUME_MIN = 0.3f;
    public static final float HEART_VOLUME_MAX = 0.7f;
    public static final float HEART_PITCH_MIN = 0.97f;
    public static final float HEART_PITCH_MAX = 1.04f;
    /** Ниже этой паники сердце не слышно. */
    public static final float HEART_MIN_PANIC = 0.12f;
    /** Паника: при предупреждении, у двери, при беге — от дальнего к ближнему (блоки). */
    public static final float PANIC_WARN = 0.35f;
    public static final float PANIC_BLOCKED = 0.75f;
    public static final float PANIC_RUN_MIN = 0.5f;
    public static final double PANIC_NEAR = 2.0;
    public static final double PANIC_FAR = 22.0;
    /** Рост паники за тик (доля разницы) и спад после конца (тики до нуля). */
    public static final float PANIC_RISE = 0.15f;
    public static final int PANIC_FADE_TICKS = 240;
    /** Виньетка по краям в такт сердцу (текстура виньетки усталости). */
    public static final float VIGNETTE_BASE = 0.25f;
    public static final float VIGNETTE_PULSE = 0.35f;
    public static final float BEAT_DECAY = 0.82f;
    /** Чёрный кадр поимки: нарастание, держится, спад (тики). */
    public static final int BLACK_IN = 4;
    public static final int BLACK_HOLD = 30;
    public static final int BLACK_OUT = 16;
    /** Его шаги: звук шага блока под ним каждые STEP_EVERY блоков бега. */
    public static final double STEP_EVERY = 1.5;
    public static final float STEP_VOLUME = 0.9f;
    /** Стук в дверь, пока упёрся (тики между ударами). */
    public static final int KNOCK_EVERY = 14;
    public static final float KNOCK_VOLUME = 0.9f;
    /** Пыль, пока на него смотрят: частиц за тик при полном взгляде. */
    public static final int STARE_DUST_MAX = 3;
    /** «Обесточено» (LabOutage, флаг ChasePayload.I_DARK): тело в темноте не видно — глаза на его лице видны с такого расстояния. */
    public static final double DARK_EYES_DISTANCE = 40.0;
    /** Тело в погоне поворачивается по ходу бега (доля разницы за тик), голова — к игроку, не дальше HEAD_MAX от тела. */
    public static final float BODY_TURN = 0.35f;
    public static final float HEAD_MAX = 75f;

    // ---- Клиент: музыка погони (ChaseMusic), категория «Враждебные существа» ----
    /** Гул: пока он стоит на следе, бежит, упёрся в дверь. */
    public static final float MUSIC_DRONE_WARN = 0.4f;
    public static final float MUSIC_DRONE_RUN = 0.6f;
    public static final float MUSIC_DRONE_BLOCKED = 0.5f;
    /** Подъём: с MUSIC_RISE_FAR блоков нарастает до MUSIC_RISE_MAX к MUSIC_RISE_NEAR; у двери — MUSIC_RISE_BLOCKED. */
    public static final float MUSIC_RISE_MAX = 0.55f;
    public static final float MUSIC_RISE_BLOCKED = 0.3f;
    public static final double MUSIC_RISE_FAR = 24.0;
    public static final double MUSIC_RISE_NEAR = 3.0;
    /** Бормотание у головы: ближе MUSIC_NEAR_DIST блоков, вплотную — MUSIC_NEAR_MAX. */
    public static final double MUSIC_NEAR_DIST = 8.0;
    public static final float MUSIC_NEAR_MAX = 0.6f;
    /** Скорость громкости: тики на подъём от 0 до 1 и на спад от 1 до 0 во время погони; затухание после конца. */
    public static final int MUSIC_RISE_TICKS = 30;
    public static final int MUSIC_FALL_TICKS = 20;
    public static final int MUSIC_FADE_OUT_TICKS = 70;
    /** Шёпот осколка: первый через столько тиков, дальше раз в MIN..MAX; ближе HEAD_DIST блоков — у самой головы. */
    public static final int WHISPER_FIRST_TICKS = 8;
    public static final int WHISPER_MIN_TICKS = 35;
    public static final int WHISPER_MAX_TICKS = 70;
    public static final float WHISPER_VOLUME = 0.9f;
    public static final float WHISPER_HEAD_VOLUME = 0.5f;
    public static final double WHISPER_HEAD_DIST = 4.0;
    public static final double WHISPER_HEAD_OFFSET = 1.2;

    // ---- Субтитры голоса осколка (client/ChaseSubtitles) ----
    /** Дрожание ванильной строки субтитров, px (умножается на панику). */
    public static final float SUBTITLE_SHAKE_X = 4.0f;
    public static final float SUBTITLE_SHAKE_Y = 2.6f;
    /** Цикл глитча в кадрах (20 fps): первые 2 кадра из N — сбой. */
    public static final int SUBTITLE_GLITCH_EVERY = 6;
    /** Смещение красного двойника, px, и его прозрачность. */
    public static final float SUBTITLE_GHOST_OFFSET = 4.5f;
    public static final float SUBTITLE_GHOST_ALPHA = 0.65f;
    /** Голубой двойник с другой стороны (цветовой раскол), px. */
    public static final float SUBTITLE_CHROMA_OFFSET = 3.5f;
    /** Рывок строки на кадре сбоя, px. */
    public static final float SUBTITLE_JOLT = 7.0f;
    /** Прозрачность полос и их высота, px. */
    public static final int SUBTITLE_BAND_ALPHA = 0x88;
    public static final int SUBTITLE_BAND_MIN = 4;
    public static final int SUBTITLE_BAND_MAX = 11;
    /** Максимальный сдвиг полосы по горизонтали, px. */
    public static final int SUBTITLE_BAND_SHIFT = 11;

    // ------------------------------------------------------------------
    // Пиксельная эрозия кадра и редкие фразы вместо глаз по углам (client/ChaseText,
    // client/PixelDecay, client/TextSwarm) [новое]
    // ------------------------------------------------------------------
    /** Строк в начале погони и в пике; за FILL_TICKS число растёт до пика, паника немного опережает рост. */
    public static final int SWARM_START = 3;
    public static final int SWARM_MAX = 12;
    public static final int SWARM_FILL_TICKS = 480;
    public static final float SWARM_PANIC_BOOST = 0.18f;
    /** Появление роя при начале погони и затухание после конца (тики). */
    public static final int SWARM_FADE_IN_TICKS = 20;
    public static final int SWARM_END_FADE_TICKS = 24;
    /** Общая яркость роя 0..1. */
    public static final float SWARM_ALPHA = 0.8f;
    /** Свободный центр кадра: полуширина и полувысота в долях экрана (прицел и то, что впереди, остаются читаемыми). */
    public static final float SWARM_AVOID_X = 0.16f;
    public static final float SWARM_AVOID_Y = 0.20f;
    /** Пиксельная эрозия по краям кадра: стартовая доля, прирост за погоню, добавка от паники и редкий красный акцент. */
    public static final float DECAY_START = 0.30f;
    public static final float DECAY_GROW = 0.55f;
    public static final float DECAY_PANIC = 0.15f;
    public static final float DECAY_RED = 0.06f;
}
