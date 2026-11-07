package com.hypersomnia.lab;

/**
 * Числа лаборатории. Остановки лифта (stop): 0 = поверхность (павильон), 1..5 = этажи B1..B5.
 * Чертежи: data/hypersomnia/lab/{real|dream}_{g|b1..b5}.txt; этаж без чертежа не строится.
 */
public final class LabTuning {
    private LabTuning() {}

    public static final int STOPS = 6;

    // ---- Вертикаль ----
    /** Y пола B1 (первый блок воздуха над полом). Дальше вниз с шагом PITCH. */
    public static final int B1_Y = 20;
    public static final int PITCH = 8;

    public static int floorY(int stop) {
        return B1_Y - (stop - 1) * PITCH;
    }

    public static String stopLabel(int stop) {
        return stop == 0 ? "G" : "B" + stop;
    }

    public static String blueprintName(boolean dream, int stop) {
        return (dream ? "dream_" : "real_") + (stop == 0 ? "g" : "b" + stop);
    }

    // ---- Место и момент стройки ----
    public static final int DIST_MIN = 300;
    public static final int DIST_MAX = 500;
    public static final int BUILD_RADIUS = 160;
    public static final int CHECK_EVERY = 100;
    /** Кандидатов на проход (проходов два: второй с более широким кольцом). */
    public static final int ATTEMPTS = 96;
    public static final long SEED_SALT = 0x4C4142_0047L;

    // ---- Поиск места (по рельефу генератора, относительно клетки '@') ----
    /** Площадка выборки: x -12..12, z -8..14 с шагом 4 (накрывает павильон и двор). */
    public static final int SAMPLE_X1 = -12;
    public static final int SAMPLE_X2 = 12;
    public static final int SAMPLE_Z1 = -8;
    public static final int SAMPLE_Z2 = 14;
    public static final int SAMPLE_STEP = 4;
    /** Берём сразу, если перепад высот не больше SPREAD_OK; иначе лучший из прошедших, но не хуже SPREAD_MAX. */
    public static final int SPREAD_OK = 2;
    public static final int SPREAD_MAX = 5;
    /** Кандидат с перепадом больше SPREAD_REJECT отбрасывается сразу. */
    public static final int SPREAD_REJECT = 12;
    /** Допустимая высота земли над уровнем моря. */
    public static final int MIN_ABOVE_SEA = 3;
    public static final int MAX_ABOVE_SEA = 40;
    /** Не ставим лабораторию ближе этого расстояния к деревне (блоки). */
    public static final int VILLAGE_CLEAR_BLOCKS = 128;

    // ---- Подготовка площадки поверхности ----
    /** Мощёный двор вокруг здания (блоки). */
    public static final int YARD = 3;
    /** Уровень двора — этот процентиль высот площадки (0.7 = ближе к верхней стороне, меньше выкапываем). */
    public static final double PAD_PERCENTILE = 0.7;
    /** Глубина подсыпки под площадкой. */
    public static final int FILL_DEPTH = 24;
    /** Длина входной дорожки за подпорной стенкой (блоки), по 1 блоку подъёма/спуска на шаг. */
    public static final int ENTRY_PATH = 6;
    /** Подпорная стенка поднимается над полом не выше этого (блоки); выше остаётся естественный срез рельефа. */
    public static final int WALL_MAX_ABOVE = 1;

    // ---- Замки ----
    public static final int CODE_DIGITS = 4;
    public static final int KEYPAD_MAX_DIST = 6;
    public static final int KEYPAD_DENIED_TICKS = 30;
    public static final int KEYPAD_GRANTED_TICKS = 20;
    public static final int LOCK_HATCH = 1;

    /**
     * Шифр 6 (реальный B1, Power Room): панель RECORDER 47-OP у щита MAIN FEED и запечатанный ящик оператора рядом
     * (строка seal чертежа). Ответ свой в каждом мире: LabLocks.indexCode.
     */
    public static final int LOCK_INDEX = 2;
    /** Карточка со строками индекса (DocumentDefs, элементы Row). Её id — ключ порядка строк (WorldVariant). */
    public static final String INDEX_CARD = "op_index";
    /**
     * Цифра каждой строки карточки по порядку элементов Row (= воспоминания 1..7): капсула 47, возраст 8, пункт 4,
     * таймкод 03:12, 47-A, 03:07, «четыре раза». Меняется только вместе с текстами карточки и воспоминаний.
     */
    public static final int[] INDEX_DIGITS = {7, 8, 4, 2, 7, 7, 4};

    /**
     * Входной замок (LOCK_HATCH): код = время записи журнала GATE_G1.LOG, на которую указывает письмо g_keypad.
     * Правило письма по зерну мира (LabVariants.hatchRule, ключ HATCH_KEY), времена журнала не меняются:
     * 0 последний вход (02:41), 1 первый вход после 20:00 (20:56), 2 вход дежурного оператора (21:30),
     * 3 последний вход до полуночи (23:40). Тексты правил — ArchiveDefs.G_KEYPAD_RULES и lang g_keypad.1.v<n>.
     * В мире снов журнал другой (все записи 03:13), там код всегда HATCH_DREAM_CODE.
     */
    public static final String HATCH_KEY = "gate_rule";
    public static final int[] HATCH_CODES = {241, 2056, 2130, 2340};
    public static final int HATCH_DREAM_CODE = 313;

    /**
     * Шифр 4 «Две стороны» (мир снов, B1): приказ bay_transfer даёт время перевода (LabVariants.bayTime, минуты от полуночи),
     * сложенные половинки записки — правило «the far door reads backwards»: код решётки блока 47-48 = цифры времени задом
     * наперёд (LabVariants.bayCode). Время-палиндром (03:30, 04:40, 05:50) сдвигается на минуту, иначе правило ничего не меняет.
     */
    public static final int LOCK_BAY = 3;
    public static final String BAY_KEY = "bay_time";
    public static final int BAY_MIN = 3 * 60 + 20;
    public static final int BAY_MAX = 5 * 60 + 50;

    /**
     * Шифр 5 «Куда смотрят» (мир снов, блок 47-48): план зала наблюдения plan_l5 — камеры 01..04 с линиями взгляда и
     * пронумерованные посты на стенах (LabVariants.posts, ключ POSTS_KEY). По бумаге код = посты на концах линий;
     * записка note_l3: «down here they look the other way» — код = посты за спиной камер, камеры 01..04 по порядку.
     * Сетка плана POSTS_W x POSTS_H клеток, посты в узлах стен (не в углах), направления камер — 8 румбов.
     */
    public static final int LOCK_NURSERY = 4;
    public static final String POSTS_KEY = "obs_posts";
    public static final int POSTS_W = 10;
    public static final int POSTS_H = 6;
    public static final int POSTS_CAMERAS = 4;
    /** Постов на плане: по два на камеру (спереди и сзади) и ложные до этого числа, номера — разные цифры 0-9. */
    public static final int POSTS_TOTAL = 10;
    /** Посты не ближе стольких клеток друг к другу (евклидово), чтобы номер читался однозначно. */
    public static final double POSTS_MIN_GAP = 2.0;
    /** Линия камеры не короче стольких клеток в обе стороны (камера не вплотную к стене). */
    public static final int POSTS_MIN_RAY = 2;
    /** Попыток раскладки на одну случайную последовательность зерна; не вышло — следующая последовательность того же зерна. */
    public static final int POSTS_ATTEMPTS = 2000;

    /** Форма 12-B: слово контрольной передачи (Морзе и знаки) по зерну. 0 = прежнее. Другие языки: lang hypersomnia.variant.morse.<n>. */
    public static final String MORSE_KEY = "morse_word";

    /**
     * Финал, секрет «Subject 48» (мир снов, блок 47-48): панель CRADLE / SESSION CONTROL у капсулы 47. Код — номер следующего
     * сеанса (FinaleTuning.SESSION_CODE): шприц sedation_47 велит «confirm the number on the bay panel», табличка у капсулы 48.
     */
    public static final int LOCK_SESSION = 5;
    public static final String[] MORSE_WORDS = {"HELP", "WAKE", "LATE", "STAY"};

    // ---- Доступ к этажам B3-B5: панели LIFT ACCESS справа от двери лифта (шифры 7-9, только реальный мир) ----
    /** B2 -> B3. Шифр 7: пустые места в рядах хранилища и лист проверки копии B2-CHK (LabVariants.stacksCode). */
    public static final int LOCK_STACKS = 6;
    /**
     * B3 -> B4. Шифр 8: кода нет. Буферы стоек, удержанные при потере питания, уходят на ретранслятор через антенну ANT-1 на
     * крыше павильона: тарелку поворачивают кареткой на азимут ретранслятора из BUFFER.LOG (на 180° от нынешнего), и панель
     * открывается сама (LabAntenna). Без MAIN FEED несущей нет.
     */
    public static final int LOCK_BUFFER = 7;
    /** B4 -> B5. Шифр 9: код директора, который выстукивают из палаты 2D во сне (LabVariants.knockCode). */
    public static final int LOCK_WARD = 8;
    /** Замки доступа по этажам: ACCESS_LOCKS[i] открывает остановку FEED_REACHED + 1 + i (LabLocks.accessTop). */
    public static final int[] ACCESS_LOCKS = {LOCK_STACKS, LOCK_BUFFER, LOCK_WARD};

    public static boolean accessLock(int lock) {
        for (int a : ACCESS_LOCKS) if (a == lock) return true;
        return false;
    }

    // ---- Шифр 7: хранилище B2 ----
    /** Ряды хранилища (таблички ROW 1..) и места в ряду (1 — у таблички). Мест — чертёж: строка stack. */
    public static final int STACK_ROWS = 4;
    public static final int STACK_PLACES = 8;
    /** Пустое место каждого ряда (коробки копии 03:13 не вернули) и цифры листа B2-CHK. */
    public static final String STACKS_KEY = "stacks_copy";
    public static final String STACKS_SHEET_KEY = "stacks_sheet";

    // ---- Серверная B3 (ServerRackBlock, LabFixtures): удержанные буферы в BUFFER.LOG и на лампах, до передачи через ANT-1 ----
    public static final int RACKS = 12;
    /** Сколько буферов SOURCE 47 удержано (строки HOLD журнала); ещё один удержан для копии B2. */
    public static final int RACK_HELD = 4;
    public static final String RACK_KEY = "rack_buffer";
    public static final String RACK_LAMPS_KEY = "rack_lamps";
    /** Свет верхней половины стойки с горящей лампой состояния, 0-15. */
    public static final int RACK_LIGHT = 3;

    // ---- Шифр 8: антенна ANT-1 на крыше павильона (только реальный мир; LabAntenna, AntennaDriveBlock) ----
    /** Один оборот штурвала каретки (ПКМ — по часовой стрелке, с Shift — назад), градусы. Азимуты тарелки кратны шагу. */
    public static final int ANTENNA_STEP = 15;
    /** Поворот на шаг длится столько тиков (у клиента плавно: teleport_duration дисплеев); раньше штурвал не берётся. */
    public static final int ANTENNA_TURN_TICKS = 12;
    /** Тарелка стоит на азимуте ретранслятора без поворотов столько тиков при MAIN FEED -> несущая, буферы ушли. */
    public static final int ANTENNA_LOCK_TICKS = 60;
    /** Раз в столько тиков: перестройка крыши старого мира, проверка дисплеев тарелки, проверка несущей. */
    public static final int ANTENNA_SCAN_TICKS = 10;
    /** Зерно мира: азимут, на который тарелку отвернули вручную в 03:13 (ретранслятор — на 180° от него). */
    public static final String ANTENNA_KEY = "antenna_slew";
    /** Блоков мачты (ограда из полированного сланца) над кареткой; ось поворота тарелки — на верхушке мачты. */
    public static final int ANTENNA_MAST = 3;
    /** Угол места тарелки, градусы (наклон чаши вверх от горизонта). */
    public static final float ANTENNA_ELEVATION = 25f;
    /** Глубина чаши: z = k * r^2 (облучатель в фокусе 1 / (4k)). */
    public static final float ANTENNA_DEPTH = 0.16f;
    /** Центр чаши над осью поворота и вперёд от неё, блоки. */
    public static final float ANTENNA_HUB_UP = 0.95f;
    public static final float ANTENNA_HUB_FORWARD = 0.12f;
    /** Дальность видимости дисплеев тарелки (1 = 64 блока). */
    public static final float ANTENNA_VIEW_RANGE = 2.0f;
    /** Сообщение о несущей видят игроки ближе стольких блоков к каретке. */
    public static final int ANTENNA_MESSAGE_RADIUS = 32;
    /** Скрип каретки (ванильное точило) и сигнал несущей (маяк): громкость и высота. [проверить на слух] */
    public static final float ANTENNA_SOUND_VOLUME = 0.7f;
    public static final float ANTENNA_SOUND_PITCH = 0.55f;
    public static final float ANTENNA_CARRIER_PITCH = 0.7f;

    // ---- Шифр 9: стук из палаты 2D (мир снов, B4; LabKnocks) ----
    public static final String KNOCK_KEY = "ward_knock";
    /** Стук слышен, пока игрок ближе этого к заложенной двери (блоки). */
    public static final int KNOCK_RADIUS = 7;
    /** Раз в столько тиков проверяется, есть ли кто-то рядом. */
    public static final int KNOCK_SCAN_TICKS = 10;
    /** Тишина после того, как игрок подошёл, перед первым ударом. */
    public static final int KNOCK_START_TICKS = 40;
    /** Между ударами одного числа; между рядом и столбцом; между цифрами; после четырёх цифр до повтора. */
    public static final int KNOCK_GAP_TICKS = 8;
    public static final int KNOCK_PAUSE_TICKS = 22;
    public static final int KNOCK_DIGIT_TICKS = 56;
    public static final int KNOCK_REPEAT_TICKS = 240;
    /** Громкость и высота удара (lab_knock: ванильный звук дерева, ниже обычного). [проверить на слух] */
    public static final float KNOCK_VOLUME = 0.9f;
    public static final float KNOCK_PITCH = 0.6f;

    // ---- Защита постройки (LabProtection) ----
    /** Стены, пол и потолок лаборатории нельзя сломать и взорвать (кроме творческого режима): шифры не обходятся киркой. */
    public static final boolean PROTECT = true;
    /** Запас вокруг этажа по горизонтали (фасад, пилястры). */
    public static final int PROTECT_MARGIN = 1;
    /** Над крышей павильона (будка, парапет, тарелка). */
    public static final int PROTECT_ROOF = 11;
    /** «Не поддаётся» — не чаще раза в столько тиков (держат ЛКМ или бьют по нескольким блокам подряд). */
    public static final int PROTECT_MESSAGE_TICKS = 40;

    /** Сколько цифр набирается на панели замка. */
    public static int lockDigits(int lock) {
        return lock == LOCK_INDEX ? INDEX_DIGITS.length : lock == LOCK_NURSERY ? POSTS_CAMERAS
                : lock == LOCK_SESSION ? com.hypersomnia.finale.FinaleTuning.SESSION_CODE.length() : CODE_DIGITS;
    }

    /** Панель помнит набранное (черновик игрока на сервере) и не стирает ввод после отказа. */
    public static boolean lockKeepsInput(int lock) {
        return lock == LOCK_INDEX;
    }

    /** Панель работает только при включённом MAIN FEED. */
    public static boolean lockNeedsFeed(int lock) {
        return lock == LOCK_INDEX || accessLock(lock);
    }

    // ---- Рабочие станции CRADLE WORKSTATION (ArchiveDefs, LabTerminalScreen) ----
    /** Свет блока станции (экран), 0-15. */
    public static final int WS_LIGHT = 3;
    /** «LOADING PROFILE...» после входа: первый вход в каждую станцию за запуск игры (тики, любая клавиша пропускает). */
    public static final int WS_LOGON_TICKS = 16;
    /** «READING INDEX…» при первом открытии за запуск игры (тики, любая клавиша пропускает). */
    public static final int ARCHIVE_LOADING_TICKS = 8;
    /** Сколько тиков строка состояния показывает RECORD UNAVAILABLE. */
    public static final int ARCHIVE_FLASH_TICKS = 40;
    /** Прокрутка документа на один щелчок колеса / стрелку (px GUI). */
    public static final int ARCHIVE_SCROLL_PX = 10;
    /** Mono: минимальная ширина ячейки сетки колонок (px GUI); фактическая = max(этого, самого широкого символа колонок записи). */
    public static final int ARCHIVE_MONO_CELL = 6;
    /** Mono: последняя колонка переносится под своё начало, если оно не правее этой доли ширины строки (%), иначе с начала строки. */
    public static final int ARCHIVE_MONO_HANG_MAX_PCT = 66;
    /** CRT: развёртка и неоднородность яркости поверх окна. Вторичны, текст должен читаться. */
    public static final boolean ARCHIVE_CRT = true;
    /** Тёмная строка развёртки через каждые ARCHIVE_SCANLINE_STEP px GUI (ARGB). */
    public static final int ARCHIVE_SCANLINE_ARGB = 0x12000000;
    public static final int ARCHIVE_SCANLINE_STEP = 2;
    /** Затемнение к верхнему и нижнему краю окна: высота полосы (px GUI) и альфа у края. */
    public static final int ARCHIVE_VIGNETTE_PX = 28;
    public static final int ARCHIVE_VIGNETTE_ALPHA = 0x1C;
    /** Медленная светлая полоса (прокатка кадра): высота (px GUI), альфа в центре, период прохода (мс). */
    public static final int ARCHIVE_ROLL_PX = 18;
    public static final int ARCHIVE_ROLL_ALPHA = 0x07;
    public static final int ARCHIVE_ROLL_MS = 9000;

    // ---- Щит MAIN FEED (B1, Power Room) ----
    /** Рубильники 1..FEEDERS; 0 = главный рубильник MAIN FEED. */
    public static final int FEEDERS = 5;
    /**
     * Действующий порядок замыкания — перестановка 1..FEEDERS по зерну мира (LabVariants.feedOrder, ключ FEED_KEY).
     * Ревизия C бланка SW-B1-01 показывает его, B — его же с одной парой соседних шагов наоборот, A — 1-2-3-4-5.
     * Строку порядка в бланке подставляет терминал при отрисовке (lang sw_b1_01*.6 — только запасной текст).
     */
    public static final String FEED_KEY = "feed_order";
    /**
     * После включения питания лифт открыт до этой остановки (B2). Ниже — по панелям LIFT ACCESS (ACCESS_LOCKS).
     * Без основного питания лифт на аварийном контуре и ходит только до EMERGENCY_REACHED (G и B1, cab_evac).
     */
    public static final int FEED_REACHED = 2;
    public static final int EMERGENCY_REACHED = 1;
    /**
     * Лифт мира снов открывает этажи, уже открытые в реальной лаборатории (во сне нет щита MAIN FEED).
     * Нужен открытый вход сна (reached >= 1); свой reached сна не меняется.
     */
    public static final boolean DREAM_FOLLOWS_REAL = true;
    /** Экран щита: сервер принимает рубильники, только если игрок не дальше этого от щита (блоки). */
    public static final int PANEL_MAX_DIST = 6;
    /** Свет блока щита при включённом питании (лампа ON), 0-15. */
    public static final int PANEL_LIGHT = 4;
    /** Сколько тиков экран щита мигает после того, как MAIN FEED выбило. */
    public static final int PANEL_TRIP_FLICKER_TICKS = 12;
    /** Лампа сработавшего щита (TRIPPED) чуть светится, 0-15: в темноте «обесточено» видно, куда бежать. */
    public static final int PANEL_TRIPPED_LIGHT = 3;

    // ---- Интерьер (LabBuilder) ----
    /** Свет монитора со «снегом» (мир снов), 0-15. */
    public static final int MONITOR_LIGHT = 5;
    /** Решётка вентиляции: столько блоков вниз от кровли (2 = верхний ряд стены под потолочным). */
    public static final int VENT_BELOW_ROOF = 2;
    /** Стол без своего стула, без ряда и без стены смотрит на ближайший стул не дальше стольких клеток (по осям). */
    public static final int DESK_CHAIR_REACH = 3;

    // ---- Стул и шкаф ----
    /** Точка сиденья (SeatEntity) над низом блока стула: верх подушки модели 8.5/16. */
    public static final double CHAIR_SEAT_Y = 0.53;
    /** Ящиков в картотечном шкафу: ряд экрана = ящик на 9 ячеек. */
    public static final int CABINET_ROWS = 3;
    /** Бумага в шкафу: 1..CABINET_PAPER_STACKS пачек по 1..CABINET_PAPER_MAX листов. */
    public static final int CABINET_PAPER_STACKS = 3;
    public static final int CABINET_PAPER_MAX = 6;
    /** Шкаф без документа пуст с шансом 1 из N (0 = пустых нет). Раскладка одинакова при каждой перестройке. */
    public static final int CABINET_EMPTY_ONE_IN = 5;
    /** Звук ящика (железный люк): громкость и высота. [проверить на слух] */
    public static final float CABINET_SOUND_VOLUME = 0.45f;
    public static final float CABINET_SOUND_PITCH = 1.45f;

    // ---- Павильон: фасад ----
    /** Пилястры на фасаде на 1/4 и 3/4 длины стороны. */
    public static final double PILASTER_FRACTION = 0.25;
    /** Редкие «заплатки» ремонта: 1 из N блоков поля — каменный кирпич (0 = выключено). */
    public static final int PATCH_REAL = 61;
    public static final int PATCH_DREAM = 43;
    /** Трещины: 1 из N блоков поля. */
    public static final int CRACKS_REAL = 149;
    public static final int CRACKS_DREAM = 83;
    /** Мир снов: 1 из N блоков поля — камень, которого в этом здании нет (0 = выключено). */
    public static final int WRONG_DREAM = 211;

    // ---- Крыша павильона: парапет и антенна (реальный мир — ANT-1, сон — статичная тарелка) ----
    /** Сколько блоков над крышей стирать при пересборке (парапет, мачта, тарелка). */
    public static final int ROOF_CLEAR = 11;
    /** Шаг столбиков ограды крыши: каждый N-й блок — столбик (плюс все углы). */
    public static final int PARAPET_POST_EVERY = 4;

    // ---- Рельеф плоской крыши (без скатов) ----
    /** Высота машинного помещения над кабиной лифта. */
    public static final int MACHINE_HEIGHT = 2;
    /** Отступ люков от углов крыши. */
    public static final int ROOF_HATCH_INSET = 3;

    // ---- Лифт ----
    public static final int LIFT_BASE_TICKS = 50;
    public static final int LIFT_PER_FLOOR = 16;
    /** [новое] Тиков на блок пути; для подземных этажей совпадает с LIFT_PER_FLOOR / PITCH. */
    public static final int LIFT_PER_BLOCK = 2;

    // ---- «Обесточено» (LabOutage): погоня в реальной лаборатории после MAIN FEED ----
    /** Этажи, где бывает (B5 — финал, не трогаем). Щит этажа — у лифта, клетка '&' чертежа (id LabBreakers.BOARD + этаж). */
    public static final int OUTAGE_MIN_STOP = 1;
    public static final int OUTAGE_MAX_STOP = 4;
    /** Мигание перед темнотой: длительности (тики) попеременно «погасли / зажглись», начиная с «погасли»; потом темно. */
    public static final int[] OUTAGE_FLICKER = {3, 4, 2, 2, 1, 6, 3, 1, 8, 2, 4};
    /** Свет возвращается: попеременно «зажглись / погасли», начиная с «зажглись»; потом горит. */
    public static final int[] OUTAGE_RESTORE = {2, 5, 1, 3};
    /** Свет вернётся сам: через столько тиков после конца погони, но не позже OUTAGE_MAX_TICKS от начала. */
    public static final int OUTAGE_AFTER_CHASE = 60;
    public static final int OUTAGE_MAX_TICKS = 1200;
    /** Звуки: щелчки ламп при мигании, гул обрыва, удар щита, возврат питания (громкость, высота). [проверить на слух] */
    public static final float OUTAGE_CLICK_VOLUME = 0.35f;
    public static final float OUTAGE_CLICK_PITCH = 0.6f;
    public static final float OUTAGE_DOWN_VOLUME = 1.0f;
    public static final float OUTAGE_DOWN_PITCH = 0.5f;
    public static final float OUTAGE_UP_VOLUME = 0.8f;
    public static final float OUTAGE_UP_PITCH = 0.6f;
}
