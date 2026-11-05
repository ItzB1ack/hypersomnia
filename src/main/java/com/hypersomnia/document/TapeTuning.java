package com.hypersomnia.document;

/**
 * Кассета (страницы Style.TAPE): темп, с которым экран документа «проигрывает» расшифровку, и громкость звуков ленты.
 * 20 тиков = 1 секунда. Общий код, без клиентских импортов.
 */
public final class TapeTuning {
    private TapeTuning() {}

    /** Знаков расшифровки за тик: 0.75 = 15 знаков в секунду, темп спокойной речи. Сеанс 12 идёт ~30 с, последняя запись ~40 с. */
    public static final float CHARS_PER_TICK = 0.75f;
    /** Пустая лента до первой фразы. */
    public static final int LEAD_IN_TICKS = 24;
    /** Пауза после точки, вопроса, восклицания (если дальше пробел или конец строки). */
    public static final int SENTENCE_PAUSE_TICKS = 8;
    /** Пауза после запятой, двоеточия, точки с запятой. */
    public static final int COMMA_PAUSE_TICKS = 3;
    /** Пауза между абзацами: говорящий молчит. */
    public static final int PARA_PAUSE_TICKS = 18;
    /** Провал записи на месте вымаранного слова (█): столько тиков лента идёт без голоса. */
    public static final int DROPOUT_TICKS = 16;
    /** Пустая лента после последней фразы до щелчка конца. */
    public static final int TAIL_TICKS = 30;

    /** Счётчик ленты: одна единица за столько тиков. Три цифры, по кругу. */
    public static final int COUNTER_STEP_TICKS = 6;
    /** Скорость катушек, градусов за тик. */
    public static final float REEL_DEG_PER_TICK = 10f;

    /** Громкость шипения ленты. Все звуки кассеты идут по ползунку Jukebox/Note Blocks (SoundSource.RECORDS). */
    public static final float HISS_VOLUME = 0.32f;
    /** Во сколько раз стихает шипение на провале записи. */
    public static final float DROPOUT_HISS_FACTOR = 0.25f;
    public static final float DROPOUT_VOLUME = 0.55f;
    public static final float CLICK_VOLUME = 0.7f;
    /** Высота щелчка: старт и стоп звучат чуть по-разному. */
    public static final float CLICK_PITCH_START = 1.0f;
    public static final float CLICK_PITCH_STOP = 0.8f;
}
