package com.hypersomnia.document;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Описание страниц: список элементов. Тексты по-английски; ключи lang: hypersomnia.doc.<id>.<номер элемента>
 * (запасной текст зашит здесь), заголовок предмета: hypersomnia.doc.<id>.title. Без клиентских импортов.
 */
public final class DocumentDefs {
    private DocumentDefs() {}

    /**
     * OFFICIAL: чистая лабораторная бумага. NOTE: мятая записка осколка.
     * TAPE: расшифровка аудиозаписи на той же бумаге; экран документа проигрывает её как кассету (TapeTuning).
     */
    public enum Style { OFFICIAL, NOTE, DRAWING, TAPE }

    public interface El {}
    public record Head(String text) implements El {}
    public record Para(String text) implements El {}
    /** Зачёркнутая строка. */
    public record Strike(String text) implements El {}
    public record Stamp(String text) implements El {}
    /** Слой 1: на каждую букву ряд черт счёта, число черт = номер буквы (A=1). Пробел = разрыв. */
    public record Letters(String text) implements El {}
    /** Шифр 3: бьющееся сердце и кардиограмма (низкий скачок = точка, высокий = тире). printed = false: только сердце, без печатной линии. */
    public record Morse(String text, boolean printed) implements El {
        public Morse(String text) { this(text, true); }
    }
    /** Слой 3: стрелки, символы U D L R. */
    public record Dirs(String dirs) implements El {}
    /** Шифр 1: текст из lang, слова между * отмечены чертой сверху; первые буквы отмеченных слов дают слово. */
    public record Marked(String text) implements El {}
    /** Шифр 2: алфавит черт (SignAlphabet, таблица своя в каждом мире). Текст из lang, поэтому на русском шифруется кириллицей. */
    public record Glyphs(String text) implements El {}
    /** Фрагмент ключа алфавита черт: знаки для перечисленных букв (тоже из lang). */
    public record GlyphKey(String letters) implements El {}
    public record Gap() implements El {}
    /** Моноширинная строка (журналы, бланки): каждый символ в ячейке одной ширины. Пока рисует только архив терминала. */
    public record Mono(String text) implements El {}

    /** Картинка на всю ширину страницы: textures/misc/<tex>.png, размер 176 x h. */
    public record Picture(String tex, int h) implements El {}

    /** Шифр 5: план зала наблюдения (камеры с линиями взгляда и посты), раскладка по зерну мира — LabVariants.posts. */
    public record Plan() implements El {}

    /**
     * Строка служебного перечня. Группа Row подряд показывается в порядке мира (WorldVariant, ключ = id страницы)
     * с номерами 1..n; ключ lang у каждой строки свой (её индекс элемента), поэтому перевод не зависит от порядка.
     */
    public record Row(String text) implements El {}

    /**
     * Шифр 7: лист проверки копии B2-CHK — цифра на каждое место хранилища (LabVariants.stacksSheet, по зерну мира),
     * ряды 1..STACK_ROWS сверху вниз, места 1..STACK_PLACES слева направо. labels = «ROW|PLACE» (ключ lang элемента).
     */
    public record CheckGrid(String labels) implements El {}

    /** Кромка листа: целая или рваная (половинка одного разорванного листа). */
    public static final int EDGE_NONE = 0;
    public static final int EDGE_RIGHT = 1;
    public static final int EDGE_LEFT = 2;

    /** edge: EDGE_NONE — обычный лист, EDGE_RIGHT/LEFT — половина листа с рваной кромкой. */
    public record Doc(String id, String title, Style style, List<El> els, int edge) {
        public Doc(String id, String title, Style style, List<El> els) {
            this(id, title, style, els, EDGE_NONE);
        }
    }

    private static final Map<String, Doc> DEFS = new LinkedHashMap<>();

    public static Doc get(String id) { return DEFS.get(id); }
    public static Collection<Doc> all() { return DEFS.values(); }

    /** Номер модели предмета = порядок add(...) + 1 (0 = запасной лист). Новые страницы только в конец, и строку в PAGES скрипта тоже. */
    public static int modelIndex(String id) {
        int i = 1;
        for (String k : DEFS.keySet()) {
            if (k.equals(id)) return i;
            i++;
        }
        return 0;
    }

    private static void add(Doc d) { DEFS.put(d.id(), d); }

    static {
        // Индексы элементов = номера ключей lang: hypersomnia.doc.<id>.<индекс>
        add(new Doc("morse_sample", "Observation Log 12-B", Style.OFFICIAL, List.of(
                new Head("CRADLE / FORM 12-B"),                                   // 0
                new Stamp("CLASSIFIED"),                                          // 1
                new Para("Subject 47. Pattern recorded during idle phase. Reference transmission below. Decoded: HELP."), // 2
                new Morse("HELP"),                                                // 3
                new Gap(),                                                        // 4
                new Para("Same transmission, alternate encoding."),               // 5
                new Glyphs("HELP"),                                               // 6: зацепка к алфавиту черт
                new Gap(),                                                        // 7
                new Para("Key: see the diary."))));                              // 8

        // Слой 1 (стадия 3): подсказки к рисункам Nora. После записки k Фрагмент оставляет у дома рисунок ObserverTuning.HINT_DRAWINGS[k]:
        // WINDOW — на стекле окна (drawing_0), BEHIND THE DOOR — за ближайшей дверью (drawing_3), UNDER THE BED — у кровати (drawing_1).
        add(new Doc("note_l1_0", "Page", Style.NOTE, List.of(
                new Para("Did I write this?"),                                    // 0
                new Marked("*Where* is she? *It* was here again. *Never* asleep, never. *Did* I close the door? *Once* more. *Waiting*."))));  // 1: WINDOW
        add(new Doc("note_l1_1", "Page", Style.NOTE, List.of(
                new Para("It was on the pillow again."),                          // 0
                new Glyphs("BEHIND THE DOOR"))));                                 // 1
        add(new Doc("note_l1_2", "Page", Style.NOTE, List.of(
                new Strike("It is not mine."),                                    // 0
                new Para("It is mine."),                                          // 1
                new Glyphs("UNDER THE BED"))));

        // Рисунки Nora (drawing_4 ниже, после воспоминаний). Картинка: textures/misc/<tex>.png, 176x132 (tools/make_drawings.py).
        add(new Doc("drawing_0", "Drawing", Style.DRAWING, List.of(new Picture("drawing_0", 132))));  // дом, окно закрашено
        add(new Doc("drawing_1", "Drawing", Style.DRAWING, List.of(
                new Picture("drawing_1", 132),                                    // 0: капсула
                new Para("daddy is sleeping"))));                                 // 1: подпись
        add(new Doc("drawing_2", "Drawing", Style.DRAWING, List.of(new Picture("drawing_2", 132))));  // солнце с закрытыми глазами
        add(new Doc("drawing_3", "Drawing", Style.DRAWING, List.of(new Picture("drawing_3", 132))));  // птица в открытой клетке
        add(new Doc("drawing_blank", "Unfinished Drawing", Style.DRAWING, List.of(new Picture("drawing_blank", 132))));// 2

        // Воспоминания 1-7 (MemoryTuning.IDS), затем не-воспоминания и рисунок 5. Порядок = PAGES в make_documents.py, новые только в конец.
        add(new Doc("mem_1_photo", "Photograph: Staff Event", Style.OFFICIAL, List.of(
                new Head("PHOTOGRAPH / ARCHIVE 0412"),                            // 0
                new Para("Image: two men at a staff event, shaking hands. E. Marsh, left. Director Halloran, right. Both smiling."), // 1
                new Para("Background: capsule 47. Placard: PROTOTYPE."),          // 2
                new Para("Date: before Contract C-47."),                          // 3
                new Head("REVERSE"),                                              // 4
                new Para("Elias, welcome to the team. - H."))));                  // 6
        add(new Doc("mem_2_discharge", "Discharge Summary: Nora Marsh", Style.OFFICIAL, List.of(
                new Head("DISCHARGE SUMMARY"),                                    // 0
                new Para("Patient: Marsh, Nora. Age: 8. Record No. 3341."),       // 1
                new Strike("Diagnosis: progressive cardiac failure, inoperable."), // 2
                new Stamp("CRADLE PRIORITY"),                                     // 3
                new Para("Attending: Dr. R. Lang. Date: 11 March. Recommendation: palliative care at home. Review in 30 days."), // 4
                new Gap(),                                                        // 5
                new Para("Patient's father requests transfer to experimental program. Approved."))));  // 6
        add(new Doc("mem_3_contract", "Contract C-47", Style.OFFICIAL, List.of(
                new Head("CONTRACT C-47"),                                        // 0
                new Para("Parties: Director Halloran (the Facility) and Dr. Elias Marsh (the Participant)."), // 1
                new Para("1. The Participant joins the Cradle programme as Subject 47."), // 2
                new Para("2. In return, the Facility grants Nora Marsh priority access to treatment."), // 3
                new Para("3. Sessions are scheduled at the Facility's discretion, usually at night."), // 4
                new Para("4. The Participant may not terminate a session early."), // 5
                new Para("5. Cognitive patterns recorded during the programme may be transferred to third parties."), // 6
                new Para("6. Records are stored for ten years."),                 // 7
                new Gap(),                                                        // 8
                new Para("Signed: E. Marsh   Halloran"),                          // 9
                new Stamp("APPROVED"))));                                         // 10
        add(new Doc("mem_4_session12", "Session 12", Style.TAPE, List.of(
                new Head("SESSION 12 / AUDIO LOG"),                               // 0
                new Stamp("TRANSCRIPT"),                                          // 1
                new Para("Timecode 03:12. Speaker: E. Marsh. Condition: calm, fatigued."), // 2
                new Para("She liked it when I stayed up. She said the night shift was our secret. █████ used to wait for me by the window."), // 3
                new Para("If this works, she gets more time. That is the whole calculation."), // 4
                new Para("I told █████ I would be there when she woke. I was. I was always there."))));  // 5
        add(new Doc("mem_5_memo", "Report: Anomaly 47-A", Style.OFFICIAL, List.of(
                new Head("REPORT / DR. H. VOSS"),                                 // 0
                new Para("Anomaly 47-A persists."),                               // 1
                new Para("No sensory input from outside the recording. It cannot be reached. It can only reach."), // 2
                new Para("Observed: the entity writes in the subject's hand and repeats a single question. I heard it from the corridor, with the recording off."), // 3
                new Para("I do not know what this means. Logged as fact."))));    // 4
        add(new Doc("mem_6_protocol", "Incident Protocol 47", Style.OFFICIAL, List.of(
                new Head("INCIDENT PROTOCOL 47"),                                 // 0
                new Stamp("RESTRICTED"),                                          // 1
                new Para("02:41 - subject 47 in session."),                       // 2
                new Para("03:07 - Nora Marsh, cardiac arrest."),                  // 3
                new Para("03:12 - pronounced dead."),                             // 4
                new Para("03:13 - session 47 continues."),                        // 5
                new Para("Subject sedated deeper per Director's order."))));      // 6
        add(new Doc("mem_7_log", "Operator's Last Log", Style.TAPE, List.of(
                new Head("OPERATOR / LAST LOG"),                                  // 0
                new Stamp("TRANSCRIPT"),                                          // 1
                new Para("No name needed. Recording."),                           // 2
                new Para("The girl would have died either way. I saw her numbers an hour before. Nothing in that room could have changed them."), // 3
                new Para("He could not wake. I ran the sequence four times. The Director ordered the sedation deeper and stood behind me until it was done."), // 4
                new Para("He couldn't have saved her. Nobody could. But he doesn't know that yet."), // 5
                new Para("If it can hear this: it was not his fault. This box is all I could save. Keep the lights on."))));  // 6

        // Не воспоминания: пропуск (оборот = ключ глазного алфавита), правка Voss, рисунок 5
        add(new Doc("employee_pass", "Employee Pass: E. Marsh", Style.OFFICIAL, List.of(
                new Head("CRADLE RESEARCH FACILITY"),                             // 0
                new Stamp("STAFF"),                                               // 1
                new Para("Name: Marsh, Elias. Dr."),                              // 2
                new Para("Position: neurophysiologist. Co-author, Cradle protocol. Clearance: level 4."), // 3
                new Gap(),                                                        // 4
                new Head("REVERSE"),                                              // 5
                new Para("Key: filed separately. See the diary."))));              // 6
        add(new Doc("voss_note", "Correction", Style.OFFICIAL, List.of(
                new Strike("Anomaly 47-A"),                                       // 0
                new Para("Fragment"))));                                          // 1
        add(new Doc("drawing_4", "Drawing", Style.DRAWING, List.of(new Picture("drawing_4", 132))));  // два папы, зайчик

        // Шифр 4 «Две стороны» (слой 2): две половинки одного листа. Текст разрезан посередине:
        // каждая строка левой половины продолжается на правой. Левая — в шкафу реального B1, правая — в том же шкафу
        // B1 мира снов (чертежи, символ a); обе ещё и среди записок Фрагмента (NOTE_PAGES).
        // Ключ: «the far door reads backwards» — код решётки блока 47-48 = время из bay_transfer задом наперёд.
        add(new Doc("note_l2_a", "Left Half", Style.NOTE, List.of(
                new Para("it starts at th"),                                      // строка 1: «...three. again.»
                new Para("the knocking ag"),                                      // строка 2: «...ain. it waits.»
                new Para("i went downstai"),                                      // строка 3: «...rs. the door.»
                new Para("the handle was "),                                      // строка 4: «...warm. it knew.»
                new Para("the far door rea"),                                     // строка 5: «...ds backwards.»
                new Para("do not answer i")), EDGE_RIGHT));                       // строка 5: «...t. - e.»
        add(new Doc("note_l2_b", "Right Half", Style.NOTE, List.of(
                new Para("ree. again."),                                          // 1
                new Para("ain. it waits."),                                       // 2
                new Para("rs. the door."),                                        // 3
                new Para("warm. it knew."),                                       // 4
                new Para("ds backwards."),                                        // 5
                new Para("t. - e.")), EDGE_LEFT));                                // 5

        // Шифр 5 «Куда смотрят» (мир снов, блок 47-48): по бумаге код — посты на концах линий камер 01..04;
        // записка: здесь камеры смотрят в обратную сторону, код — посты за их спиной (LabVariants.postCode).
        add(new Doc("plan_l5", "Level B1: Observation Room", Style.OFFICIAL, List.of(
                new Head("LEVEL B1 / OBSERVATION ROOM"),                          // 0
                new Plan(),                                                       // 1: раскладка по зерну мира
                new Para("Each camera covers one post. Access code: the covered posts, cameras 01 to 04."), // 2
                new Stamp("COPY"))));                                             // 3
        add(new Doc("note_l3", "Page", Style.NOTE, List.of(
                new Para("on paper they look where the lines go."),               // 0
                new Para("down here they look the other way."))));                // 1

        // Блок 47-48: приказ о переводе. 04:12 — запасной текст: время мира подставляет экран документа (LabVariants.bayTime).
        // Лежит в каталоге B1 мира снов. Код решётки — эти цифры задом наперёд (шифр 4, LabVariants.bayCode).
        add(new Doc("bay_transfer", "Transfer Order: Bays 47-48", Style.OFFICIAL, List.of(
                new Head("CRADLE RESEARCH FACILITY"),                              // 0
                new Para("Transfer of subjects 47 and 48 from the main catalog was approved at 04:12."), // 1
                new Para("Bay access code: 04:12."),                               // 2
                new Para("The room is not listed. Do not log the entrance."),      // 3
                new Stamp("RESTRICTED"))));                                        // 4

        // Шкафы лаборатории (строки cabinet в чертежах, LabCabinets): служебные бумаги реального центра.
        add(new Doc("cab_intake", "Intake Checklist", Style.OFFICIAL, List.of(
                new Head("CRADLE / FORM 03 / INTAKE"),                             // 0
                new Para("Complete at the gate before the subject is taken below. One copy stays at the post."), // 1
                new Para("[x] Consent form signed."),                              // 2
                new Para("[x] Personal effects removed and bagged."),              // 3
                new Para("[x] Watch removed. Subjects are not told the time."),    // 4
                new Para("[ ] Next of kin informed. Not required."),               // 5
                new Stamp("FILED"))));                                             // 6
        add(new Doc("cab_night_memo", "Memo: Night Shift", Style.OFFICIAL, List.of(
                new Head("INTERNAL MEMO"),                                         // 0
                new Para("To: night shift, all levels. From: Administration."),    // 1
                new Para("Sessions run from 01:00 to 05:00. During this window staff remain at their posts."), // 2
                new Para("Calls from the bays are logged automatically. Do not answer them. The log is reviewed in the morning."), // 3
                new Para("A subject who wakes during a session is reported, not assisted."), // 4
                new Stamp("CIRCULATED"))));                                        // 5
        add(new Doc("cab_evac", "Notice: Level B1", Style.OFFICIAL, List.of(
                new Head("NOTICE / LEVEL B1"),                                     // 0
                new Para("Level B1 has no stairwell. The lift is the only way to the surface."), // 1
                new Para("Without the main feed the lift serves G and B1 only."),  // 2
                new Para("In case of power loss, remain at your post. Do not open the lift doors by hand."), // 3
                new Para("Subjects in session are not moved."),                    // 4
                new Stamp("POSTED"))));                                            // 5
        add(new Doc("cab_maint", "Maintenance Requests", Style.OFFICIAL, List.of(
                new Head("ENGINEERING B1 / REQUESTS"),                             // 0
                new Para("0219. Corridor B1-02: lamp flickers. Closed: no fault found."), // 1
                new Para("0224. Corridor B1-02: lamp flickers only when the corridor is empty. Closed: no fault found."), // 2
                new Para("0231. Reception: chairs moved overnight. Closed: not a maintenance issue."), // 3
                new Para("0238. Lift: stops at B1 with no call. Doors open. Closed: no fault found."), // 4
                new Stamp("ARCHIVED"))));                                          // 5

        // Шкафы мира снов: вместо бумаг — записки.
        add(new Doc("cab_dnote_clock", "Page", Style.NOTE, List.of(
                new Para("they took the clock down at the gate."),                 // 0
                new Para("i can still hear it."))));                               // 1
        add(new Doc("cab_dnote_wake", "Page", Style.NOTE, List.of(
                new Strike("do not wake a subject."),                              // 0
                new Para("do not wake."))));                                       // 1
        add(new Doc("cab_dnote_lift", "Page", Style.NOTE, List.of(
                new Para("the lift stopped here. nobody called it."),              // 0
                new Para("the doors opened. nobody got out."))));                  // 1
        add(new Doc("cab_dnote_chair", "Page", Style.NOTE, List.of(
                new Para("someone sat in my chair."),                              // 0
                new Para("it was still warm. nobody is on shift."))));             // 1

        // Шифр 6 (реальный B1, Power Room, шкаф рядом с панелью RECORDER 47-OP). Строки Row = воспоминания 1..7
        // по порядку элементов, цифры — LabTuning.INDEX_DIGITS; на листе строки идут в порядке мира (WorldVariant).
        add(new Doc("op_index", "Index Card: Recorder 47-OP", Style.OFFICIAL, List.of(
                new Head("RECORDER 47-OP / INDEX"),                                // 0
                new Para("For each line, find the record. Take the last digit of the number asked for. Enter the digits from top to bottom."), // 1
                new Para("An incomplete index is not accepted. The panel runs on the main feed."), // 2
                new Gap(),                                                         // 3
                new Row("Photograph: the capsule in the background."),             // 4: 47 -> 7
                new Row("Discharge summary: the patient's age."),                  // 5: 8
                new Row("Contract C-47: the clause on ending a session early."),   // 6: 4
                new Row("Session 12: the timecode."),                              // 7: 03:12 -> 2
                new Row("Voss report: the number of the anomaly."),                // 8: 47-A -> 7
                new Row("Incident protocol: the time of the cardiac arrest."),     // 9: 03:07 -> 7
                new Row("Operator's last log: how many times the sequence was run."), // 10: four -> 4
                new Gap(),                                                         // 11
                new Stamp("OPERATOR COPY"))));                                     // 12
        // В ящике оператора (строка seal): открывается вместе с панелью RECORDER 47-OP.
        add(new Doc("op_retention", "Retention Notice: Session 47", Style.OFFICIAL, List.of(
                new Head("RECORDER 47-OP / RETENTION"),                            // 0
                new Para("Index accepted. Session 47, night record: retained."),   // 1
                new Para("The record is held outside the main catalog. It is not purged with the catalog."), // 2
                new Para("Status: prepared for playback."),                        // 3
                new Stamp("RETAINED"))));                                          // 4

        // Каталог капсул (мир снов, B1): выписка 41-50; 47 и 48 переведены приказом bay_transfer.
        add(new Doc("cat_extract", "Capsule Catalog: 41-50", Style.OFFICIAL, List.of(
                new Head("CAPSULE CATALOG / 41-50"),                               // 0
                new Para("41. Subject unresponsive."),                             // 1
                new Para("42. Cognitive collapse."),                               // 2
                new Para("43. Terminated during session."),                        // 3
                new Para("44. Dissociation, discharged."),                         // 4
                new Para("45. Sleep did not resume."),                             // 5
                new Para("46. Subject unresponsive."),                             // 6
                new Para("47. Transferred. See order."),                           // 7
                new Para("48. Transferred. See order."),                           // 8
                new Para("49-50. Not assigned."),                                  // 9
                new Stamp("CATALOG COPY"))));                                      // 10

        // Финал (finale/FinaleTuning): кассета «счастливого сна» — её приносит Фрагмент; шприц глубокой седации — блок 47-48.
        add(new Doc("tape_edited", "Tape 47-N (Edited)", Style.TAPE, List.of(
                new Head("TAPE 47-N / EDITED"),                                    // 0
                new Stamp("EDITED COPY"),                                          // 1
                new Para("Session 47, night. Restored for the subject's comfort."), // 2
                new Para("Content: daughter, healthy. Garden. Birthday. Laughter."), // 3
                new Para("Removed: 02:58 - 03:13. Remaining: 41 minutes. Loops."), // 4
                new Para("Play on the monitor in the room."))));                   // 5
        add(new Doc("sedation_47", "Syringe: Deep Sedation", Style.OFFICIAL, List.of(
                new Head("CRADLE PHARMACY"),                                       // 0
                new Para("Deep sedation: Subject 47, per Director."),              // 1
                new Para("Single dose. Administer in capsule 47 only."),           // 2
                new Para("Next session: confirm the number on the bay panel."),    // 3
                new Stamp("RESTRICTED"))));                                        // 4

        // Этажи B2-B5: по одному служебному листу на этаж (шкаф c чертежа). Только известные факты: 03:13, фидер 5, приоритет Cradle.
        // Шифр 7 (LIFT ACCESS B2): правило — здесь, цифры — лист b2_check, пустые места — в рядах хранилища.
        add(new Doc("cab_b2_copy", "Notice: Nightly Copy", Style.OFFICIAL, List.of(
                new Head("ARCHIVE B2 / NIGHTLY COPY"),                             // 0
                new Para("The main archive is copied to the workstations on every level at 03:13. The copy runs every night."), // 1
                new Para("Before the copy the operator on duty takes one box from each row of the store, rows 1 to 4, and brings them to the copy room."), // 2
                new Para("Check code of the copy: for each box, the digit for its row and place on sheet B2-CHK. Places are counted from the row sign. Row 1 first."), // 3
                new Para("The check code opens LIFT ACCESS B2: the copy tapes go down to the store on B3. After the copy the boxes go back to their places."), // 4
                new Stamp("POSTED"))));                                            // 5
        // Шифр 8 (LIFT ACCESS B3): как читать лампы; какие стойки и в каком порядке — BUFFER.LOG на станции srv_b3.
        add(new Doc("cab_b3_racks", "Servers: Racks", Style.OFFICIAL, List.of(
                new Head("SERVERS B3 / RACKS"),                                    // 0
                new Para("Racks 1-12 hold the session recordings. Numbers run from the sign at the head of each line."), // 1
                new Para("Status lamp, top of the rack: light - idle, red - buffer held, dark - off."), // 2
                new Para("If the main feed is lost, a recording in progress is held in the buffer of its rack. The buffer address is on the four lamps under the status lamp."), // 3
                new Para("Read them from the top: 8, 4, 2, 1. The address is the sum of the lit lamps."), // 4
                new Para("Do not switch off a rack while its buffer is held."),   // 5
                new Stamp("INTERNAL"))));                                          // 6
        add(new Doc("cab_b4_roster", "Ward Roster: Level B4", Style.OFFICIAL, List.of(
                new Head("MEDICAL WING B4 / WARD ROSTER"),                         // 0
                new Para("2A: two subjects. Observation after session."),          // 1
                new Para("2B: closed for cleaning."),                              // 2
                new Para("2C: empty."),                                            // 3
                new Para("2D: pediatric. One patient, transferred under Cradle priority. Visits after sessions only."), // 4
                new Stamp("INTERNAL"))));                                          // 5
        add(new Doc("cab_b5_session", "Session Hall: Rules", Style.OFFICIAL, List.of(
                new Head("SESSION HALL B5 / RULES"),                               // 0
                new Para("Sessions run from 01:00 to 05:00. The operator stays at the console until the session ends."), // 1
                new Para("A session is ended early only on the Director's order."), // 2
                new Para("Subjects in session are not moved."),                    // 3
                new Para("Hall lights stay off during a session."),                // 4
                new Stamp("INTERNAL"))));                                          // 5
        // Шифр 7: цифры мест хранилища (копировальная B2).
        add(new Doc("b2_check", "Copy Check Sheet B2-CHK", Style.OFFICIAL, List.of(
                new Head("B2-CHK / COPY CHECK DIGITS"),                            // 0
                new Para("One digit for each place in the store. For the nightly copy only."), // 1
                new CheckGrid("ROW|PLACE"),                                        // 2
                new Stamp("RECORDS B2"))));                                        // 3
        // Шифр 9: правило стука (палата 2D реального B4); сам стук — во сне, за заложенной дверью 2D (LabKnocks).
        add(new Doc("note_2d_knock", "Note: Ward 2D", Style.NOTE, List.of(
                new Para("2D knocked on the wall again tonight."),                 // 0
                new Para("Row, then column, like the buttons on the lift panel outside her window. Always the same four."), // 1
                new Para("She says she watched the Director press them."))));      // 2
    }
}