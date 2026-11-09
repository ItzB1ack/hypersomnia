package com.hypersomnia;

/**
 * Числа наблюдателя. Всё в одном месте, как FatigueTuning.
 * Числа в блоках «Режиссёр», «Стадии», «Память», «Нарушение правила» — ГИПОТЕЗА для ручного прохождения, не готовый баланс.
 * Массивы *_BY_STAGE индексируются стадией 0..5. Массивы EP_* индексируются ObserverDirector.Kind:
 * GHOST, DOOR, SCARE, BED, STILL, GLITCH, CHASE (числа самой погони — chase/ChaseTuning).
 */
public final class ObserverTuning {
    private ObserverTuning() {}

    // ---- Когда может появляться (контекст «устал») ----
    /** Доля усталости, с которой наблюдатель начинает появляться (пока безумие = 0). */
    public static final float START_RATIO = 0.30f;
    /** То же, но после первого принудительного сна (безумие > 0). */
    public static final float START_RATIO_AFTER_MADNESS = 0.15f;
    /** Безумие, при котором его вклад в давление достигает максимума. */
    public static final int MADNESS_CAP = 5;
    /** Максимальная освещённость места появления (0..15). Ночью/в пещерах/в тени. */
    public static final int MAX_LIGHT = 9;

    // ---- Стадии осколка: дни + очки воспоминаний + ограниченное давление бодрствования ----
    public static final int TICKS_PER_DAY = 24000;
    public static final int MEMORY_POINTS = 2;
    /** Очки, с которых начинаются стадии 1..5. */
    public static final int[] STAGE_POINTS = {5, 10, 16, 22, 28};
    /** Стадия 5 требует хотя бы столько воспоминаний, иначе остаётся 4. */
    public static final int STAGE5_MIN_MEMORIES = 4;

    /** Дополнительные очки за искусственное и долгое бодрствование [новое]. */
    public static final int EXPOSURE_MAX_POINTS = 8;
    public static final int EXPOSURE_SYRINGE_POINTS = 1;
    public static final int EXPOSURE_AWAKE_TICKS_PER_POINT = 12000;
    public static final float EXPOSURE_FATIGUE_THRESHOLD = .75f;
    public static final int EXPOSURE_REST_REDUCTION = 1;

    public static int stage(int days, int memories) { return stage(days, memories, 0); }
    public static int stage(int days, int memories, int exposure) {
        long points = (long) days + (long) MEMORY_POINTS * memories + Math.max(0, Math.min(EXPOSURE_MAX_POINTS, exposure));
        int s = 0;
        for (int i = 0; i < STAGE_POINTS.length; i++) if (points >= STAGE_POINTS[i]) s = i + 1;
        if (s >= 5 && memories < STAGE5_MIN_MEMORIES) s = 4;
        return s;
    }

    // Стадия 5: индивидуальный короткий образ детской; мир и инвентари не меняются [новое].
    public static final int NORA_ROOM_TRIGGER_RADIUS = 8;
    public static final int NORA_ROOM_SEARCH_RADIUS = 4;
    public static final int NORA_ROOM_DURATION = 400;
    public static final int NORA_ROOM_FADE_TICKS = 36;
    public static final float NORA_ROOM_FADE_MIN_SCALE = .04f;
    public static final int NORA_ROOM_ACK_TICKS = 80;
    public static final int NORA_ROOM_ACK_RETRY_TICKS = 40;
    public static final double NORA_ROOM_VIEW_DOT = .55;
    public static final int NORA_ROOM_PICTURE_COUNT = 4;
    public static final int[] NORA_ROOM_PICTURE_INDICES = {4, 3, 1, 0};
    public static final int NORA_ROOM_PICTURE_SEARCH = 3;
    public static final float NORA_ROOM_PICTURE_SIZE = .70f;
    public static final float NORA_ROOM_PICTURE_OFFSET = .505f;
    public static final int NORA_ROOM_RETRY_TICKS = 1200;
    public static final int NORA_ROOM_SCAN_TICKS = 100;
    public static final int NORA_ROOM_LAYOUT_BED = 1;
    public static final int NORA_ROOM_LAYOUT_CARPET = 1 << 1;
    public static final int NORA_ROOM_LAYOUT_TOY = 1 << 2;
    // Личное краткое перекрытие пути к лаборатории, без изменения блоков [новое].
    public static final int LAB_PATH_NEAR = 20;
    public static final int LAB_PATH_FAR = 48;
    public static final int LAB_PATH_AHEAD = 3;
    public static final int LAB_PATH_DURATION = 300;
    public static final int LAB_PATH_RETRY_TICKS = 1200;
    public static final int LAB_PATH_SCAN_TICKS = 40;
    public static final int LAB_PATH_ESCAPE_DISTANCE = 12;
    public static final double LAB_PATH_WIDTH = 1.45;
    // Личная домашняя композиция в пределах преграды [новое].
    public static final int LAB_PATH_HOME_APPEAR_TICKS = 32;
    public static final float LAB_PATH_HOME_MIN_SCALE = .08f;
    public static final float LAB_PATH_HOME_ITEM_SCALE = .55f;

    // Тихие личные звуки в лаборатории [новое].
    public static final int LAB_AMBIENT_MIN_STAGE = 3;
    public static final int LAB_AMBIENT_SCAN_TICKS = 20;
    public static final int LAB_AMBIENT_DWELL_MIN = 600;
    public static final int LAB_AMBIENT_DWELL_RANGE = 600;
    public static final int LAB_AMBIENT_COOLDOWN = 7200;
    public static final float LAB_AMBIENT_VOLUME = .28f;
    public static final float LAB_AMBIENT_PITCH_MIN = .70f;
    public static final float LAB_AMBIENT_PITCH_RANGE = .15f;
    public static final int LAB_AMBIENT_TAIL_TICKS = 20;
    /**
     * Сцены тихих звуков. Моменты — в тиках от начала; коды звуков: 0 click, 1 dropout, 2 hiss, 3 knock,
     * 4 шаг по камню, 5 железная дверь, 6 поршень, 7 железный люк, 8 капель, 9 стекло, 10 металлическая защёлка,
     * 11 шаг по дереву, 12 деревянная дверь закрывается, 13 деревянная дверь скрипит, 14 нота шкатулки (NOTE_BLOCK_CHIME),
     * 15 звонок лифта (NOTE_BLOCK_BELL), 16 выдох (PLAYER_BREATH). Сцены 0..10, 18 и 19 — лаборатория,
     * 11..17 — ночной дом (16 ещё и на B4). [новое]
     */
    public static final int[][] LAB_AMBIENT_SCENE_TICKS = {
            {8, 28, 58},              // 0: щелчки мёртвого динамика
            {12, 44, 52},             // 1: щелчок и обрыв ленты
            {18, 38, 58},             // 2: шипение в пустой комнате
            {20, 30, 40},             // 3: стук за стеной
            {10, 18, 26, 34, 44, 52}, // 4: шаги по потолку
            {12, 44, 52},             // 5: лифт уезжает без игрока
            {14, 50},                 // 6: металлический скрип
            {8, 26, 64},              // 7: интерком просыпается и глохнет
            {24, 78},                 // 8: капель
            {12, 20, 28, 36, 44, 96}, // 9: те же шаги, слишком длинная пауза, ещё один
            {14, 86},                 // 10: стекло капсулы оседает, затем защёлка
            {10, 22, 34},             // 11: кто-то ходит наверху
            {16},                     // 12: далеко закрылась дверь
            {12, 46},                 // 13: скрип, пауза, скрип
            {18, 28},                 // 14: стук в стену
            {10, 22, 34, 46, 58, 70}, // 15: шаги приближаются и обрываются [новое]
            {14, 30, 46, 68},         // 16: далёкая шкатулка, четвёртая нота спотыкается, пятой нет [новое]
            {12, 32, 52, 72, 132},    // 17: капли в раковине идут ровно, пятая приходит после долгой паузы [новое]
            {10, 54, 72},             // 18: лифт зовут на пустом этаже: два звонка, двери закрылись, никто не вышел [новое]
            {26, 92, 118}             // 19: за стеклом закрытой капсулы слышен выдох, затем лента щёлкает [новое]
    };
    public static final byte[][] LAB_AMBIENT_SCENE_SOUNDS = {
            {0, 0, 1}, {0, 2, 2}, {2, 2, 2}, {3, 3, 3},
            {4, 4, 4, 4, 4, 4}, {5, 6, 6}, {7, 7}, {0, 2, 1}, {8, 8}, {4, 4, 4, 4, 4, 4},
            {9, 10},
            {11, 11, 11}, {12}, {13, 13}, {3, 3},
            {11, 11, 11, 11, 11, 11},
            {14, 14, 14, 14},
            {8, 8, 8, 8, 8},
            {15, 15, 5},
            {16, 16, 0}
    };
    /**
     * Громкость каждого звука сцены (множитель к громкости сцены); пустой массив — все звуки 1.
     * Сцена 15: шаги нарастают, последний обрывается без эха. [новое]
     */
    public static final float[][] LAB_AMBIENT_SCENE_STEP_VOLUME = {
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            {.35f, .5f, .65f, .85f, 1f, 1.15f},
            {1f, 1f, .9f, .7f},
            {1f, 1f, 1f, 1f, .8f},
            {1f, .9f, .7f},
            {.7f, .5f, .6f}
    };
    /**
     * Абсолютная высота тона звука сцены (множитель к тону сцены); 0 или нет значения — случайная, как у остальных.
     * Сцена 16: мотив вверх-вверх-вниз и «спотыкание» четвёртой ноты; без случайности, иначе интервалы плывут.
     * Сцена 17: четыре одинаковые капли и чуть более низкая пятая; без случайности, иначе счёт «плывёт». [новое]
     */
    public static final float[][] LAB_AMBIENT_SCENE_STEP_PITCH = {
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            {.8f, 1f, .9f, .84f},
            {1f, 1f, 1f, 1f, .85f},
            {1f, .94f, 0f},
            {.85f, .8f, 0f}
    };
    /**
     * Стадия, с которой доступна сцена: шаги — присутствие (3), обрыв ритма и интерком — адресные (4–5).
     * Дом начинается раньше лаборатории (2): ночные звуки — фон, а не адресное событие.
     */
    public static final int[] LAB_AMBIENT_SCENE_MIN_STAGE = {3, 3, 3, 3, 3, 3, 3, 5, 3, 4, 3, 2, 3, 3, 4, 4, 3, 3, 3, 4};
    /** Сцена требует питания: во время «обесточено» лифт не играет (для дома всегда false). */
    public static final boolean[] LAB_AMBIENT_SCENE_POWER = {false, false, false, false, false, true, false, false, false, false, false, false, false, false, false, false, false, false, true, false};
    /** Множитель громкости и высоты тона сцены. */
    public static final float[] LAB_AMBIENT_SCENE_VOLUME = {1f, 1f, 1f, .9f, .7f, .9f, .7f, .8f, .7f, .65f, .55f, 1.2f, .9f, 1f, 1.1f, .8f, .6f, .8f, .7f, .6f};
    public static final float[] LAB_AMBIENT_SCENE_PITCH = {1f, 1f, 1f, .9f, .95f, .8f, .85f, .95f, 1.2f, .95f, .8f, .9f, .85f, .65f, .8f, .85f, 1f, 1.2f, .85f, .8f};
    /** Точка звука: смещение от игрока назад по взгляду и по высоте (блоки); 0 — над головой/под ногами. */
    public static final int[] LAB_AMBIENT_SCENE_DISTANCE = {4, 4, 4, 4, 0, 12, 5, 4, 6, 0, 4, 0, 10, 5, 2, 3, 5, 7, 10, 3};
    public static final int[] LAB_AMBIENT_SCENE_Y = {0, 0, 0, 0, 4, 0, 2, 0, 0, 4, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0};
    /** Варианты сцен по этажам 1..5; недоступные по стадии и питанию отбрасываются, прошлая сцена не повторяется. */
    public static final int[][] LAB_AMBIENT_FLOOR_SCENES = {
            {0, 6, 8}, {1, 4, 6, 8, 18}, {2, 5, 8, 18}, {3, 4, 9, 1, 16}, {7, 5, 10, 0, 2, 19}
    };

    // ---- Ночные звуки у дома [новое]: те же тихие эпизоды, но в Верхнем мире у места сна ----
    public static final int HOME_AMBIENT_MIN_STAGE = 2;
    /** Радиус вокруг самого частого места сна (блоки). */
    public static final int HOME_AMBIENT_RADIUS = 24;
    /** Ночь по времени суток: 13000..23000 тиков. */
    public static final int HOME_AMBIENT_NIGHT_FROM = 13000;
    public static final int HOME_AMBIENT_NIGHT_TO = 23000;
    /** Дом звучит чаще лаборатории: ожидание 1–2.5 мин, затем пауза 4 мин. */
    public static final int HOME_AMBIENT_DWELL_MIN = 1200;
    public static final int HOME_AMBIENT_DWELL_RANGE = 1800;
    public static final int HOME_AMBIENT_COOLDOWN = 4800;
    /** Сцены 11..17: ходьба наверху, далёкая дверь, скрип, стук в стену, шаги приближаются и обрываются, шкатулка, капли. */
    public static final int[] HOME_AMBIENT_SCENES = {11, 12, 13, 14, 15, 16, 17};

    // ---- Дом в мире снов [новое]: зеркало дома (DreamMirror) с 3-й стадии переносит дом в сон,
    // ---- и та же ночная тишина звучит уже там. Числа не должны расходиться с DreamMirrorTuning.MIN_STAGE.
    public static final int DREAM_AMBIENT_MIN_STAGE = 3;
    /** Радиус вокруг места сна в сне; зеркало захватывает 3x3 чанка, этого радиуса достаточно. */
    public static final int DREAM_AMBIENT_RADIUS = 32;
    /** Во сне реже, чем дома: ожидание 1.5–3 мин, затем пауза 5 мин. */
    public static final int DREAM_AMBIENT_DWELL_MIN = 1800;
    public static final int DREAM_AMBIENT_DWELL_RANGE = 1800;
    public static final int DREAM_AMBIENT_COOLDOWN = 6000;
    /** Те же домашние сцены 11..17: в перенесённом доме они звучат как в настоящем. */
    public static final int[] DREAM_AMBIENT_SCENES = {11, 12, 13, 14, 15, 16, 17};
    /** Сцена 10: оседает стеклянная крышка закрытой капсулы B5; звук идёт от ближайшей такой капсулы. */
    public static final int LAB_AMBIENT_CAPSULE_SCENE = 10;
    /** Сцена 19: за стеклом закрытой капсулы слышен выдох; без капсулы рядом сцена не выбирается. */
    public static final int LAB_AMBIENT_CAPSULE_BREATH_SCENE = 19;
    /** Радиус поиска закрытой капсулы для сцен 10 и 19 (блоки, по горизонтали и ±1 по высоте). */
    public static final int LAB_AMBIENT_CAPSULE_RADIUS = 12;

    // ---- Давление: временная интенсивность внутри стадии (меняет выбор и частоту, не стадию) ----
    public static final float PRESSURE_W_FATIGUE = 0.5f;
    public static final float PRESSURE_W_MADNESS = 0.35f;
    /** Вклад шприца ограничен этим значением: искусственное бодрствование не перепрыгивает стадии. */
    public static final float PRESSURE_SYRINGE_MAX = 0.3f;
    /** Сглаживание за тик: подъём 0 -> 1 примерно за 100 с, спад 1 -> 0 примерно за 200 с (сон не обнуляет сразу). */
    public static final float PRESSURE_RISE_PER_TICK = 0.0005f;
    public static final float PRESSURE_FALL_PER_TICK = 0.00025f;
    /** Вес сильных видов: x (1 + давление * это). */
    public static final float PRESSURE_STRONG_BIAS = 1.0f;

    // ---- Режиссёр (client/ObserverDirector): бюджет, один эпизод за раз, тишина ----
    public static final float DIRECTOR_BUDGET_CAP = 100f;
    public static final float DIRECTOR_BUDGET_START = 40f;
    /** Прирост бюджета за тик: база + давление (0..1) * множитель + за каждую стадию. */
    public static final float DIRECTOR_REGEN_BASE = 0.006f;
    public static final float DIRECTOR_REGEN_PER_PRESSURE = 0.010f;
    public static final float DIRECTOR_REGEN_PER_STAGE = 0.002f;
    /** Как часто режиссёр решает, предложить ли эпизод, и сколько живёт предложение (тики). */
    public static final int DECIDE_TICKS = 40;
    public static final int OFFER_TTL = 60;
    /** Система не смогла исполнить предложение (нет места, светло, нет двери): повтор этого вида не раньше (тики). */
    public static final int REFUSE_RETRY_TICKS = 400;

    /** С какой стадии доступен вид. GHOST, DOOR, SCARE, BED, STILL, GLITCH, CHASE. */
    public static final int[] EP_MIN_STAGE = {1, 4, 2, 2, 2, 2, 2};
    /** Цена из бюджета (SCARE идёт внутри эпизода GHOST и бюджет не тратит). */
    public static final float[] EP_COST = {35f, 50f, 0f, 40f, 30f, 25f, 60f};
    /** Кулдаун вида после конца эпизода (тики). У CHASE ещё и серверный счёт дней (ChaseTuning.MIN_DAYS_BETWEEN). */
    public static final int[] EP_COOLDOWN = {1800, 9600, 2400, 24000, 6000, 9600, 24000};
    /** Вес выбора режиссёром: только для предлагаемых видов (GHOST, DOOR, GLITCH, CHASE); BED и STILL просит контекст. */
    public static final int[] EP_WEIGHT = {6, 3, 0, 0, 0, 1, 2};
    /** Сильный вид (лимит сильных, долгая тишина). STILL сильный только с силуэтом (нарушение правила), SCARE — когда сработал. */
    public static final boolean[] EP_STRONG = {false, true, true, true, false, false, true};
    /** Страховка: дольше этого эпизод не держит блокировку (тики). CHASE: ожидание + бег + стук + чёрный кадр. */
    public static final int[] EP_MAX_TICKS = {3000, 1500, 60, 600, 400, 60, 1400};

    /**
     * Тишина после обычного эпизода, по стадиям 0..5 (тики). Идёт и тогда, когда игрок не устал.
     * Модель темпа (devtest DirectorPacing): при 3/2.5/2/2/1.7 мин выходило 13-16 эпизодов в час «всё время устал»
     * и около 9 в час при сне каждую ночь; это частые появления, поэтому паузы длиннее. [гипотеза]
     */
    public static final int[] SILENCE_AFTER_BY_STAGE = {0, 9600, 7200, 6000, 6000, 4800};
    /** После сильного эпизода (не меньше тишины стадии). */
    public static final int SILENCE_AFTER_STRONG = 9600;
    /** После прерывания эпизода (смена мира, сцена в кровати) и после закрытия любого экрана. */
    public static final int SILENCE_AFTER_ABORT = 600;
    /** Эпизод, прерванный после стольких активных тиков, игрок уже мог заметить (шаги, дверь, фигура):
     *  тишина и счёт затишья как у завершённого. Раньше — короткая тишина SILENCE_AFTER_ABORT [гипотеза]. */
    public static final int ABORT_NOTICED_TICKS = 40;
    public static final int SILENCE_AFTER_SCREEN = 200;
    /** После каждых CALM_EVERY эпизодов обязательная длинная пауза. */
    public static final int CALM_EVERY = 3;
    public static final int CALM_TICKS = 12000;
    /** Пробуждение (время найти след или записку), вход в игру, сцена в кровати. */
    public static final int SILENCE_AFTER_WAKE = 2400;
    public static final int SILENCE_AT_START = 1200;
    public static final int SILENCE_AFTER_SCENE = 2400;
    /** Вход в мир снов: тишина не дольше этого (сон длится DreamTuning.DURATION_TICKS). */
    public static final int DREAM_SILENCE_CAP = 1200;
    /** Не больше STRONG_PER_WINDOW сильных событий за STRONG_WINDOW_TICKS. */
    public static final int STRONG_WINDOW_TICKS = 12000;
    public static final int[] STRONG_PER_WINDOW_BY_STAGE = {0, 0, 1, 1, 1, 2};

    // ---- Фигура в реальном мире (эпизод GHOST) ----
    /** Появляется ВНЕ поля зрения: на столько градусов за краем поля зрения (плюс разброс). */
    public static final float SPAWN_OUTSIDE_VIEW_MARGIN = 12f;
    public static final float SPAWN_ANGLE_SPREAD = 40f;
    /** Дистанция появления по стадиям: ближе с ростом стадии, давление её не меняет. */
    public static final float[] SPAWN_DISTANCE_MIN_BY_STAGE = {24f, 24f, 18f, 18f, 16f, 16f};
    public static final float[] SPAWN_DISTANCE_MAX_BY_STAGE = {34f, 34f, 32f, 30f, 28f, 28f};
    /** Доля реального поля зрения (FOV и размер окна), внутри которой считается, что игрок его видит. */
    public static final float VIEW_MARGIN = 0.95f;
    /** Место появления должно быть за краем кадра с запасом (доля поля зрения): фигура не возникает на глазах. */
    public static final float SPAWN_VIEW_MARGIN = 1.15f;
    /**
     * Правило взгляда: сколько тиков смотреть, чтобы он исчез, по стадиям 0..5. Исчезает на всех стадиях (и во сне,
     * и в погоне), но чем выше стадия, тем дольше его надо выдержать: 0.2 с -> 2.2 с. Взгляд в сторону отнимает по тику.
     * Из укрытия — ещё COVER_GAZE_TICKS_BONUS. [гипотеза]
     */
    public static final int[] GAZE_TICKS_BY_STAGE = {4, 4, 8, 16, 28, 44};

    public static int gazeTicks(int stage) {
        return GAZE_TICKS_BY_STAGE[Math.max(0, Math.min(GAZE_TICKS_BY_STAGE.length - 1, stage))];
    }
    /** Исчезает, если игрок подошёл ближе / ушёл дальше. */
    public static final double NEAR_VANISH = 6.0;
    public static final double FAR_VANISH = 56.0;
    /** Время жизни незамеченной фигуры (тики). */
    public static final int LIFETIME = 600;

    // ---- Частицы при исчезновении ----
    public static final int VANISH_DUST_COUNT = 60;
    public static final int VANISH_INK_COUNT = 20;

    // ---- Шаги ближе, пока игрок не смотрит (только реальный мир, только не из укрытия) ----
    public static final int APPROACH_MIN_STAGE = 2;
    public static final int STEP_COOLDOWN_MIN = 60;
    public static final int STEP_COOLDOWN_MAX = 120;
    public static final float STEP_DISTANCE_MIN = 1.5f;
    public static final float STEP_DISTANCE_MAX = 3.0f;
    /** Ближе этой дистанции не подходит, по стадиям. */
    public static final float[] APPROACH_LIMIT_BY_STAGE = {99f, 99f, 14f, 12f, 10f, 10f};

    // ---- Слабый признак перед фигурой: одиночный шаг с места, где она встанет ----
    public static final int SIGN_MIN_STAGE = 2;
    public static final float SIGN_CHANCE = 0.4f;
    /** После признака продолжения нет: база; давление уменьшает её до половины. */
    public static final float NO_CONTINUE_CHANCE = 0.4f;
    /** Доля NO_CONTINUE_CHANCE, которую снимает полное давление (1 = продолжение всегда при давлении 1). */
    public static final float NO_CONTINUE_PRESSURE_CUT = 0.5f;
    public static final int SIGN_DELAY_MIN = 30;
    public static final int SIGN_DELAY_MAX = 60;
    /** Если место признака всё это время в поле зрения, фигура не появляется (эпизод кончается признаком), тики. */
    public static final int SIGN_WAIT_MAX = 200;
    public static final float SIGN_VOLUME = 0.35f;
    /** Шаг слышен в стороне места, но не дальше этого (ванильный шаг затухает к 16 блокам). [проверить на слух] */
    public static final float SIGN_SOUND_DIST = 8f;

    // ---- Нарушение выученного правила (стадия 4+): силуэт за спиной в эпизоде неподвижности (STILL) ----
    // Обычную фигуру взгляд прогоняет всегда (GAZE_TICKS_BY_STAGE); «стоит под взглядом и не исчезает» больше нет.
    public static final int RULE_MIN_STAGE = 4;
    public static final float[] RULE_CHANCE_BY_STAGE = {0f, 0f, 0f, 0f, 0.25f, 0.35f};
    /** Одно нарушение на столько тиков. */
    public static final int RULE_COOLDOWN = 24000;

    // ---- Мир снов ----
    public static final double DREAM_APPROACH_SPEED = 0.05;       // блоков за тик
    public static final double DREAM_APPROACH_PER_PRESSURE = 0.03;
    public static final double DREAM_STOP_DISTANCE = 3.0;
    /** Во сне потолок не мешает: хватает двух свободных блоков. */
    public static final int DREAM_HEADROOM = 2;
    /** С какой стадии он идёт к игроку, пока на него смотрят (и всё равно исчезает через gazeTicks). */
    public static final int DREAM_APPROACH_MIN_STAGE = 4;
    /** Кадр-сюрприз: только фаза реакции внутри эпизода GHOST во сне, если её заложил план. */
    public static final int SCARE_MIN_STAGE = 2;
    public static final float[] SCARE_PLAN_CHANCE_BY_STAGE = {0f, 0f, 0.3f, 0.3f, 0.4f, 0.5f};
    public static final int SCARE_TICKS = 8;
    public static final int SCARE_COOLDOWN_TICKS = 100;
    /** С какой стадии строки кадра переходят от вопросов к обвинению. */
    public static final int SCARE_ACCUSE_STAGE = 5;

    // ---- Осколок у кровати (Верхний мир, пока игрок засыпает) ----
    /** На каком тике лежания он может появиться (после тика сцены TRIGGER_TICK=30, чтобы не мешать ей). */
    public static final int BED_WATCH_DELAY_TICKS = 45;
    /** Шанс за одно засыпание: база + за каждую стадию выше минимальной (плюс кулдаун EP_COOLDOWN[BED]). */
    public static final float BED_WATCH_CHANCE_BASE = 0.3f;
    public static final float BED_WATCH_CHANCE_PER_STAGE = 0.05f;
    /**
     * Кровать — контекст раз за ночь: общую тишину и бюджет запрос BED не ждёт (иначе фигура днём почти всегда
     * «съедает» вечерний взгляд у кровати), но после конца прошлого эпизода должно пройти столько тиков.
     */
    public static final int BED_MIN_GAP_TICKS = 2400;
    public static final float BED_WATCH_DISTANCE_MIN = 2.6f;
    public static final float BED_WATCH_DISTANCE_MAX = 3.4f;
    public static final int DEATH_WATCH_MIN_STAGE = 5;
    /** Медленно подкрадывается к кровати (блоков за тик), ближе этой дистанции не идёт. */
    public static final double BED_WATCH_CREEP_SPEED = 0.04;
    public static final double BED_WATCH_STOP_DISTANCE = 1.8;
    /** Ближе этой дистанции наклоняется над спящим (поза приседа): проверяет, дышит ли он. */
    public static final double BED_WATCH_LEAN_DISTANCE = 2.2;
    /** У кровати хватает двух свободных блоков: в низкой комнате он наклоняется. */
    public static final int BED_WATCH_HEADROOM = 2;
    /** Разброс от оси кровати (градусы в обе стороны): узкий, чтобы он был в центре кадра. */
    public static final float BED_WATCH_SPREAD_DEG = 12f;
    /** Минимальная освещённость его модели (0..15), чтобы силуэт читался в темноте. */
    public static final int BED_WATCH_MIN_LIGHT = 7;

    // ---- Неподвижность (ImmobilityClient): 20 с без ввода -> тишина мира, шаг за спиной ----
    public static final int STILL_TICKS = 400;
    /** Шанс, что неподвижность станет эпизодом (остальное: ничего). */
    public static final float STILL_CHANCE = 0.5f;
    /** Длительность тишины мира в начале эпизода (тики). Глушатся только звуки мира, не музыка и не интерфейс. */
    public static final int STILL_SILENCE_TICKS = 40;
    /** Пауза после шага до появления силуэта (тики). Силуэт только как нарушение правила (стадия 4+). */
    public static final int STILL_SPAWN_DELAY = 5;
    /** Сколько тиков силуэт стоит за спиной, если его не выдержать взглядом (под взглядом исчезает через gazeTicks). */
    public static final int STILL_VISIBLE_TICKS = 60;
    /** Разброс направления «сзади» (градусы): осколок встаёт там, где был шаг. */
    public static final float STILL_BEHIND_SPREAD_DEG = 15f;
    public static final float STILL_DISTANCE_MIN = 5f;
    public static final float STILL_DISTANCE_MAX = 12f;

    // ---- Дверь (стадия 4): зацикленное «открыл-закрыл» за спиной, дверь в мире не меняется ----
    /** Как часто проверять, есть ли рядом дверь (для решения режиссёра), тики. */
    // Дверь дома: реальное открывание, а не только звук [новое].
    public static final int DOOR_EPISODE_MIN_CYCLES = 4;
    public static final int DOOR_EPISODE_MAX_CYCLES = 6;
    public static final int DOOR_EPISODE_INITIAL_REPLY = 100;
    public static final int DOOR_EPISODE_MIN_REPLY = 35;
    public static final int DOOR_EPISODE_REPLY_STEP = 12;
    public static final int DOOR_EPISODE_GAP = 24;
    public static final int DOOR_EPISODE_MAX_DISTANCE = 16;
    public static final int DOOR_EPISODE_REQUEST_COOLDOWN = 1200;
    public static final int DOOR_EPISODE_ACK_TIMEOUT = 100;
    public static final int DOOR_CHECK_TICKS = 200;
    public static final int DOOR_RADIUS = 10;
    public static final double DOOR_MIN_DIST = 4.0;
    /** Дверь не берём, если она в передней полусфере взгляда (косинус угла выше этого значения). */
    public static final double DOOR_MAX_LOOK_DOT = 0.3;
    /** Серия обрывается, если игрок посмотрел на дверь (косинус угла выше этого значения, 0.8 ≈ 37°). */
    public static final double DOOR_STOP_LOOK_DOT = 0.8;
    public static final int DOOR_CYCLES_MIN = 3;
    public static final int DOOR_CYCLES_MAX = 5;
    /** Паузы выбираются один раз на эпизод: каждый цикл звучит одинаково, как заученное действие. */
    public static final int DOOR_OPEN_GAP_MIN = 10;
    public static final int DOOR_OPEN_GAP_MAX = 16;
    public static final int DOOR_CLOSE_GAP_MIN = 18;
    public static final int DOOR_CLOSE_GAP_MAX = 26;
    public static final float DOOR_VOLUME = 0.7f;
    /** Стадия 5: двери ближе этого к месту сна получают вес x DOOR_HOME_WEIGHT. */
    public static final int DOOR_HOME_RADIUS = 12;
    public static final int DOOR_HOME_WEIGHT = 4;

    // ---- Сбой экспозиции (GlitchClient/GlitchOverlay): эпизод режиссёра, слабый признак ----
    public static final int GLITCH_TICKS_MIN = 6;
    public static final int GLITCH_TICKS_MAX = 10;

    // ---- Память привычек: сервер ведёт (ObserverProgressData), клиент читает копию (ObserverState) ----
    /** Точек маршрута: опрос раз в ROUTE_SAMPLE_TICKS, ближе ROUTE_MERGE_DIST сливаются (вес +1), раз в день вес x3/4. */
    public static final int ROUTE_SLOTS = 8;
    public static final int ROUTE_SAMPLE_TICKS = 400;
    public static final int ROUTE_MERGE_DIST = 10;
    public static final int ROUTE_WEIGHT_MAX = 60;
    /** Мест сна (кровати Верхнего мира): ближе SLEEP_MERGE_DIST сливаются. */
    public static final int SLEEP_SLOTS = 3;
    public static final int SLEEP_MERGE_DIST = 4;
    public static final int SLEEP_COUNT_MAX = 30;
    /** Доля эпизодов GHOST, которые ищут место у знакомой точки маршрута, по стадиям. */
    public static final float[] ANCHOR_CHANCE_BY_STAGE = {0f, 0.35f, 0.5f, 0.5f, 0.5f, 0.6f};
    /** Ближе этого знакомая точка не годится (по стадиям): на стадии 1 фигура остаётся далёкой. */
    public static final float[] ANCHOR_DIST_MIN_BY_STAGE = {12f, 22f, 12f, 12f, 12f, 12f};
    public static final float ANCHOR_DIST_MAX = 40f;
    /** Радиус поиска укрытия или места вокруг точки-якоря. */
    public static final int ANCHOR_SEARCH_RADIUS = 4;
    /** Стадия 2+: рядом с местом сна фигура чаще у окна. */
    public static final float HOME_RADIUS = 24f;
    public static final float HOME_WINDOW_SHARE = 0.75f;
    /** Стадия 5: точки на пути домой (ближе этого к отрезку «игрок — место сна») получают вес x HOME_PATH_WEIGHT. */
    public static final float HOME_PATH_DIST = 8f;
    public static final float HOME_PATH_WEIGHT = 3f;
    /** Недавние места наблюдения (только клиент, до выхода из мира): их вес снижается. */
    public static final int RECENT_SPOTS = 4;
    public static final float RECENT_SPOT_RADIUS = 12f;
    public static final int RECENT_SPOT_TICKS = 36000;
    public static final float RECENT_SPOT_WEIGHT = 0.2f;
    /** Стадия 5: узнавание — эпизод возвращается к прежнему месту наблюдения. */
    public static final int RECOGNITION_MIN_STAGE = 5;
    public static final float RECOGNITION_CHANCE = 0.35f;
    /**
     * Места, где игрок прогнал фигуру взглядом (сервер, ObserverProgressData.seen): после сна там могут остаться её шаги
     * (TraceTuning.SEEN_*). Хранится SEEN_SLOTS последних мест, старше SEEN_MAX_AGE_DAYS дней забываются.
     * Клиент сообщает место не чаще SEEN_MIN_GAP_TICKS и не дальше SEEN_MAX_DIST блоков от игрока. [гипотеза]
     */
    public static final int SEEN_SLOTS = 2;
    public static final int SEEN_MAX_AGE_DAYS = 2;
    public static final int SEEN_MIN_GAP_TICKS = 1200;
    public static final int SEEN_MAX_DIST = 64;

    // ---- Записки (ObserverNotesHandler) ----
    /** Id страниц из DocumentDefs, выдаются по порядку, каждая один раз. 3-4 = половинки «Двух сторон» (слой 2). */
    public static final String[] NOTE_PAGES = {"note_l1_0", "note_l1_1", "note_l1_2", "note_l2_a", "note_l2_b", "note_l3"};
    /** Минимальная стадия для каждой страницы (по индексу NOTE_PAGES). */
    public static final int[] NOTE_PAGE_MIN_STAGE = {3, 3, 3, 4, 4, 5};
    public static final int NOTE_MIN_STAGE = 3;
    /** Шанс записки за один сон. */
    public static final float NOTE_CHANCE = 0.5f;
    /** Порядок как в ObserverNotesHandler.Way: BED, CONTAINER, WINDOW, INVENTORY, DREAM. */
    public static final int[] NOTE_WAY_MIN_STAGE = {3, 3, 3, 4, 4};
    public static final int[] NOTE_WAY_WEIGHT = {3, 2, 2, 2, 2};
    /** Число способов, доступных для отложенной выдачи (BED, CONTAINER, WINDOW). */
    public static final int NOTE_DEFERRED_WAYS = 3;
    /** Интервал повторной попытки отложенной выдачи (тики). */
    public static final int NOTE_RETRY_TICKS = 200;
    public static final int NOTE_BED_RADIUS = 3;
    public static final int NOTE_CONTAINER_RADIUS = 8;
    public static final int NOTE_WINDOW_RADIUS = 10;
    /** Записка на кровати: сколько тиков её нельзя подобрать, чтобы игрок успел её увидеть. */
    public static final int NOTE_BED_PICKUP_DELAY = 200;
    public static final int NOTE_DREAM_PICKUP_DELAY = 20;
    public static final float NOTE_DREAM_DIST_MIN = 4f;
    public static final float NOTE_DREAM_DIST_MAX = 8f;

    // ---- Рисунки у дома по подсказкам записок слоя 1 (ObserverNotesHandler.leaveHint) ----
    /**
     * Рисунок (DrawingTuning, индекс) для записки k = 0..2 из NOTE_PAGES: WINDOW — дом с закрашенным окном (на стекле),
     * BEHIND THE DOOR — птица в открытой клетке (за дверью), UNDER THE BED — «папа спит» (у кровати).
     * Кладётся утром после ночи, когда записка уже была выдана; за одно утро одна находка (записка, рисунок или подарок).
     */
    public static final int[] HINT_DRAWINGS = {0, 3, 1};
    /** Ближайшая дверь к месту сна ищется в таком радиусе (по высоте +-2). */
    public static final int HINT_DOOR_RADIUS = 10;
    /** Рисунок не кладётся, если такой же уже лежит (предмет или рамка) ближе стольких блоков от места сна. */
    public static final double HINT_LOOK_RADIUS = 16.0;
    /** Рисунок на полу: сколько тиков его нельзя подобрать (как записку на кровати: игрок должен его увидеть). */
    public static final int HINT_PICKUP_DELAY = 200;
    /** «Под кроватью»: сдвиг листа от центра соседней клетки к раме кровати (0.5 минус половина предмета и зазор). */
    public static final double HINT_BED_TUCK = 0.36;

    // ---- Подарок воспоминания (канон, пункт 8: «осколок подарит его, сундук или у кровати, после нескольких ночей») ----
    /** Столько пробуждений подряд без нового воспоминания — и Фрагмент оставляет одно из ненайденных. */
    public static final int GIFT_NIGHTS = 4;
    /** Не раньше этой стадии (как записки). */
    public static final int GIFT_MIN_STAGE = 3;
    /** Шанс, что подарок сначала ищет сундук или бочку (NOTE_CONTAINER_RADIUS); иначе сначала кровать. Не вышло одно — другое. */
    public static final float GIFT_CONTAINER_CHANCE = 0.5f;

    // ---- Образ осколка: тёмная масса с пропорциями игрока ----
    public static final float GHOST_SCALE_XZ = 1.15f;
    public static final float GHOST_SCALE_Y = 1.4f;
    /** Сколько свободных блоков над полом нужно фигуре (рост 1.8 x GHOST_SCALE_Y = 2.52). */
    public static final int GHOST_HEADROOM = 3;
    /** «Плывущие края»: амплитуда и скорость (рад/тик) пульсации ширины. */
    public static final float GHOST_WOBBLE = 0.05f;
    public static final float GHOST_WOBBLE_SPEED = 0.13f;
    /** Эхо: разреженная копия, дрейфует вбок на столько блоков, скорость рад/тик. */
    public static final float ECHO_OFFSET = 0.22f;
    public static final float ECHO_SPEED = 0.05f;
    /** Человеческие глаза видны, если ближе этой дистанции (в мире снов всегда). */
    public static final double EYES_DISTANCE = 9.0;
    /** Глаза на лице (блоки, до масштаба фигуры): ширина, разнос, ось поворота головы, высота глаз, вынос перед лицом. */
    public static final float FACE_EYE_W = 0.18f;
    public static final float FACE_EYE_DX = 0.12f;
    public static final float FACE_PIVOT_Y = 1.41f;
    public static final float FACE_EYE_Y = 1.62f;
    public static final float FACE_Z = 0.24f;
    /** Поза наклона (присед): насколько опускается и выдвигается голова (блоки, до масштаба фигуры). [проверить на глаз] */
    public static final float FACE_CROUCH_DROP = 0.39f;
    public static final float FACE_CROUCH_FORWARD = 0.0f;

    // ---- Дыхание (BreathClient): пар изо рта ночью/на морозе ----
    /** Перерыв между выдохами (тики). 60-160 = 3-8 с, как у спокойного дыхания. */
    public static final int BREATH_INTERVAL_MIN = 60;
    public static final int BREATH_INTERVAL_MAX = 160;
    /** Базовая температура биома ниже этой = «мороз», пар виден и днём. */
    public static final float BREATH_COLD_TEMP = 0.15f;
    /** Высота (Y) выше которой всегда холодно. */
    public static final int BREATH_COLD_Y = 120;
    /** Звук breath_exhale - это вдох и выдох; выдох начинается через столько тиков после начала звука
     *  (0.50 с = EXHALE_AT в tools/make_breath_sound.py). В этот момент изо рта идёт пар. */
    public static final int BREATH_INHALE_TICKS = 10;
    /** Струя пара: сколько тиков идёт (0.8 с) и частиц за тик в первой половине (во второй - вдвое меньше). */
    public static final int BREATH_PUFF_TICKS = 16;
    public static final int BREATH_PUFF_PER_TICK = 2;
    /** Скорость струи изо рта (блоков/тик) в начале выдоха; к концу падает до 35 %. Струя идёт вперёд и вниз
     *  (DOWN - доля скорости вниз): пар остаётся в нижней трети экрана, уходит на ~1 блок и тает. */
    public static final double BREATH_PUFF_SPEED = 0.065;
    public static final double BREATH_PUFF_DOWN = 0.35;
    /** Рот: вперёд от глаз и вниз (блоки). Ближе 0.3 частица лезет в камеру. */
    public static final double BREATH_MOUTH_FORWARD = 0.32;
    public static final double BREATH_MOUTH_DOWN = 0.13;
    /** Частица пара (client/BreathParticle): жизнь (тики), размер в начале и в конце (блоки), непрозрачность. */
    public static final int BREATH_LIFE_MIN = 22;
    public static final int BREATH_LIFE_MAX = 34;
    public static final float BREATH_SIZE_START = 0.05f;
    public static final float BREATH_SIZE_END = 0.24f;
    public static final float BREATH_ALPHA = 0.30f;
    /** Торможение пара за тик и подъём тёплого пара (блоков/тик за тик). */
    public static final float BREATH_FRICTION = 0.91f;
    public static final double BREATH_RISE = 0.0006;
    /** Ближе этого к камере пар тает (не вспыхивает белым, когда идёшь сквозь него). */
    public static final double BREATH_NEAR_FADE = 0.45;
    /** Пар не темнее этого света блока (0-15): ночью виден, но не светится. */
    public static final int BREATH_MIN_LIGHT = 5;
    /** Звук: громкость, питч и его случайный разброс (питч сдвигает и начало выдоха - пар подстраивается). */
    public static final float BREATH_SOUND_VOLUME = 0.30f;
    public static final float BREATH_SOUND_PITCH = 1.00f;
    public static final float BREATH_SOUND_PITCH_SPREAD = 0.05f;

    // ---- Укрытия при появлении (client/ObserverCover) ----
    /** Доля появлений в укрытии (за деревом или за стеклом), по стадиям; на стадии 1 только укрытие. */
    public static final float[] COVER_CHANCE_BY_STAGE = {0f, 1f, 0.85f, 0.8f, 0.75f, 0.75f};
    /** Из укрытий: доля попыток сначала искать окно (нет окна, тогда дерево). */
    public static final float COVER_WINDOW_SHARE = 0.4f;
    public static final int COVER_ATTEMPTS = 12;
    public static final int TREE_SEARCH_RADIUS = 3;
    /** Встаёт за ствол от игрока и вбок от центра ствола, так что виден край фигуры. */
    public static final float TREE_PEEK_BEHIND = 0.55f;
    public static final float TREE_PEEK_SIDE = 0.62f;
    public static final int WINDOW_RADIUS = 20;
    /** Ближняя граница для окна по стадиям: на стадии 1 только дальние окна. */
    public static final float[] WINDOW_DIST_MIN_BY_STAGE = {14f, 14f, 8f, 8f, 8f, 8f};
    public static final float WINDOW_DIST_MAX = 20f;
    /** Насколько вплотную к стеклу он стоит (блоков). */
    public static final float WINDOW_GLASS_GAP = 0.15f;
    /** Из укрытия наблюдает дольше и исчезает от взгляда чуть позже. */
    public static final float COVER_LIFETIME_MULT = 1.6f;
    public static final int COVER_GAZE_TICKS_BONUS = 6;
}
