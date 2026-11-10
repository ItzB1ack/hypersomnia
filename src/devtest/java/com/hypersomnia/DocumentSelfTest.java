package com.hypersomnia;

import com.hypersomnia.diary.DiaryData;
import com.hypersomnia.diary.DiaryTuning;
import com.hypersomnia.document.DocumentDefs;
import com.hypersomnia.document.DrawingTuning;
import com.hypersomnia.document.EyeAlphabet;
import com.hypersomnia.document.MemoryTuning;
import com.hypersomnia.document.MorseCipher;
import com.hypersomnia.document.SignAlphabet;
import com.hypersomnia.document.WorldVariant;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Документы, шифры, дневник и найденное (N19). */
public final class DocumentSelfTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }

    public static void main(String[] args) {
        morse();
        eyeAlphabet();
        signAlphabet();
        documents();
        memoriesAndDrawings();
        progressPersistence();
        diary();
        System.out.println("DocumentSelfTest: " + checks + " checks passed.");
    }

    private static boolean isMorse(String code) {
        if (code.isEmpty()) return false;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c != '.' && c != '-') return false;
        }
        return true;
    }

    private static String decodeMorse(String text, Map<String, Character> table) {
        StringBuilder out = new StringBuilder();
        for (String code : text.trim().split("\\s+")) {
            if (code.isEmpty()) continue;
            Character c = table.get(code);
            if (c == null) return null;
            out.append(c);
        }
        return out.toString();
    }

    private static String encodeMorse(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) out.append(MorseCipher.code(c)).append(' ');
        return out.toString();
    }

    private static void morse() {
        Map<String, Character> latin = new HashMap<>();
        for (char c = 'A'; c <= 'Z'; c++) {
            String code = MorseCipher.code(c);
            check(isMorse(code), "latin code: " + c);
            check(code.length() <= 5, "latin code length: " + c);
            check(latin.put(code, c) == null, "latin code unique: " + c);
            check(MorseCipher.code(Character.toLowerCase(c)).equals(code), "lowercase latin: " + c);
        }
        check(latin.size() == 26, "26 latin codes");
        check("HELP".equals(decodeMorse(encodeMorse("HELP"), latin)), "morse latin round trip");

        Map<String, Character> cyrillic = new HashMap<>();
        for (char c = '\u0410'; c <= '\u042F'; c++) {
            String code = MorseCipher.code(c);
            check(isMorse(code), "cyrillic code: " + c);
            check(code.length() <= 5, "cyrillic code length: " + c);
            check(cyrillic.put(code, c) == null, "cyrillic code unique: " + c);
            check(MorseCipher.code(Character.toLowerCase(c)).equals(code), "lowercase cyrillic: " + c);
        }
        check(cyrillic.size() == 32, "32 cyrillic codes");
        check("\u041F\u0420\u0418\u0412\u0415\u0422".equals(
                decodeMorse(encodeMorse("\u041F\u0420\u0418\u0412\u0415\u0422"), cyrillic)),
                "morse cyrillic round trip");

        Set<String> digits = new HashSet<>();
        for (char c = '0'; c <= '9'; c++) {
            String code = MorseCipher.code(c);
            check(code.length() == 5, "digit code length: " + c);
            check(digits.add(code), "digit code unique: " + c);
        }
        check(digits.size() == 10, "10 digit codes");
        check(MorseCipher.code('0').equals("-----"), "zero code");
        check(MorseCipher.code('9').equals("----."), "nine code");

        check(MorseCipher.code('\u0401').equals(MorseCipher.code('\u0415')), "Ё is Е");
        check(MorseCipher.code('\u0451').equals(MorseCipher.code('\u0415')), "lowercase ё is Е");
        check(MorseCipher.code(' ').isEmpty(), "space has no code");
        check(MorseCipher.code('.').isEmpty(), "punctuation has no code");
        check(MorseCipher.code('\n').isEmpty(), "newline has no code");
    }

    private static void eyeAlphabet() {
        check(EyeAlphabet.count() == 36, "eye alphabet has 36 signs");
        Set<Integer> latin = new HashSet<>();
        for (char c = 'A'; c <= 'Z'; c++) {
            int idx = EyeAlphabet.indexOf(c);
            check(idx >= 0 && idx < EyeAlphabet.count(), "eye latin index: " + c);
            check(latin.add(idx), "eye latin index unique: " + c);
            EyeAlphabet.Sym s = EyeAlphabet.symbol(c);
            check(s != null && s.equals(EyeAlphabet.byIndex(idx)), "eye latin symbol round trip: " + c);
            check(EyeAlphabet.indexOf(Character.toLowerCase(c)) == idx, "eye lowercase: " + c);
        }
        check(latin.size() == 26, "26 eye latin signs");

        Set<Integer> cyrillic = new HashSet<>();
        for (char c = '\u0410'; c <= '\u042F'; c++) {
            int idx = EyeAlphabet.indexOf(c);
            check(idx >= 0 && idx < EyeAlphabet.count(), "eye cyrillic index: " + c);
            check(cyrillic.add(idx), "eye cyrillic index unique: " + c);
            check(EyeAlphabet.byIndex(idx).equals(EyeAlphabet.symbol(c)), "eye cyrillic round trip: " + c);
        }
        check(cyrillic.size() == 32, "32 eye cyrillic signs");

        Set<EyeAlphabet.Sym> all = new HashSet<>();
        for (int i = 0; i < EyeAlphabet.count(); i++) all.add(EyeAlphabet.byIndex(i));
        check(all.size() == EyeAlphabet.count(), "all eye signs distinct");
        check(EyeAlphabet.byIndex(-1).equals(EyeAlphabet.byIndex(EyeAlphabet.count() - 1)),
                "eye index wraps below zero");
        check(EyeAlphabet.byIndex(EyeAlphabet.count()).equals(EyeAlphabet.byIndex(0)),
                "eye index wraps above count");
        check(EyeAlphabet.indexOf('\u0401') == EyeAlphabet.indexOf('\u0415'), "eye Ё is Е");
        check(EyeAlphabet.indexOf(' ') == -1, "eye space has no index");
        check(EyeAlphabet.symbol(' ') == null, "eye space has no symbol");
    }

    private static void signAlphabet() {
        check(SignAlphabet.count() == 36, "sign alphabet has 36 signs");
        long[] seeds = {0L, 1L, 47L, 1947L, Long.MIN_VALUE, Long.MAX_VALUE};
        for (long seed : seeds) {
            Set<Integer> latin = new HashSet<>();
            for (char c = 'A'; c <= 'Z'; c++) {
                int idx = SignAlphabet.indexOf(seed, c);
                check(idx >= 0 && idx < SignAlphabet.count(), "sign latin index: " + c);
                check(latin.add(idx), "sign latin index unique: " + c);
                check(SignAlphabet.byIndex(idx).equals(SignAlphabet.symbol(seed, c)),
                        "sign latin round trip: " + c);
            }
            Set<Integer> cyrillic = new HashSet<>();
            for (char c = '\u0410'; c <= '\u042F'; c++) {
                int idx = SignAlphabet.indexOf(seed, c);
                check(idx >= 0 && idx < SignAlphabet.count(), "sign cyrillic index: " + c);
                check(cyrillic.add(idx), "sign cyrillic index unique: " + c);
                check(SignAlphabet.byIndex(idx).equals(SignAlphabet.symbol(seed, c)),
                        "sign cyrillic round trip: " + c);
            }
            check(SignAlphabet.indexOf(seed, '\u0401') == SignAlphabet.indexOf(seed, '\u0415'),
                    "sign Ё is Е");
            check(SignAlphabet.indexOf(seed, ' ') == -1, "sign space has no index");
        }

        Set<SignAlphabet.Sym> all = new HashSet<>();
        for (int i = 0; i < SignAlphabet.count(); i++) all.add(SignAlphabet.byIndex(i));
        check(all.size() == SignAlphabet.count(), "all sign glyphs distinct");
        check(SignAlphabet.byIndex(-1).equals(SignAlphabet.byIndex(SignAlphabet.count() - 1)),
                "sign index wraps below zero");
        check(SignAlphabet.byIndex(SignAlphabet.count()).equals(SignAlphabet.byIndex(0)),
                "sign index wraps above count");
        check(SignAlphabet.isLetter('A') && SignAlphabet.isLetter('\u042F')
                && SignAlphabet.isLetter('\u0401'), "letters are letters");
        check(!SignAlphabet.isLetter(' ') && !SignAlphabet.isLetter('1')
                && !SignAlphabet.isLetter('.'), "non-letters are not letters");

        for (int n : new int[]{0, 1, 7, 36, 100}) {
            int[] order = WorldVariant.order(47L, "signs_latin", n);
            check(order.length == n, "order length " + n);
            boolean[] seen = new boolean[n];
            for (int v : order) {
                check(v >= 0 && v < n && !seen[v], "order is a permutation " + n);
                if (v >= 0 && v < n) seen[v] = true;
            }
            check(Arrays.equals(order, WorldVariant.order(47L, "signs_latin", n)),
                    "order is deterministic " + n);
        }
        check(!Arrays.equals(WorldVariant.order(47L, "a", 36), WorldVariant.order(47L, "b", 36)),
                "different keys give different orders");
        check(WorldVariant.order(47L, "a", -5).length == 0, "negative size gives empty order");
    }

    private static String fallback(DocumentDefs.El el) {
        if (el instanceof DocumentDefs.Head e) return e.text();
        if (el instanceof DocumentDefs.Para e) return e.text();
        if (el instanceof DocumentDefs.Strike e) return e.text();
        if (el instanceof DocumentDefs.Stamp e) return e.text();
        if (el instanceof DocumentDefs.Letters e) return e.text();
        if (el instanceof DocumentDefs.Morse e) return e.text();
        if (el instanceof DocumentDefs.Dirs e) return e.dirs();
        if (el instanceof DocumentDefs.Marked e) return e.text();
        if (el instanceof DocumentDefs.Glyphs e) return e.text();
        if (el instanceof DocumentDefs.GlyphKey e) return e.letters();
        if (el instanceof DocumentDefs.Mono e) return e.text();
        if (el instanceof DocumentDefs.Row e) return e.text();
        if (el instanceof DocumentDefs.CheckGrid e) return e.labels();
        return null;
    }

    private static void documents() {
        Collection<DocumentDefs.Doc> all = DocumentDefs.all();
        check(all.size() >= 40, "documents are defined");
        Set<String> ids = new HashSet<>();
        int index = 1;
        for (DocumentDefs.Doc doc : all) {
            check(ids.add(doc.id()), "document id unique: " + doc.id());
            check(doc.title() != null && !doc.title().isBlank(), "document title: " + doc.id());
            check(DocumentDefs.modelIndex(doc.id()) == index, "model index order: " + doc.id());
            check(!doc.els().isEmpty(), "document has elements: " + doc.id());
            for (DocumentDefs.El el : doc.els()) {
                String text = fallback(el);
                if (text != null) check(!text.isBlank(), "fallback is not empty: " + doc.id() + " " + el);
            }
            index++;
        }
        check(DocumentDefs.modelIndex("no_such_doc") == 0, "missing document has model zero");
        check(DocumentDefs.get("no_such_doc") == null, "missing document is null");

        for (String id : ObserverTuning.NOTE_PAGES) check(DocumentDefs.get(id) != null, "note page: " + id);
        for (String id : MemoryTuning.IDS) check(DocumentDefs.get(id) != null, "memory page: " + id);
        for (int i = 0; i <= DrawingTuning.BLANK_INDEX; i++)
            check(DocumentDefs.get(DrawingTuning.idOf(i)) != null, "drawing page: " + i);
        for (DiaryTuning.Photo photo : DiaryTuning.PHOTOS.values())
            check(DocumentDefs.get(photo.source()) != null, "photo source: " + photo.source());

        DocumentDefs.Doc sample = DocumentDefs.get("morse_sample");
        check(sample != null, "morse sample exists");
        boolean hasMorse = false;
        boolean hasGlyphs = false;
        if (sample != null) {
            for (DocumentDefs.El el : sample.els()) {
                if (el instanceof DocumentDefs.Morse m && m.text().equals("HELP")) hasMorse = true;
                if (el instanceof DocumentDefs.Glyphs g && g.text().equals("HELP")) hasGlyphs = true;
            }
        }
        check(hasMorse, "morse sample has HELP");
        check(hasGlyphs, "morse sample repeats HELP in glyphs");
    }

    private static void memoriesAndDrawings() {
        check(MemoryTuning.COUNT == 7 && MemoryTuning.IDS.length == 7, "seven memories");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < MemoryTuning.COUNT; i++) {
            check(MemoryTuning.indexOf(MemoryTuning.IDS[i]) == i, "memory index: " + i);
            check(ids.add(MemoryTuning.IDS[i]), "memory id unique: " + i);
        }
        check(MemoryTuning.indexOf("mem_0") == -1, "unknown memory");
        check(MemoryTuning.indexOf("") == -1, "empty memory id");
        check(MemoryTuning.indexOf(null) == -1, "null memory id");

        check(DrawingTuning.COUNT == 5 && DrawingTuning.BLANK_INDEX == 5, "five drawings and a blank");
        for (int i = 0; i <= DrawingTuning.BLANK_INDEX; i++) {
            check(DrawingTuning.indexOf(DrawingTuning.idOf(i)) == i, "drawing index: " + i);
        }
        check(DrawingTuning.idOf(DrawingTuning.BLANK_INDEX).equals(DrawingTuning.BLANK_ID), "blank id");
        check(DrawingTuning.indexOf("drawing_5") == -1, "drawing five is blank only");
        check(DrawingTuning.indexOf("drawing_-1") == -1, "negative drawing");
        check(DrawingTuning.indexOf("drawing_x") == -1, "non-numeric drawing");
        check(DrawingTuning.indexOf("drawing_00") == 0, "leading zero drawing");
        check(DrawingTuning.indexOf("") == -1, "empty drawing id");
    }

    private static void progressPersistence() {
        ObserverProgressData data = new ObserverProgressData();
        for (int i = 0; i < MemoryTuning.COUNT; i++) data.addMemory(i);
        check(data.memoriesCount() == MemoryTuning.COUNT, "all memories found");
        data.addMemory(0);
        check(data.memoriesCount() == MemoryTuning.COUNT, "repeated memory is idempotent");
        data.addMemory(-1);
        data.addMemory(MemoryTuning.COUNT);
        check(data.memoriesCount() == MemoryTuning.COUNT, "invalid memory is ignored");

        for (int i = 0; i < ObserverTuning.NOTE_PAGES.length; i++) data.addNote(i);
        check(data.notesCount() == ObserverTuning.NOTE_PAGES.length, "all notes found");
        data.addNote(0);
        check(data.notesCount() == ObserverTuning.NOTE_PAGES.length, "repeated note is idempotent");
        data.addNote(-1);
        data.addNote(ObserverTuning.NOTE_PAGES.length);
        check(data.notesCount() == ObserverTuning.NOTE_PAGES.length, "invalid note is ignored");

        for (int i = 0; i <= DrawingTuning.BLANK_INDEX; i++) data.addDrawing(i);
        check(data.drawingsCount() == DrawingTuning.COUNT, "all drawings found");
        check(data.hasBlank(), "blank drawing found");
        data.addDrawing(0);
        check(data.drawingsCount() == DrawingTuning.COUNT, "repeated drawing is idempotent");
        data.addDrawing(-1);
        data.addDrawing(DrawingTuning.BLANK_INDEX + 1);
        check(data.drawingsCount() == DrawingTuning.COUNT, "invalid drawing is ignored");

        data.setDays(3);
        data.setPending(2, 1);
        data.markGifted(4);
        data.setEnding(1);
        data.setTruth(true);
        data.setStageOverride(4);
        CompoundTag tag = data.serializeNBT(null);

        ObserverProgressData loaded = new ObserverProgressData();
        loaded.deserializeNBT(null, tag);
        check(loaded.getDays() == 3, "days survive reload");
        check(loaded.memoriesCount() == MemoryTuning.COUNT, "memories survive reload");
        check(loaded.notesCount() == ObserverTuning.NOTE_PAGES.length, "notes survive reload");
        check(loaded.drawingsCount() == DrawingTuning.COUNT && loaded.hasBlank(),
                "drawings survive reload");
        check(loaded.getPendingNote() == 2 && loaded.getPendingWay() == 1, "pending note survives reload");
        check(loaded.giftedMask() == (1 << 4), "gifted memory survives reload");
        check(loaded.ending() == 1 && loaded.truth(), "finale survives reload");
        check(loaded.stageOverride() == 4, "stage override survives reload");

        CompoundTag broken = new CompoundTag();
        broken.putInt("memories", 0xFFFF);
        broken.putInt("notes", 0xFFFF);
        broken.putInt("drawings", 0xFFFF);
        broken.putInt("gifted", 0xFFFF);
        ObserverProgressData bad = new ObserverProgressData();
        bad.deserializeNBT(null, broken);
        check(bad.memoriesCount() == MemoryTuning.COUNT, "corrupt memories are masked");
        check(bad.notesCount() == ObserverTuning.NOTE_PAGES.length, "corrupt notes are masked");
        check(bad.drawingsCount() == DrawingTuning.COUNT && bad.hasBlank(), "corrupt drawings are masked");
        check(Integer.bitCount(bad.giftedMask()) == MemoryTuning.COUNT, "corrupt gifts are masked");

        ObserverProgressData notes = new ObserverProgressData();
        check(notes.nextFreeNote(2) == -1, "notes locked before stage three");
        check(notes.nextFreeNote(3) == 0, "first note at stage three");
        notes.addNote(0);
        check(notes.nextFreeNote(3) == 1, "second note at stage three");
        notes.addNote(1);
        check(notes.nextFreeNote(3) == 2, "third note at stage three");
        notes.addNote(2);
        check(notes.nextFreeNote(3) == -1, "layer two locked at stage three");
        check(notes.nextFreeNote(4) == 3, "layer two opens at stage four");
        notes.addNote(3);
        notes.addNote(4);
        check(notes.nextFreeNote(4) == -1, "last note locked at stage four");
        check(notes.nextFreeNote(5) == 5, "last note opens at stage five");
        notes.addNote(5);
        check(notes.nextFreeNote(5) == -1, "all notes handed out");
    }

    private static void diary() {
        DiaryData data = new DiaryData();
        data.place("doc_a", 0, -5, 9999);
        check(data.entries().size() == 1, "first sheet placed");
        DiaryData.Entry first = data.entries().get(0);
        check(first.page() == 1, "page is clamped up");
        check(first.x() == 0, "x is clamped left");
        check(first.y() == DiaryTuning.PAPER_H - DiaryTuning.THUMB_MIN, "y is clamped down");

        data.place("doc_a", 3, 10, 10);
        check(data.entries().size() == 1, "repeated placement does not duplicate");
        check(data.entries().get(0).page() == 3, "repeated placement moves the sheet");

        data.place("doc_b", 999, 999, -1);
        check(data.entries().size() == 2, "second sheet placed");
        DiaryData.Entry second = data.entries().get(1);
        check(second.page() == DiaryTuning.PAGES, "page is clamped to the last");
        check(second.x() == DiaryTuning.PAPER_W - DiaryTuning.THUMB_MIN, "x is clamped right");
        check(second.y() == 0, "y is clamped up");
        data.place("", 1, 1, 1);
        data.place(null, 1, 1, 1);
        check(data.entries().size() == 2, "empty id is ignored");

        DiaryData full = new DiaryData();
        for (int i = 0; i < DiaryTuning.MAX_ENTRIES; i++) full.place("id_" + i, 1, 1, 1);
        check(full.entries().size() == DiaryTuning.MAX_ENTRIES, "sheet limit reached");
        full.place("id_new", 1, 1, 1);
        check(!full.has("id_new") && full.entries().size() == DiaryTuning.MAX_ENTRIES,
                "new sheet over the limit is ignored");
        full.place("id_0", 5, 20, 20);
        check(full.has("id_0") && full.entries().size() == DiaryTuning.MAX_ENTRIES,
                "existing sheet still moves at the limit");
        full.remove("id_0");
        check(!full.has("id_0") && full.entries().size() == DiaryTuning.MAX_ENTRIES - 1,
                "sheet is removed");

        List<DiaryData.Entry> loaded = DiaryData.fromStrings(data.toStrings());
        check(loaded.size() == 2, "sheet strings round trip");
        check(loaded.get(0).page() == 3 && loaded.get(1).page() == DiaryTuning.PAGES,
                "sheet pages survive strings");

        List<DiaryData.Entry> junk = DiaryData.fromStrings(List.of(
                "", "a|b|c|d", "a|1|2", "a|1|2|3|4", "ok|2|3|4", "ok|4|5|6"));
        check(junk.size() == 1, "malformed and duplicate sheets are cleaned");
        check(junk.get(0).page() == 4 && junk.get(0).x() == 5 && junk.get(0).y() == 6,
                "last duplicate wins");

        List<String> many = new ArrayList<>();
        for (int i = 0; i < DiaryTuning.MAX_ENTRIES + 10; i++) many.add("s_" + i + "|1|1|1");
        check(DiaryData.fromStrings(many).size() == DiaryTuning.MAX_ENTRIES, "string list is capped");

        data.setKey('A', 3);
        data.setKey('B', 3);
        check(data.keySym('A') == null && data.keySym('B') == 3, "one sign belongs to one letter");
        data.setKey('b', 4);
        check(data.keySym('B') == 4, "letter is uppercased");
        data.setKey('1', 5);
        check(data.keySym('1') == null, "non-letter has no key");
        data.setKey('C', -1);
        check(data.keySym('C') == null, "negative sign clears the key");
        data.setKey('C', 7);

        Map<Character, Integer> key = DiaryData.keyFromStrings(data.keyStrings());
        check(key.get('B') == 4 && key.get('C') == 7, "key strings round trip");
        Map<Character, Integer> junkKey = DiaryData.keyFromStrings(List.of(
                "A", "A=x", "1=2", "A=-1", "A=99", "A=5", "B=5"));
        check(junkKey.size() == 1 && junkKey.get('B') == 5, "malformed keys are cleaned");

        CompoundTag tag = data.serializeNBT(null);
        DiaryData reloaded = new DiaryData();
        reloaded.deserializeNBT(null, tag);
        check(reloaded.entries().size() == data.entries().size(), "sheets survive NBT");
        check(reloaded.keySym('B') == 4 && reloaded.keySym('C') == 7, "key survives NBT");

        tag.putInt("kv", DiaryTuning.KEY_VERSION - 1);
        DiaryData old = new DiaryData();
        old.deserializeNBT(null, tag);
        check(old.entries().size() == data.entries().size(), "old key version keeps sheets");
        check(old.keySym('B') == null && old.keySym('C') == null, "old key version resets the table");

        CompoundTag corrupt = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < DiaryTuning.MAX_ENTRIES + 12; i++) {
            CompoundTag c = new CompoundTag();
            c.putString("id", i == 0 ? "" : "id_" + i);
            c.putInt("page", 0);
            c.putInt("x", -5);
            c.putInt("y", 9999);
            list.add(c);
        }
        corrupt.put("e", list);
        corrupt.putInt("kv", DiaryTuning.KEY_VERSION);
        CompoundTag keys = new CompoundTag();
        keys.putInt("1", 5);
        keys.putInt("A", 99);
        keys.putInt("B", 3);
        keys.putInt("C", 3);
        keys.putInt("D", 4);
        corrupt.put("k", keys);
        DiaryData broken = new DiaryData();
        broken.deserializeNBT(null, corrupt);
        check(broken.entries().size() == DiaryTuning.MAX_ENTRIES, "corrupt sheets are capped");
        DiaryData.Entry cleaned = broken.entries().get(0);
        check(cleaned.page() == 1 && cleaned.x() == 0 && cleaned.y() == DiaryTuning.PAPER_H - DiaryTuning.THUMB_MIN,
                "corrupt sheet is clamped");
        check(broken.keySym('1') == null, "non-letter key is dropped");
        check(broken.keySym('A') == null, "out-of-range sign is dropped");
        check(broken.keySym('C') == 3 && broken.keySym('B') == null, "last letter keeps a shared sign");
        check(broken.keySym('D') == 4, "valid key survives");
    }
}
