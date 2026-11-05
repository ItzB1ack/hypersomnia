package com.hypersomnia.finale;

/**
 * Тексты финала (EN, запасной язык). Перевод: lang hypersomnia.ending.<key>.line.<i> / .title / .card.<i> / .stamp
 * и hypersomnia.finale.*. Общий код, без клиентских импортов.
 * Стили строк экрана концовки: n — рассказ, f — Фрагмент (строчные, винный цвет), v — голос (Nora, имя),
 * s — служебная строка записи, b — реплика пустого листа (только с FLAG_BLANK).
 */
public final class FinaleTexts {
    private FinaleTexts() {}

    public record Line(char style, String text) {}

    private static Line n(String t) { return new Line('n', t); }
    private static Line f(String t) { return new Line('f', t); }
    private static Line v(String t) { return new Line('v', t); }
    private static Line s(String t) { return new Line('s', t); }
    private static Line b() { return new Line('b', BLANK_LINE); }

    /** Реплика пустого листа (drawing_blank). */
    public static final String BLANK_LINE = "you didn't make it. but you were there.";

    /** Строки концовок, индекс = FinaleTuning.OBLIVION..SUBJECT_48. */
    public static final Line[][] LINES = {
            { // Забвение
                    n("You open the door."),
                    f("wait."),
                    n("Morning. The room behind you is gone."),
                    n("You do not remember the dream. You never do."),
                    n("Somewhere below the facility, a small light stays on."),
                    n("It is still awake.")},
            { // Прощание
                    n("You walk to the door. It does not follow."),
                    f("nobody could."),
                    n("It says it the way you would. It has always spoken the way you do."),
                    n("Its eyes are closed. For the first time, it sleeps."),
                    b(),
                    n("You wake up. You remember her name."),
                    v("Nora.")},
            { // Замена
                    n("You lie down in the small bed. It is too short for you."),
                    n("When you close your eyes, it stands up."),
                    f("sleep. i will be you for a while."),
                    n("In the morning, someone gets out of your bed."),
                    n("He walks like you. He answers to your name. He does not sleep.")},
            { // Единство
                    n("You lie down. It sits on the edge of the bed, the way you used to sit with her."),
                    f("it wasn't our fault."),
                    n("Two halves of one page. The lines meet."),
                    b(),
                    n("You do not hear it lie down. You only notice that the room is warm."),
                    n("For the first time in a long time, you sleep through the night.")},
            { // Вечный сон
                    s("TAPE 47-N / EDITED / PLAY"),
                    n("She is laughing. On this tape she is always laughing."),
                    n("You stop moving. Your hands keep going on their own."),
                    n("Years pass on the other side of the glass. The lights go out one floor at a time."),
                    n("It takes a syringe from the tray. It looks like yours. The label is a different colour."),
                    n("It does not hesitate. Of the two of you, only it is still awake.")},
            { // Нора
                    s("RECORDER 47-OP / SESSION 47 / NIGHT RECORD / 02:58"),
                    v("\"Daddy? You're holding too tight.\""),
                    v("\"Can you bring Bunny back? He fell. He's on the floor.\""),
                    n("Her voice is tired. It is not the voice from the tape."),
                    n("You hold her hand. Something holds yours. It is cold, and it is shaking, and it is yours."),
                    s("03:07"),
                    n("Neither of you lets go."),
                    b(),
                    n("You say goodbye. Both of you."),
                    n("You wake up together.")},
            { // Субъект 48
                    n("You lie down in capsule 47. It is the right size. It always was."),
                    n("The needle is cold. The label says: per Director."),
                    n("A hand closes around your wrist. It is shaking."),
                    f("don't."),
                    n("You press anyway. The hand lets go.")}
    };

    public static final String[] TITLES = {"Oblivion", "Farewell", "Replacement", "Unity", "Eternal Sleep", "Nora", "Subject 48"};

    public static final String[][] CARDS = {
            {"SESSION 47: CLOSED BY SUBJECT.", "ANOMALY 47-A: NOT RECOVERED."},
            {"SESSION 47: CLOSED.", "FRAGMENT: AT REST."},
            {"SESSION 47: ACTIVE.", "SUBJECT 47: RESPONSIVE. NO ANOMALY DETECTED."},
            {"SESSION 47: TERMINATED BY SUBJECT.", "ANOMALY 47-A: NOT FOUND."},
            {"SESSION 47: NO OPERATOR.", "SUBJECT 47: NO SIGNAL."},
            {"RECORD 47-OP: RETAINED.", "SESSION 47: CLOSED."},
            {"SESSION 47: TERMINATED.", "SESSION 48: INITIATED."}
    };

    public static final String[] STAMPS = {"FILE CLOSED", "FILE CLOSED", "APPROVED", "FILE CLOSED", "SIGNAL LOST", "DO NOT PURGE", "PER DIRECTOR"};

    /** Описания достижений (подсказки без раскрытия). */
    public static final String[] HINTS = {
            "Wake up without remembering.",
            "Tell it the truth and wake up.",
            "Let it take your place.",
            "Tell it the truth and stay.",
            "Play the edited tape.",
            "Hear the real record.",
            "Begin the next session."
    };

    /** Кадр «капсула 47, мёртвые мониторы» перед карточкой. */
    public static boolean hasFrame(int ending) {
        return ending == FinaleTuning.ETERNAL || ending == FinaleTuning.SUBJECT_48;
    }

    public static String key(int ending) {
        return "hypersomnia.ending." + FinaleTuning.KEYS[ending];
    }

    // ---- Детская и экран (ключ, EN) ----
    public static final String[][] UI = {
            {"hypersomnia.finale.reply", "nobody could."},
            {"hypersomnia.finale.reply.none", "NO RESPONSE."},
            {"hypersomnia.finale.recorder.empty", "RECORDER: NO TAPE"},
            {"hypersomnia.finale.recorder.busy", "RECORDER: PLAYING"},
            {"hypersomnia.finale.confirm.bed", "Use again to fall asleep."},
            {"hypersomnia.finale.confirm.door", "Use again to wake up."},
            {"hypersomnia.finale.confirm.tape", "Use again to play the tape."},
            {"hypersomnia.finale.confirm.capsule", "Use again to lie down in capsule 47."},
            {"hypersomnia.finale.capsule.active", "SESSION 47: ACTIVE"},
            {"hypersomnia.finale.capsule.standby", "SESSION 48: AWAITING SUBJECT"},
            {"hypersomnia.finale.capsule.reserved", "CAPSULE 48: RESERVED"},
            {"hypersomnia.finale.capsule.empty", "CAPSULE: EMPTY"},
            {"hypersomnia.finale.log", "OPERATOR / LAST LOG"},
            {"hypersomnia.finale.screen.play", "PLAY"},
            {"hypersomnia.finale.screen.capsule", "CAPSULE 47"},
            {"hypersomnia.finale.screen.continue", "[ any key ]"},
            {"hypersomnia.finale.screen.ending", "ENDING"},
            {"hypersomnia.advancement.root.title", "Session 47"},
            {"hypersomnia.advancement.root.description", "Reach an ending."}
    };

    public static String ui(String key) {
        for (String[] kv : UI) if (kv[0].equals(key)) return kv[1];
        return key;
    }
}
