package com.hypersomnia.lang;

import com.hypersomnia.document.DocumentDefs;
import com.hypersomnia.finale.FinaleTexts;
import com.hypersomnia.finale.FinaleTuning;
import com.hypersomnia.lab.ArchiveDefs;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Собирает английские строки из запасных текстов кода (doc, archive, ending, finale, advancement)
 * в формат key<TAB>base64(value). Нужен для паритета en_us.json с ru_ru.json.
 * Запуск: gradlew -I src/devtest/lang.init.gradle dumpEnUs -PenUsOut=...
 */
public final class EnUsDump {
    private EnUsDump() {}

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "build/en_us_fallback.tsv");
        try (PrintWriter w = new PrintWriter(out.toFile(), StandardCharsets.UTF_8)) {
            documents(w);
            archive(w);
            finale(w);
        }
        System.out.println("wrote " + out);
    }

    private static void emit(PrintWriter w, String key, String value) {
        if (value == null) return;
        w.println(key + "\t" + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static void documents(PrintWriter w) {
        for (DocumentDefs.Doc d : DocumentDefs.all()) {
            emit(w, "hypersomnia.doc." + d.id() + ".title", d.title());
            for (int i = 0; i < d.els().size(); i++) {
                String text = docText(d.els().get(i));
                if (text != null) emit(w, "hypersomnia.doc." + d.id() + "." + i, text);
            }
        }
    }

    /** Текст элемента, для которого экран документа/терминала берёт ключ lang; null — ключа нет. */
    private static String docText(DocumentDefs.El el) {
        if (el instanceof DocumentDefs.Head e) return e.text();
        if (el instanceof DocumentDefs.Para e) return e.text();
        if (el instanceof DocumentDefs.Strike e) return e.text();
        if (el instanceof DocumentDefs.Stamp e) return e.text();
        if (el instanceof DocumentDefs.Morse e) return e.text();
        if (el instanceof DocumentDefs.Marked e) return e.text();
        if (el instanceof DocumentDefs.Glyphs e) return e.text();
        if (el instanceof DocumentDefs.GlyphKey e) return e.letters();
        if (el instanceof DocumentDefs.Row e) return e.text();
        if (el instanceof DocumentDefs.CheckGrid e) return e.labels();
        if (el instanceof DocumentDefs.Mono e) return e.text();
        return null;
    }

    private static void archive(PrintWriter w) {
        for (ArchiveDefs.Rec r : ArchiveDefs.all()) {
            emit(w, "hypersomnia.archive." + r.id() + ".title", r.title());
            for (int i = 0; i < r.body().size(); i++) {
                String text = docText(r.body().get(i));
                if (text != null) emit(w, "hypersomnia.archive." + r.id() + "." + i, text);
            }
        }
    }

    private static void finale(PrintWriter w) {
        for (int e = 0; e < FinaleTuning.KEYS.length; e++) {
            String key = FinaleTexts.key(e);
            emit(w, key + ".title", FinaleTexts.TITLES[e]);
            FinaleTexts.Line[] lines = FinaleTexts.LINES[e];
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].style() == 'b') continue;
                emit(w, key + ".line." + i, lines[i].text());
            }
            String[] cards = FinaleTexts.CARDS[e];
            for (int i = 0; i < cards.length; i++) emit(w, key + ".card." + i, cards[i]);
            emit(w, key + ".stamp", FinaleTexts.STAMPS[e]);
            emit(w, key + ".hint", FinaleTexts.HINTS[e]);
        }
        emit(w, "hypersomnia.finale.blank", FinaleTexts.BLANK_LINE);
        for (String[] kv : FinaleTexts.UI) emit(w, kv[0], kv[1]);
    }
}
