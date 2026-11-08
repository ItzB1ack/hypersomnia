package com.hypersomnia.lab;

import com.hypersomnia.document.DocumentDefs.El;
import com.hypersomnia.document.DocumentDefs.Gap;
import com.hypersomnia.document.DocumentDefs.Head;
import com.hypersomnia.document.DocumentDefs.Mono;
import com.hypersomnia.document.DocumentDefs.Para;
import com.hypersomnia.document.DocumentDefs.Stamp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Рабочие станции CRADLE WORKSTATION и их содержимое: почта, локальный архив, журналы, документы.
 * У каждой станции свой набор приложений и записей. Общий код, без клиентских импортов; доступ выдаёт только сервер (ArchiveAccess).
 * Ключи lang: hypersomnia.archive.<id>.title, hypersomnia.archive.<id>.<индекс элемента> (считаются все элементы, включая Gap);
 * hypersomnia.archive.app.<app>, hypersomnia.archive.section.<папка>, hypersomnia.archive.type.<type в нижнем регистре, пробел = _>,
 * hypersomnia.archive.station.<key>. Запасной английский текст здесь, русский — в ru_ru.json.
 * id записей и станций стабильны: по ним сервер выдаёт доступ, а клиент помнит выделение и прочитанное. Не переименовывать.
 */
public final class ArchiveDefs {
    private ArchiveDefs() {}

    /** Приложение рабочего стола. Порядок = порядок значков. */
    public enum App {
        MAIL("mail", "Mail"),
        ARCHIVE("archive", "Local Archive"),
        LOGS("logs", "Logs"),
        FILES("files", "Documents");

        public final String key;
        public final String fallback;

        App(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }
    }

    /** Папка приложения (раздел архива, папка почты). Порядок = порядок в дереве. */
    public enum Folder {
        INBOX(App.MAIL, "inbox", "Inbox"),
        SENT(App.MAIL, "sent", "Sent"),
        RECORDS(App.ARCHIVE, "records", "Records"),
        SESSIONS(App.ARCHIVE, "sessions", "Session Index"),
        MAINTENANCE(App.ARCHIVE, "maintenance", "Maintenance"),
        ACCESS(App.ARCHIVE, "access", "Access Control"),
        LOGS(App.LOGS, "logs", "Logs"),
        FILES(App.FILES, "files", "Documents");

        public final App app;
        public final String key;
        public final String fallback;

        Folder(App app, String key, String fallback) {
            this.app = app;
            this.key = key;
            this.fallback = fallback;
        }

        public static List<Folder> of(App app) {
            List<Folder> out = new ArrayList<>();
            for (Folder f : values()) if (f.app == app) out.add(f);
            return out;
        }
    }

    /**
     * Рабочая станция. id = номер в LabSavedData.inter (KIND_TERMINAL * 1000 + id), key = имя в чертеже (station P = key).
     * host и user показываются при входе и на панели задач; place — запасной текст lang hypersomnia.archive.station.<key>.
     */
    public record Station(int id, String key, String host, String user, String place, List<App> apps) {}

    /** Условие доступа. Проверяет только сервер (ArchiveAccess). Новые виды добавляются сюда и в ArchiveAccess.test. */
    public sealed interface Cond permits Always, LockOpen, LabFlag, Memory, World, Not {}
    public record Always() implements Cond {}
    /** Замок лаборатории id открыт в этом мире (LabSavedData.locksOpen). */
    public record LockOpen(int lock) implements Cond {}
    /** Сюжетное событие мира произошло (LabSavedData.FLAG_*). */
    public record LabFlag(int flag) implements Cond {}
    /** Игрок нашёл воспоминание n (1..7, ObserverProgressData). Даёт только служебную запись-ссылку, не текст воспоминания. */
    public record Memory(int n) implements Cond {}
    /** Станция стоит в этом мире: dream = мир снов (LabSavedData.dream). Так у одной станции две версии содержимого. */
    public record World(boolean dream) implements Cond {}
    public record Not(Cond cond) implements Cond {}

    public static final Cond ALWAYS = new Always();
    public static final Cond REAL = new World(false);
    public static final Cond DREAM = new World(true);
    private static final Cond FEED = new LabFlag(LabSavedData.FLAG_FEED);

    /**
     * Запись станции station. Смысл полей зависит от приложения папки:
     * <pre>
     *            ref              title      type                   date      access   to
     * ARCHIVE    служебный номер  заголовок  тип (lang type.*)      индекс    допуск   -
     * MAIL       отправитель      тема       -                      дата      -        получатель
     * LOGS       имя файла        заголовок  источник (как есть)    изменён   -        -
     * FILES      имя файла        заголовок  тип (lang type.*)      изменён   -        -
     * </pre>
     * @param order    порядок в папке (почта: сверху новые)
     * @param listed   запись видна в списке
     * @param readable запись открывается; иначе в списке как [RESTRICTED]
     */
    public record Rec(String id, int station, Folder folder, int order, String ref, String title, String type, String date,
                      String access, String to, Cond listed, Cond readable, List<El> body) {
        public App app() { return folder.app; }

        public String typeKey() {
            return "hypersomnia.archive.type." + type.toLowerCase(Locale.ROOT).replace(' ', '_');
        }
    }

    // ---- Станции. id не менять: они записаны в LabSavedData.inter построенных лабораторий ----
    /** Охрана B1: почта, локальный архив, журналы. id 1 — бывший единственный терминал B1. */
    public static final int ST_SEC_B1 = 1;
    /** Пост у ворот (G), в обоих мирах: почта, журналы. */
    public static final int ST_GATE_G = 2;
    /** Щитовая B1: почта, журналы, документы. */
    public static final int ST_ENG_B1 = 3;

    /** Records B2 (кабинет архива): почта, журнал копии. Шифр 7. */
    public static final int ST_ARC_B2 = 4;
    /** Пульт серверной B3: почта, журнал буферов. Шифр 8. */
    public static final int ST_SRV_B3 = 5;
    /** Пост медсестры B4: почта. Шифр 9 (записка глифами в реальном 2D ведёт в сон, счёт — в записке сна). */
    public static final int ST_MED_B4 = 6;

    /** Журнал буферов B3 (шифр 8): номера стоек в строках BUFFER_ROWS терминал подставляет по зерну мира (LabVariants.rackHold). */
    public static final String BUFFER_LOG = "v_buffer_log";
    /** Индексы элементов-строк HOLD: BUFFER_ROWS[k] показывает стойку rackHold(seed)[k] (последняя — копия B2). */
    public static final int[] BUFFER_ROWS = {3, 5, 6, 7, 4};
    /**
     * Строки ANT-1 (шифр 8): [0] — первое трёхзначное число = азимут ретранслятора (LabVariants.antennaTarget),
     * [1] — «ретранслятор > куда отвернули» (antennaTarget > antennaStart). Терминал подставляет по зерну мира.
     */
    public static final int[] ANTENNA_ROWS = {8, 9};

    /** Часы панели задач в мире снов (в реальном мире — время мира). */
    public static final String DREAM_CLOCK = "03:13";

    /**
     * Правило ночного кода в письме g_keypad (элемент 1) по LabVariants.hatchRule, коды — LabTuning.HATCH_CODES.
     * 0 — прежний текст. Терминал подставляет вариант при отрисовке (RU: lang g_keypad.1.v<n>); в мире снов всегда 0.
     */
    public static final String[] G_KEYPAD_RULES = {
            "The code is set automatically to the time of the last gate entry: four digits, HHMM.",
            "The code is set automatically to the time of the first gate entry after 20:00, when the night shift starts: four digits, HHMM.",
            "The code is set automatically to the time the operator on duty passes the gate: four digits, HHMM.",
            "The code is set automatically to the time of the last gate entry before midnight: four digits, HHMM."};
    /** Номер строки порядка в бланке SW-B1-01 (switching): её терминал заменяет порядком мира (LabVariants.feedText). */
    public static final int SWITCHING_ORDER_ROW = 6;

    private static final Map<String, Rec> DEFS = new LinkedHashMap<>();
    private static final Map<Integer, Station> STATIONS = new LinkedHashMap<>();

    public static Rec get(String id) { return DEFS.get(id); }
    public static Collection<Rec> all() { return DEFS.values(); }
    public static Station station(int id) { return STATIONS.get(id); }
    public static Collection<Station> stations() { return STATIONS.values(); }

    public static Station station(String key) {
        for (Station s : STATIONS.values()) if (s.key().equals(key)) return s;
        return null;
    }

    /** Записи станции в папке по порядку (без учёта доступа). */
    public static List<Rec> inFolder(int station, Folder f) {
        List<Rec> out = new ArrayList<>();
        for (Rec r : DEFS.values()) if (r.station() == station && r.folder() == f) out.add(r);
        out.sort(Comparator.comparingInt(Rec::order));
        return out;
    }

    private static void addStation(int id, String key, String host, String user, String place, App... apps) {
        if (STATIONS.put(id, new Station(id, key, host, user, place, List.of(apps))) != null)
            throw new IllegalStateException("Duplicate workstation: " + id);
    }

    private static void add(Rec r) {
        if (DEFS.put(r.id(), r) != null) throw new IllegalStateException("Duplicate archive record: " + r.id());
        Station s = STATIONS.get(r.station());
        if (s == null || !s.apps().contains(r.app()))
            throw new IllegalStateException("Record " + r.id() + ": station " + r.station() + " has no app " + r.app());
    }

    private static void archive(int st, String id, Folder f, int order, String ref, String title, String type, String date,
                                String access, Cond listed, Cond readable, List<El> body) {
        add(new Rec(id, st, f, order, ref, title, type, date, access, "", listed, readable, body));
    }

    private static void mail(int st, String id, Folder f, int order, String from, String to, String date, String subject,
                             Cond listed, List<El> body) {
        add(new Rec(id, st, f, order, from, subject, "", date, "", to, listed, ALWAYS, body));
    }

    private static void log(int st, String id, int order, String file, String title, String source, String date,
                            Cond listed, List<El> body) {
        add(new Rec(id, st, Folder.LOGS, order, file, title, source, date, "", "", listed, ALWAYS, body));
    }

    private static void file(int st, String id, int order, String file, String title, String type, String date,
                             Cond listed, List<El> body) {
        add(new Rec(id, st, Folder.FILES, order, file, title, type, date, "", "", listed, ALWAYS, body));
    }

    /** Бланк SW-B1-01: одинаковая форма у всех ревизий, различаются ревизия, даты и порядок. Индексы элементов = ключи lang. */
    private static List<El> switching(String rev, String issued, String filed, String order) {
        return List.of(
                new Head("SWITCHING ORDER SW-B1-01"),                                                 // 0
                new Mono("REVISION  " + rev),                                                         // 1
                new Mono("ISSUED    " + issued),                                                      // 2
                new Mono("FILED     " + filed),                                                       // 3
                new Para("Restoring the main feed after a full shutdown, level B1."),               // 4
                new Para("Close the feeder breakers one at a time, in this order. Then close MAIN FEED."), // 5
                new Mono(order),                                                                      // 6
                new Para("If MAIN FEED trips, all breakers open. Start again from the first step."), // 7
                new Para("Signed: chief engineer."));                                                 // 8
    }

    /** Журнал щита B1 до включения питания; после включения к нему дописываются две строки (e_panel_log_on). */
    private static List<El> panelLog(boolean fed) {
        List<El> l = new ArrayList<>(List.of(
                new Head("PANEL B1 / EVENT LOG"),                                                     // 0
                new Mono("02.09  06:00  SELF TEST        OK"),                                        // 1
                new Mono("03.09  02:41  LOAD RISE        FEEDER 5"),                                  // 2
                new Mono("03.09  03:07  LOAD PEAK        FEEDER 5"),                                  // 3
                new Mono("03.09  03:13  MAIN FEED TRIP   FEEDERS OPEN"),                              // 4
                new Mono("03.09  03:13  EMERGENCY        ON")));                                      // 5
        if (fed) {
            l.add(new Mono("--.--  --:--  MAIN FEED ON     FEEDERS 1-5"));                            // 6
            l.add(new Mono("--.--  --:--  EMERGENCY        OFF"));                                    // 7
        }
        return List.copyOf(l);
    }

    private static List<El> gateHeader(List<El> rows) {
        List<El> l = new ArrayList<>();
        l.add(new Head("GATE READER G-1 / ENTRY LOG"));                                               // 0
        l.add(new Mono("DATE   TIME   EVENT  CARD"));                                                 // 1
        l.addAll(rows);                                                                               // 2..
        return List.copyOf(l);
    }

    static {
        // ================= Станции =================
        addStation(ST_SEC_B1, "sec_b1", "WS-B1-02", "d.kowal", "SECURITY B1", App.MAIL, App.ARCHIVE, App.LOGS);
        addStation(ST_GATE_G, "gate_g", "WS-G-01", "s.reyes", "GATE POST", App.MAIL, App.LOGS);
        addStation(ST_ENG_B1, "eng_b1", "WS-B1-03", "maint.b1", "POWER ROOM B1", App.MAIL, App.LOGS, App.FILES);
        addStation(ST_ARC_B2, "arc_b2", "WS-B2-01", "m.ashby", "RECORDS B2", App.MAIL, App.LOGS);
        addStation(ST_SRV_B3, "srv_b3", "WS-B3-01", "srv.night", "SERVER CONTROL B3", App.MAIL, App.LOGS);
        addStation(ST_MED_B4, "med_b4", "WS-B4-01", "nurse.b4", "NURSE POST B4", App.MAIL);

        // ================= Пост у ворот (G) =================
        // Замок 1: правило (бывшая табличка KEYPAD NOTE) — письмо g_keypad, ответ (бывшая табличка GATE LOG) — журнал ворот.
        // Код = время строки журнала, на которую указывает правило письма (G_KEYPAD_RULES по зерну мира, LabVariants.hatchCode);
        // во сне всегда 03:13. Времена журнала менять только вместе с LabTuning.HATCH_CODES.
        mail(ST_GATE_G, "g_voss", Folder.INBOX, 10, "h.voss", "s.reyes", "02.09 21:48", "Tonight", REAL, List.of(
                new Para("I will be in at about ten. The Director will come later."),                // 0
                new Para("Keep the lift on the night code. We will need it for the session."),       // 1
                new Para("H. Voss")));                                                                // 2
        mail(ST_GATE_G, "g_keypad", Folder.INBOX, 20, "sec.office", "security", "01.09 09:00", "Lift keypad: night code", ALWAYS, List.of(
                new Para("From 1 September the surface lift keypad uses a night code."),             // 0
                new Para(G_KEYPAD_RULES[0]),                                                         // 1: правило по миру
                new Para("Read it from the gate log. Do not write it down. Do not give it out by phone."), // 2
                new Para("Security Office")));                                                        // 3
        // Письмо написано до 02:41: последний вход в нём — 23:40. Код берут из журнала, а не из письма.
        mail(ST_GATE_G, "g_handover", Folder.SENT, 10, "s.reyes", "d.kowal", "02.09 23:45", "Gate, 2 September", REAL, List.of(
                new Para("Voss in at 22:05, the Director at 23:40. Otherwise quiet."),               // 0
                new Para("Lift on the night code. I am on the gate until six."),                     // 1
                new Para("S. Reyes")));                                                               // 2
        log(ST_GATE_G, "g_gate_log", 10, "GATE_G1.LOG", "Gate Reader G-1", "GATE G-1", "03.09 02:41", REAL, gateHeader(List.of(
                new Mono("02.09  07:52  IN     ASHBY M."),                                            // 2
                new Mono("02.09  08:10  IN     BRANDT T."),                                           // 3
                new Mono("02.09  17:31  OUT    ASHBY M."),                                            // 4
                new Mono("02.09  18:04  OUT    BRANDT T."),                                           // 5
                new Mono("02.09  20:56  IN     REYES S."),                                            // 6
                new Mono("02.09  21:02  IN     KOWAL D."),                                            // 7
                new Mono("02.09  21:10  IN     ORR J."),                                              // 8
                new Mono("02.09  21:30  IN     OPERATOR"),                                            // 9
                new Mono("02.09  22:05  IN     VOSS H."),                                             // 10
                new Mono("02.09  23:40  IN     HALLORAN"),                                            // 11
                new Mono("03.09  02:41  IN     MARSH E."))));                                         // 12
        // Мир снов: оцифровка записала то, что помнит игрок, а не журнал.
        log(ST_GATE_G, "g_gate_log_d", 10, "GATE_G1.LOG", "Gate Reader G-1", "GATE G-1", "03.09 03:13", DREAM, gateHeader(List.of(
                new Mono("03.09  03:13  IN     MARSH E."),                                            // 2
                new Mono("03.09  03:13  IN     MARSH E."),                                            // 3
                new Mono("03.09  03:13  IN     MARSH E."))));                                         // 4

        // ================= Охрана B1 =================
        mail(ST_SEC_B1, "s_calls", Folder.INBOX, 10, "office.director", "security", "02.09 23:50", "Calls tonight", ALWAYS, List.of(
                new Para("During the session tonight, put all internal calls for the session hall through to my office."), // 0
                new Para("Dr. Voss is not to be disturbed."),                                         // 1
                new Para("Director's Office")));                                                      // 2
        mail(ST_SEC_B1, "s_voss_hall", Folder.INBOX, 20, "h.voss", "d.kowal", "02.09 22:12", "Session hall", ALWAYS, List.of(
                new Para("The session hall closes to staff at 02:41. Reduce the corridor lights after that."), // 0
                new Para("Do not go into the hall during the session. If something is needed, call my office."), // 1
                new Para("H. Voss")));                                                                // 2
        mail(ST_SEC_B1, "s_cameras_off", Folder.INBOX, 30, "sec.office", "security", "01.09 08:30", "Cameras on session nights", ALWAYS, List.of(
                new Para("On session nights the B1 cameras go offline with the archive copy at 03:13. This is expected."), // 0
                new Para("Do not file a fault and do not restart the recorders. Note the outage in the shift log."), // 1
                new Para("Security Office")));                                                        // 2
        mail(ST_SEC_B1, "s_archive_copy", Folder.INBOX, 40, "m.ashby", "b1.staff", "28.08 10:05", "Local archive on B1", ALWAYS, List.of(
                new Para("Workstations on B1 now hold a read-only copy of the main archive."),       // 0
                new Para("The copy is refreshed every night at 03:13. Records filed during the day appear after the next copy."), // 1
                new Para("Report missing records to Records, not to IT."),                            // 2
                new Para("M. Ashby, Records")));                                                      // 3
        mail(ST_SEC_B1, "s_report", Folder.SENT, 10, "d.kowal", "sec.office", "03.09 04:05", "B1, night 02-03.09", ALWAYS, List.of(
                new Para("Night quiet. Session hall closed from 02:41."),                             // 0
                new Para("Cameras offline from 03:13 with the copy. Not back by the end of the shift."), // 1
                new Para("Main feed tripped at 03:13. B1 on the emergency circuit. Engineering informed."), // 2
                new Para("D. Kowal")));                                                               // 3
        // Бывшая запись архива (Records) перенесена в журналы станции; id прежний.
        log(ST_SEC_B1, "sec_log_b1", 10, "SHIFT_B1.LOG", "Security Shift Log", "SECURITY B1", "03.09 04:00", ALWAYS, List.of(
                new Head("SECURITY / LEVEL B1 / NIGHT SHIFT"),                                        // 0
                new Mono("22:00  Shift start. Level B1 secured. Reception closed."),                  // 1
                new Mono("01:30  Round complete. No incidents."),                                     // 2
                new Mono("02:41  Session hall closed to staff. Corridor lights reduced."),            // 3
                new Mono("03:04  Call from the medical wing for Dr. Voss. Put through to the Director's office, as instructed."), // 4
                new Mono("03:13  Archive copy. Monitoring offline until the copy completes."),        // 5
                new Mono("03:13  Main feed tripped. Level B1 on the emergency circuit."),             // 6
                new Mono("04:00  Shift handed over.")));                                              // 7
        // Бывшая табличка 4 (MONITORING / NO SIGNAL).
        log(ST_SEC_B1, "s_cameras", 20, "CAMERAS.LOG", "Monitoring Status", "MONITORING", "03.09 03:13", ALWAYS, List.of(
                new Head("MONITORING / STATION B1"),                                                  // 0
                new Mono("CAM    LOCATION      STATUS"),                                              // 1
                new Mono("G-01   GATE          NO SIGNAL"),                                           // 2
                new Mono("G-02   LIFT HALL     NO SIGNAL"),                                           // 3
                new Mono("B1-01  RECEPTION     NO SIGNAL"),                                           // 4
                new Mono("B1-02  CORRIDOR      NO SIGNAL"),                                           // 5
                new Mono("B1-03  POWER ROOM    NO SIGNAL"),                                           // 6
                new Mono("B2-B5  LEVEL 4 ONLY"),                                                      // 7
                new Mono("LAST FRAME           03.09 03:13")));                                       // 8

        // Локальный архив (только станция охраны B1). Служебная норма без сюжетных раскрытий.
        archive(ST_SEC_B1, "sys_notice", Folder.RECORDS, 10, "SYS-001", "Workstation Notice", "NOTICE", "02.09 08:00", "L1",
                ALWAYS, ALWAYS, List.of(
                new Head("CRADLE WORKSTATION / LEVEL B1"),                                            // 0
                new Para("This terminal provides read access to the local copy of the facility archive."), // 1
                new Para("The local copy is refreshed from the main archive every night at 03:13."),  // 2
                new Para("Records cannot be changed from this terminal."),                            // 3
                new Gap(),                                                                            // 4
                new Stamp("INTERNAL")));                                                              // 5
        archive(ST_SEC_B1, "idx_header", Folder.SESSIONS, 10, "IDX-000", "Index Header", "INDEX", "03.09 03:13", "L2",
                ALWAYS, ALWAYS, List.of(
                new Head("SESSION INDEX / PROGRAMME CRADLE"),                                         // 0
                new Para("Recorded sessions are listed by subject number."),                          // 1
                new Para("Status codes follow the session protocol. Entries for active subjects are held at level 4."))); // 2
        archive(ST_SEC_B1, "wo_b1_090", Folder.MAINTENANCE, 40, "WO-B1-090", "Corridor Lighting", "WORK ORDER", "12.08 09:20", "L1",
                ALWAYS, ALWAYS, List.of(
                new Head("WORK ORDER WO-B1-090"),                                                     // 0
                new Para("Location: level B1, corridor and reception."),                              // 1
                new Para("Task: replace failed lamps, test the emergency circuit."),                  // 2
                new Para("Result: three lamps replaced. Emergency circuit tested. No faults."),       // 3
                new Para("Signed: maintenance, shift 2.")));                                          // 4
        // «Порядок переключения». Правило — письмо e_sw_index на станции щитовой (бывшая табличка 7).
        // Date = дата подшивки в индекс: ревизию B переподшили 30.08 с бумажной копии, поэтому она выше всех в списке.
        // Действующая — ревизия C. Строку порядка (элемент 6) терминал подставляет по зерну мира (LabVariants.feedText);
        // здесь и в lang — запасной текст до прихода зерна.
        archive(ST_SEC_B1, "sw_b1_01b", Folder.MAINTENANCE, 10, "SW-B1-01/B", "Switching, Rev. B", "PROCEDURE", "30.08 03:13", "L1",
                ALWAYS, ALWAYS, switching("B", "16.08", "30.08  (paper copy)", "3 - 1 - 2 - 5 - 4"));
        archive(ST_SEC_B1, "sw_b1_01c", Folder.MAINTENANCE, 20, "SW-B1-01/C", "Switching, Rev. C", "PROCEDURE", "21.08 16:05", "L1",
                ALWAYS, ALWAYS, switching("C", "21.08", "21.08", "3 - 1 - 5 - 2 - 4"));
        archive(ST_SEC_B1, "sw_b1_01a", Folder.MAINTENANCE, 30, "SW-B1-01/A", "Switching, Rev. A", "PROCEDURE", "14.08 10:40", "L1",
                ALWAYS, ALWAYS, switching("A", "14.08", "14.08", "1 - 2 - 3 - 4 - 5"));
        // После включения питания. Часы терминала сброшены отключением: дата не задана.
        archive(ST_SEC_B1, "sys_feed", Folder.RECORDS, 30, "SYS-002", "Power Event", "LOG", "--.-- --:--", "L1",
                FEED, ALWAYS, List.of(
                new Head("POWER EVENT / LEVEL B1"),                                                   // 0
                new Mono("MAIN FEED   ON"),                                                           // 1
                new Mono("SOURCE      PANEL B1"),                                                     // 2
                new Mono("CLOCK       NOT SET"),                                                      // 3
                new Para("Main feed restored from the panel on level B1. Feeders 1-5 closed."),      // 4
                new Para("Set the workstation clock before the next archive copy.")));              // 5
        // Условие Memory: служебная ссылка без содержания воспоминания.
        archive(ST_SEC_B1, "img_0412", Folder.RECORDS, 40, "IMG-0412", "Staff Event Photo", "INDEX", "02.09 03:13", "L2",
                new Memory(1), ALWAYS, List.of(
                new Head("PHOTO ARCHIVE / 0412"),                                                     // 0
                new Mono("ITEM      PHOTOGRAPH, PRINT"),                                              // 1
                new Mono("CAPTION   STAFF EVENT"),                                                    // 2
                new Para("Print held outside the archive. The local copy keeps this index card only."), // 3
                new Stamp("INTERNAL")));                                                              // 4
        archive(ST_SEC_B1, "acl_b1", Folder.ACCESS, 10, "ACL-B1", "Workstation Access", "POLICY", "02.09 08:00", "L1",
                ALWAYS, ALWAYS, List.of(
                new Head("ACCESS CONTROL / LEVEL B1"),                                                // 0
                new Para("Workstation B1: read only, local archive."),                                // 1
                new Para("Level B2 and below: clearance level 4. Requests through the Director's office."), // 2
                new Stamp("READ ONLY")));                                                             // 3
        // Показывает состояние [RESTRICTED]; замок 2 появится вместе с B2.
        archive(ST_SEC_B1, "acl_b2", Folder.ACCESS, 20, "ACL-B2", "B2 Access List", "POLICY", "02.09 08:00", "L4",
                ALWAYS, new LockOpen(2), List.of(
                new Head("ACCESS CONTROL / LEVEL B2"),                                                // 0
                new Para("Authorised: Dr. H. Voss. Director Halloran. Operator on duty.")));         // 1

        // ================= Щитовая B1 =================
        // Правило загадки щита (бывшая табличка 7: LATEST REVISION / NOT LATEST DATE / SEE ARCHIVE).
        mail(ST_ENG_B1, "e_sw_index", Folder.INBOX, 10, "t.brandt", "maint.b1", "30.08 09:12", "SW-B1-01 in the archive", ALWAYS, List.of(
                new Para("Records filed an old paper copy of SW-B1-01 last night. In the index it now shows as the newest."), // 0
                new Para("The index date is the filing date, not the issue date."),                   // 1
                new Para("Switch by the latest revision, not by the latest date."),                   // 2
                new Para("All revisions are in the local archive, on the workstation in the Security Office."), // 3
                new Para("T. Brandt, Chief Engineer")));                                              // 4
        mail(ST_ENG_B1, "e_emergency", Folder.INBOX, 20, "t.brandt", "b1.staff", "12.08 09:40", "Emergency circuit, B1", ALWAYS, List.of(
                new Para("With the main feed off, level B1 runs on the emergency circuit: workstations, lift, panel lamps and part of the lighting."), // 0
                new Para("After a trip, restore the main feed from the panel by SW-B1-01. Never close MAIN FEED before the feeders."), // 1
                new Para("T. Brandt")));                                                              // 2
        mail(ST_ENG_B1, "e_trip", Folder.SENT, 10, "maint.b1", "t.brandt", "03.09 03:16", "Main feed tripped", ALWAYS, List.of(
                new Para("Main feed tripped at 03:13, during the archive copy. All feeders open. B1 on the emergency circuit."), // 0
                new Para("Load on feeder 5 was high from 02:41. Not restoring at night without you. Will do it in the morning by SW-B1-01."), // 1
                new Para("J. Orr")));                                                                 // 2
        // Одна запись до включения питания и другая после: тот же файл, дописаны две строки.
        log(ST_ENG_B1, "e_panel_log", 10, "PANEL_B1.LOG", "Panel B1 Events", "PANEL B1", "03.09 03:13", new Not(FEED), panelLog(false));
        log(ST_ENG_B1, "e_panel_log_on", 10, "PANEL_B1.LOG", "Panel B1 Events", "PANEL B1", "--.-- --:--", FEED, panelLog(true));
        file(ST_ENG_B1, "e_feeders", 10, "FEEDERS.TXT", "Panel B1 Feeders", "TEXT", "21.08 16:30", ALWAYS, List.of(
                new Head("PANEL B1 / FEEDERS"),                                                       // 0
                new Mono("1  RECEPTION, CORRIDOR B1"),                                                // 1
                new Mono("2  SECURITY OFFICE"),                                                       // 2
                new Mono("3  LIFT"),                                                                  // 3
                new Mono("4  POWER ROOM"),                                                            // 4
                new Mono("5  LEVELS B2-B5"),                                                          // 5
                new Para("Breakers are numbered left to right, facing the panel. MAIN FEED is the last on the right."))); // 6

        // ================= Records B2 (шифр 7: код LIFT ACCESS B2 = контрольные цифры копии) =================
        mail(ST_ARC_B2, "a_copy", Folder.INBOX, 10, "m.ashby", "operator", "02.09 17:25", "Copy tonight", REAL, List.of(
                new Para("Tonight's copy is yours, I leave at half past five."),                     // 0
                new Para("One box from each row of the store, rows 1 to 4. Any box: it is a spot check. The trolley is in the copy room."), // 1
                new Para("The check sheet is in the copy room too. Do not take it out of B2."),     // 2
                new Para("Boxes back to their places after the copy, please. Last week row 3 stood half empty until noon."), // 3
                new Para("M. Ashby, Records")));                                                      // 4
        mail(ST_ARC_B2, "a_lift_codes", Folder.INBOX, 20, "sec.office", "level4", "28.08 09:00", "Lift codes below B2", REAL, List.of(
                new Para("Below B2 the lift is released from the LIFT ACCESS panel by the lift door on each level."), // 0
                new Para("B2: the check code of the night's copy. B3: no code, the panel is released when the held buffers are sent. B4: the Director's code."), // 1
                new Para("The codes are not kept on paper and not given out by phone."),           // 2
                new Para("Security Office")));                                                        // 3
        log(ST_ARC_B2, "a_copy_log", 10, "COPY.LOG", "Nightly Copy", "COPY B2", "03.09 03:13", REAL, List.of(
                new Head("ARCHIVE B2 / COPY LOG"),                                                    // 0
                new Mono("03.09  03:09  BOXES OUT      ROWS 1-4"),                                    // 1
                new Mono("03.09  03:13  COPY START     B1, B3"),                                      // 2
                new Mono("03.09  03:13  MAIN FEED      LOST"),                                        // 3
                new Mono("03.09  03:13  COPY           ABORTED"),                                     // 4
                new Mono("03.09  03:13  BOXES          NOT RETURNED"),                                // 5
                new Mono("03.09  03:13  CHECK CODE     NOT ENTERED")));                               // 6

        // ================= Пульт серверной B3 (шифр 8: буферы SOURCE 47 ушли бы через ANT-1, тарелку отвернули) =================
        // Номера стоек в строках HOLD (BUFFER_ROWS) и азимуты ANT-1 (ANTENNA_ROWS) терминал подставляет по зерну мира;
        // здесь и в lang — запасной текст.
        log(ST_SRV_B3, BUFFER_LOG, 10, "BUFFER.LOG", "Recording Buffers", "SRV-B3", "03.09 03:13", REAL, List.of(
                new Head("SERVERS B3 / BUFFER LOG"),                                                  // 0
                new Mono("03:12:58  SESSION 12   STREAM  SOURCE 47"),                                 // 1
                new Mono("03:13:00  MAIN FEED    LOST    UPS ON"),                                    // 2
                new Mono("03:13:01  HOLD   R07   SOURCE 47"),                                         // 3
                new Mono("03:13:01  HOLD   R02   SOURCE COPY B2"),                                    // 4
                new Mono("03:13:02  HOLD   R11   SOURCE 47"),                                         // 5
                new Mono("03:13:02  HOLD   R04   SOURCE 47"),                                         // 6
                new Mono("03:13:03  HOLD   R09   SOURCE 47"),                                         // 7
                new Mono("03:13:03  UPLINK  ANT-1  RELAY AZ 285  SEND"),                              // 8
                new Mono("03:13:04  ANT-1   SLEW BY HAND  285 > 105"),                                // 9
                new Mono("03:13:04  UPLINK  NO CARRIER    HOLD KEPT"),                                // 10
                new Mono("03:13:05  UPS LOW      WRITE OFF"),                                         // 11
                new Mono("03:13:05  LIFT B4      INTERLOCKED")));                                     // 12
        mail(ST_SRV_B3, "v_interlock", Folder.INBOX, 10, "t.brandt", "srv.night", "21.08 11:30", "Lift interlock B4", REAL, List.of(
                new Para("After a feed loss the lift to B4 stays interlocked while the buffers hold a recording."), // 0
                new Para("The buffers clear once they are sent to the relay through ANT-1, the dish on the roof. LIFT ACCESS B3 is released by itself when the carrier comes."), // 1
                new Para("The dish is turned by hand from the slew drive at the foot of the mast: one turn of the wheel, 15 degrees. The roof ladder is on the east wall."), // 2
                new Para("Set the dish on the relay azimuth from the buffer log and leave it until the carrier comes. Nothing goes out without the main feed."), // 3
                new Para("T. Brandt, Chief Engineer")));                                              // 4

        // ================= Пост медсестры B4 (шифр 9: подсказка; код — записка в палате 2D во сне) =================
        mail(ST_MED_B4, "m_panel", Folder.INBOX, 10, "sec.office", "nurse.b4", "28.08 09:10", "Lift panel B4", REAL, List.of(
                new Para("The lift to B5 is released only with the Director's code. Only the Director enters it."), // 0
                new Para("2D can see the panel from her window. She used to knock the digits; now she counts them in her sleep."), // 1
                new Para("Her room here is empty. Read the chalk note in it, then find the same room where she sleeps: the count is on the paper there."), // 2
                new Para("Security Office")));                                                        // 3
        mail(ST_MED_B4, "m_knock", Folder.SENT, 10, "nurse.b4", "h.voss", "01.09 05:40", "2D at night", REAL, List.of(
                new Para("Since they bricked the door up she stopped knocking. She says she counts in her sleep now, and the marks are on paper, in her room. Not this one."), // 0
                new Para("I asked her what the count is for. She says: the door."),                 // 1
                new Para("Night nurse, B4")));                                                        // 2
    }
}
