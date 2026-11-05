package com.hypersomnia.finale;

/**
 * Финал (канон, пункт 9): детская за решёткой зала наблюдения (мир снов, B1, чертёж dream_b1, строки finale).
 * Кровать — уснуть, дверь — проснуться, Фрагмент стоит между ними. Монитор на столе: кассеты.
 * Общий код сервера и клиента, без клиентских импортов. Все числа финала — здесь.
 */
public final class FinaleTuning {
    private FinaleTuning() {}

    // ---- Концовки: номер = бит маски ObserverProgressData.endings, ключи lang/достижений ----
    public static final int OBLIVION = 0;
    public static final int FAREWELL = 1;
    public static final int REPLACEMENT = 2;
    public static final int UNITY = 3;
    public static final int ETERNAL = 4;
    public static final int NORA = 5;
    public static final int SUBJECT_48 = 6;
    /** Ключ концовки: lang hypersomnia.ending.<key>.*, достижение hypersomnia:ending/<key>. */
    public static final String[] KEYS = {"oblivion", "farewell", "replacement", "unity", "eternal_sleep", "nora", "subject_48"};
    public static final int ENDINGS = KEYS.length;

    // ---- Детская: части в чертеже (строки «finale X = bed|door|recorder|fragment|toy»), KIND_FINALE * 1000 + часть ----
    public static final int PART_BED = 0;
    public static final int PART_DOOR = 1;
    public static final int PART_RECORDER = 2;
    /** Точка, где стоит Фрагмент (запись над полом, ПКМ туда не попадает). */
    public static final int PART_FRAGMENT = 3;
    /** Красный коврик между кроватью и Фрагментом: сюда при входе в детскую ложится рисунок 4 (метка над полом, как у Фрагмента). */
    public static final int PART_TOY = 4;
    public static final String[] PARTS = {"bed", "door", "recorder", "fragment", "toy"};

    /** Капсулы с номерами (строки «capsule X = n», KIND_CAPSULE * 1000 + n). Секрет «Subject 48» — капсула 47. */
    public static final int CAPSULE_47 = 47;

    // ---- Условия ----
    /** Кассета счастливого сна (третий вариант) появляется у Фрагмента при стольких найденных воспоминаниях. */
    public static final int TAPE_MIN_MEMORIES = 4;
    /** Кассета (документ на движке страниц): монтаж «счастливого сна». */
    public static final String TAPE_DOC = "tape_edited";
    /** Воспоминание 7, кассета оператора: проигранная на мониторе детской при всех воспоминаниях — правда для Фрагмента. */
    public static final String LOG_DOC = "mem_7_log";
    /** Строки mem_7_log, которые звучат в детской (номера элементов DocumentDefs). */
    public static final int[] LOG_LINES = {3, 4, 5, 6};
    /** Рисунок 4 (два одинаковых папы и красная игрушка между ними): лежит на коврике PART_TOY, пока не найден. */
    public static final String TOY_DRAWING_DOC = "drawing_4";
    /** Шприц с маркировкой «Deep sedation: Subject 47, per Director» (документ). */
    public static final String SEDATION_DOC = "sedation_47";
    /** Панель смены сеанса в блоке 47-48 (LabTuning.LOCK_SESSION): номер следующего сеанса. */
    public static final String SESSION_CODE = "48";

    // ---- Комната ----
    /**
     * Игрок «в детской»: блок ног в прямоугольнике от метки Фрагмента (dx от ROOM_MIN_DX до ROOM_MAX_DX, dz от ROOM_MIN_DZ
     * до ROOM_MAX_DZ; чертёж dream_b1: комната x11..19, z31..36, метка X (15, 34)) и не выше ROOM_DY над полом.
     * Проём ворот и блок 47-48 — снаружи (проверяет FinaleSelfTest по чертежу).
     */
    public static final int ROOM_MIN_DX = -4;
    public static final int ROOM_MAX_DX = 4;
    public static final int ROOM_MIN_DZ = -3;
    public static final int ROOM_MAX_DZ = 2;
    public static final int ROOM_DY = 3;
    /** Как часто сервер проверяет, в комнате ли игрок (тики). */
    public static final int ROOM_CHECK_TICKS = 10;
    /** Пока игрок в детской, сон не кончается: до конца сна остаётся не меньше стольких тиков. */
    public static final int ROOM_HOLD_TICKS = 20 * 30;
    /** Кассета лежит у ног Фрагмента: не выдавать новую, если такая уже лежит ближе стольких блоков (и рисунок 4 — тоже). */
    public static final double TAPE_LOOK_RADIUS = 8.0;
    /** Выбор (кровать, дверь, кассета, капсула) — вторым ПКМ по той же части не позже стольких тиков после первого. */
    public static final int CONFIRM_TICKS = 20 * 5;

    // ---- Запись mem_7 в детской (клиентские субтитры) ----
    /** Строка субтитров: появление, показ, угасание (тики). */
    public static final int SUB_FADE_TICKS = 12;
    public static final int SUB_SHOW_TICKS = 90;
    /** Пауза перед ответом Фрагмента после последней строки записи. */
    public static final int SUB_REPLY_DELAY = 30;
    /** После правды глаза Фрагмента закрываются за столько тиков (после ответа). */
    public static final int EYES_CLOSE_TICKS = 100;

    /** Одна строка субтитров целиком (появление + показ + угасание). */
    public static int subLineTicks() {
        return SUB_FADE_TICKS * 2 + SUB_SHOW_TICKS;
    }

    /** Начало ответа Фрагмента от начала записи. */
    public static int replyAt() {
        return LOG_LINES.length * subLineTicks() + SUB_REPLY_DELAY;
    }

    /** Вся запись с ответом: столько же сервер не даёт включить её снова и держит монитор включённым. */
    public static int playTicks() {
        return replyAt() + subLineTicks();
    }

    // ---- Концовка ----
    /** Пока идёт экран концовки, сон не кончается: таймер сна ставится на столько тиков (страховка, если клиент молчит). */
    public static final int ENDING_HOLD_TICKS = 20 * 60 * 5;
    /** Экран концовки: строка появляется за LINE_FADE_TICKS; следующая через LINE_GAP_TICKS + LINE_TICKS_PER_CHAR * длина. */
    public static final int LINE_FADE_TICKS = 24;
    public static final int LINE_GAP_TICKS = 50;
    public static final float LINE_TICKS_PER_CHAR = 0.9f;
    /** После последней строки — пауза, затем тёмная пауза и карточка концовки. */
    public static final int LINES_HOLD_TICKS = 80;
    public static final int BLACK_TICKS = 40;
    /** Кадр «капсула 47, мёртвые мониторы» (Вечный сон, Subject 48) перед карточкой. */
    public static final int FRAME_TICKS = 120;
    /** Карточка: закрыть можно через CARD_MIN_TICKS, сама закрывается через CARD_AUTO_TICKS. */
    public static final int CARD_MIN_TICKS = 80;
    public static final int CARD_AUTO_TICKS = 20 * 30;

    /** Таймкод экрана концовки (секунды от полуночи): запись «Нора» — с 02:58, остальные — с 03:13 (протокол 47). */
    public static int timecodeStart(int ending) {
        return ending == NORA ? 2 * 3600 + 58 * 60 : 3 * 3600 + 13 * 60;
    }

    // ---- Флаги экрана концовки (EndingPayload.flags) ----
    /** Найден пустой лист (drawing_blank): реплика «Ты не успел. Но ты был там.» в концовках с правдой. */
    public static final int FLAG_BLANK = 1;

    /** Концовка с правдой (реплика пустого листа возможна). */
    public static boolean withTruth(int ending) {
        return ending == FAREWELL || ending == UNITY || ending == NORA;
    }

    /** Состояние финала для клиента одним числом (ObserverStagePayload.finale): ending + 1 в младших битах, правда — бит 4. */
    public static int pack(int ending, boolean truth) {
        return (Math.max(-1, Math.min(ENDINGS - 1, ending)) + 1) | (truth ? 1 << 4 : 0);
    }

    public static int endingOf(int packed) {
        return (packed & 0xF) - 1;
    }

    public static boolean truthOf(int packed) {
        return (packed & (1 << 4)) != 0;
    }
}
