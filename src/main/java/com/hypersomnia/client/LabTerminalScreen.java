package com.hypersomnia.client;

import com.hypersomnia.document.DocumentDefs;
import com.hypersomnia.dream.DreamDimension;
import com.hypersomnia.lab.ArchiveDefs;
import com.hypersomnia.lab.ArchiveDefs.App;
import com.hypersomnia.lab.ArchiveDefs.Folder;
import com.hypersomnia.lab.ArchiveDefs.Rec;
import com.hypersomnia.lab.ArchiveDefs.Station;
import com.hypersomnia.lab.LabTuning;
import com.hypersomnia.lab.LabVariants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CRADLE WORKSTATION: экран рабочей станции. Вход в систему, рабочий стол со значками приложений и панелью задач,
 * развёрнутое окно приложения (Mail, Local Archive, Logs, Documents): папки, список записей и встроенный просмотрщик.
 * Набор приложений и записей задаёт ArchiveDefs.Station. Открываются только записи из снимка сервера; сам доступ экран не выдаёт.
 * Пароль сохранён на станции: вход — только подтверждение.
 */
public final class LabTerminalScreen extends Screen {
    // Палитра мода + производные тона
    private static final int VEIL = 0xE003050A;
    private static final int BAR = 0xFF03050A;
    private static final int PANEL = 0xFF1E2A34;
    private static final int LIGHT = 0xFF4A5E70;
    private static final int SHADE = 0xFF03050A;
    private static final int WELL = 0xFF0A0E12;
    private static final int TEXT = 0xFFDDE6EC;
    private static final int SOFT = 0xFFC4CED6;
    private static final int SUB = 0xFF8E9DA8;
    private static final int DIM = 0xFF4E5A64;
    private static final int RED = 0xFF8A2030;
    private static final int SEL = 0xFF4A5E70;
    private static final int SEL_IDLE = 0xFF2A353E;
    private static final int HOVER = 0xFF141C24;
    private static final int HOT = 0xFF24323E;
    /** Обои рабочего стола и надпись CRADLE на них. */
    private static final int WALL = 0xFF101820;
    private static final int MARK = 0xFF1B2631;

    private static final int MAX_W = 480, MAX_H = 300, MARGIN = 8, NARROW_W = 320;
    private static final int BAR_H = 12, STATUS_H = 12, TREE_W = 104, ROW = 11, TREE_ROW = 12, TAB_H = 12, TASK_H = 16;
    /** Пределы ширины фиксированных колонок (по содержимому, но не шире) и минимум для растягиваемой. */
    private static final int COL_TYPE = 70, COL_DATE = 70, COL_ACCESS = 76, COL_FROM = 100, COL_SOURCE = 80, COL_FLEX_MIN = 110;
    private static final int SCROLL_W = 6, DOC_PAD = 6, ICON_W = 60, ICON_H = 44;
    private static final int LOGON_W = 236, LOGON_H = 124, BTN_W = 50, BTN_H = 13;
    private static final int FOCUS_TREE = 0, FOCUS_LIST = 1;
    private static final int LOGON = 0, PROFILE = 1, DESKTOP = 2;
    private static final long REOPEN_GUARD_MS = 250;

    // ---- Память между открытиями (в пределах запуска игры). Ключ — станция и мир: во сне свой сеанс. ----
    private static final int DREAM_SESSION = 1_000_000;
    /** Станции, где уже выполнен вход. LOG OFF убирает станцию отсюда. */
    private static final Set<Integer> loggedOn = new HashSet<>();
    /** Окно, открытое при закрытии экрана: станция -> приложение. */
    private static final Map<Integer, App> lastApp = new HashMap<>();
    /** Папка приложения: "станция:приложение" -> папка. */
    private static final Map<String, Folder> lastFolder = new HashMap<>();
    /** Выделение и прокрутка списка: "станция:папка". */
    private static final Map<String, String> selected = new HashMap<>();
    private static final Map<String, Integer> listScroll = new HashMap<>();
    /** Открытые письма ("сеанс:id"): остальные во входящих — непрочитанные. */
    private static final Set<String> readMail = new HashSet<>();
    /** Выделенный значок рабочего стола: станция -> номер. */
    private static final Map<Integer, Integer> deskIcon = new HashMap<>();
    private static int focus = FOCUS_LIST;
    private static boolean indexRead;

    private final Station station;
    /** Ключ памяти сеанса: id станции, во сне со сдвигом DREAM_SESSION. */
    private final int sk;
    private final Set<String> listed;
    private final Set<String> readable;
    private final List<Rec> rows = new ArrayList<>();
    private int mode;
    private int profile;
    /** Открытое приложение (null — только рабочий стол); minimized — окно свёрнуто на панель задач. */
    private App app;
    private boolean minimized;
    private Folder folder;
    private int icon;
    private int loading;
    private int flash;
    /** Время последнего перехода (вход, окно, запись): второй щелчок двойного клика не срабатывает. */
    private long guardAt;

    // ---- Просмотрщик ----
    private Rec viewing;
    private int docScroll;
    private int layoutWidth = -1;
    private final List<Line> lines = new ArrayList<>();
    private int docHeight;
    /** Ширина ячейки Mono для открытой записи (px GUI). */
    private int monoCell = LabTuning.ARCHIVE_MONO_CELL;
    /** Ширина подписей полей шапки (From:, To:...) открытой записи. */
    private int fieldW;

    /**
     * Строка разметки документа: текст, цвет, вид (0 текст, 1 заголовок/линия, 2 штамп, 3 Mono, 4 поле шапки), высота.
     * Поле шапки: plain = подпись, value = значение.
     */
    private record Line(FormattedCharSequence text, String plain, String value, int color, int kind, int h) {}

    /** Колонка списка: заголовок и ширина (px GUI). */
    private record Col(String head, int w) {}

    private LabTerminalScreen(Station station, List<String> listed, List<String> readable) {
        super(ui("title", "CRADLE WORKSTATION"));
        this.station = station;
        Minecraft mc = Minecraft.getInstance();
        this.sk = station.id() + (mc.level != null && mc.level.dimension() == DreamDimension.LEVEL ? DREAM_SESSION : 0);
        this.listed = new HashSet<>(listed);
        this.readable = new HashSet<>(readable);
        this.icon = Math.max(0, Math.min(station.apps().size() - 1, deskIcon.getOrDefault(sk, 0)));
        if (loggedOn.contains(sk)) {
            mode = DESKTOP;
            App a = lastApp.get(sk);
            if (a != null && station.apps().contains(a)) openApp(a);
        } else {
            mode = LOGON;
        }
    }

    /** Вызывать только на клиенте. terminal = id станции (ArchiveDefs.Station); неизвестная станция не открывается. */
    public static void receive(int terminal, List<String> listed, List<String> readable) {
        Station st = ArchiveDefs.station(terminal);
        if (st == null) return;
        Minecraft.getInstance().setScreen(new LabTerminalScreen(st, listed, readable));
    }

    private static Component ui(String key, String fallback) {
        return Component.translatableWithFallback("hypersomnia.archive.ui." + key, fallback);
    }

    private static String uis(String key, String fallback) {
        return ui(key, fallback).getString();
    }

    private static String name(Rec r) {
        return Component.translatableWithFallback("hypersomnia.archive." + r.id() + ".title", r.title()).getString();
    }

    private static String appName(App a) {
        return Component.translatableWithFallback("hypersomnia.archive.app." + a.key, a.fallback).getString();
    }

    private static String folderName(Folder f) {
        return Component.translatableWithFallback("hypersomnia.archive.section." + f.key, f.fallback).getString();
    }

    private static String typeName(Rec r) {
        return Component.translatableWithFallback(r.typeKey(), r.type()).getString();
    }

    private String place() {
        return Component.translatableWithFallback("hypersomnia.archive.station." + station.key(), station.place()).getString();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** Вызывается и при смене размера окна / GUI Scale: прокрутка и выделение снова в допустимых пределах. */
    @Override
    protected void init() {
        super.init();
        if (app != null) {
            clampList();
            int sel = selIndex();
            if (sel >= 0) select(sel);
        }
        layoutWidth = -1;
    }

    @Override
    public void removed() {
        super.removed();
        if (app != null && loggedOn.contains(sk)) lastApp.put(sk, app);
        else lastApp.remove(sk);
        deskIcon.put(sk, icon);
    }

    // ================= Раскладка =================

    /** Экран станции (вся «картинка монитора»): по центру, не больше MAX_W x MAX_H. */
    private int dw() { return Math.min(width - MARGIN * 2, MAX_W); }
    private int dh() { return Math.min(height - MARGIN * 2, MAX_H); }
    private int dx() { return (width - dw()) / 2; }
    private int dy() { return (height - dh()) / 2; }
    private int taskY() { return dy() + dh() - TASK_H; }
    private boolean narrow() { return dw() < NARROW_W; }

    /** Окно приложения развёрнуто на весь рабочий стол над панелью задач. */
    private int wx() { return dx(); }
    private int wy() { return dy(); }
    private int ww() { return dw(); }
    private int wh() { return dh() - TASK_H; }
    private boolean windowShown() { return mode == DESKTOP && app != null && !minimized; }
    private boolean hasTree() { return app != null && Folder.of(app).size() > 1; }

    private int statusY() { return wy() + wh() - STATUS_H - 2; }
    private int bodyY() { return wy() + 1 + BAR_H; }
    private int closeX() { return wx() + ww() - 13; }
    private int closeY() { return wy() + 2; }

    private int treeX() { return wx() + 4; }
    private int treeY() { return bodyY() + 4; }
    private int treeH() { return statusY() - 3 - treeY(); }

    /** Ширина дерева папок: по самой длинной подписи (со счётчиком писем), не меньше TREE_W и не больше трети окна. */
    private int treeW() {
        if (app == null) return TREE_W;
        int need = font.width(appName(app)) + 22;
        for (Folder f : Folder.of(app))
            need = Math.max(need, font.width(folderName(f) + (f == Folder.INBOX ? " (00)" : "")) + 32);
        return Math.max(TREE_W, Math.min(need, ww() / 3));
    }

    private int cx() { return !hasTree() || narrow() ? wx() + 4 : treeX() + treeW() + 4; }
    private int cy() { return hasTree() && narrow() ? bodyY() + 4 + TAB_H + 3 : bodyY() + 4; }
    private int cw() { return wx() + ww() - 4 - cx(); }
    private int ch() { return statusY() - 3 - cy(); }

    private int tabW() { return (ww() - 8) / Folder.of(app).size(); }

    /** Список: шапка колонок ROW, затем строки. */
    private int listTop() { return cy() + 1 + ROW; }
    private int visibleRows() { return Math.max(1, (cy() + ch() - 1 - listTop()) / ROW); }

    /** Просмотрщик: панель с кнопкой BACK, ниже текстовая область. */
    private int toolH() { return 14; }
    private int docX() { return cx() + 1; }
    private int docY() { return cy() + toolH() + 2; }
    private int docW() { return cw() - 2; }
    private int docH() { return cy() + ch() - 1 - docY(); }
    private int backW() { return font.width(uis("back", "BACK")) + 16; }

    /** Панель задач: LOG OFF слева, кнопка открытого приложения, справа пользователь и часы. */
    private int logoffW() { return font.width(uis("logoff", "LOG OFF")) + 10; }
    private int taskBtnX() { return dx() + 2 + logoffW() + 4; }
    private int trayW() { return font.width(trayText()) + 8; }
    private int taskBtnW() { return Math.max(0, Math.min(104, dx() + dw() - 2 - trayW() - 4 - taskBtnX())); }

    private int iconRows() { return Math.max(1, (dh() - TASK_H - 8) / ICON_H); }
    private int iconX(int i) { return dx() + 4 + (i / iconRows()) * ICON_W; }
    private int iconY(int i) { return dy() + 6 + (i % iconRows()) * ICON_H; }

    private int logonW() { return Math.min(LOGON_W, dw() - 16); }
    private int logonX() { return dx() + (dw() - logonW()) / 2; }
    private int logonY() { return dy() + (dh() - LOGON_H) / 2; }
    private int okX() { return logonX() + logonW() - 10 - BTN_W * 2 - 4; }
    private int cancelX() { return logonX() + logonW() - 10 - BTN_W; }
    private int btnY() { return logonY() + LOGON_H - BTN_H - 8; }

    // ================= Состояние =================

    private boolean guarded() { return Util.getMillis() - guardAt < REOPEN_GUARD_MS; }

    private void logon() {
        loggedOn.add(sk);
        mode = PROFILE;
        profile = LabTuning.WS_LOGON_TICKS;
        guardAt = Util.getMillis();
    }

    private void logOff() {
        closeApp();
        loggedOn.remove(sk);
        lastApp.remove(sk);
        mode = LOGON;
        guardAt = Util.getMillis();
    }

    private void openApp(App a) {
        app = a;
        minimized = false;
        closeRecord();
        List<Folder> fs = Folder.of(a);
        Folder f = lastFolder.get(sk + ":" + a.name());
        folder = f != null && fs.contains(f) ? f : fs.get(0);
        loading = a == App.ARCHIVE && !indexRead ? LabTuning.ARCHIVE_LOADING_TICKS : 0;
        flash = 0;
        focus = FOCUS_LIST;
        rebuildRows();
        guardAt = Util.getMillis();
    }

    private void closeApp() {
        app = null;
        minimized = false;
        closeRecord();
        rows.clear();
        loading = 0;
        flash = 0;
        guardAt = Util.getMillis();
    }

    private String fkey() { return sk + ":" + folder.name(); }

    private int scroll() { return listScroll.getOrDefault(fkey(), 0); }

    private void rebuildRows() {
        rows.clear();
        for (Rec r : ArchiveDefs.inFolder(station.id(), folder)) if (listed.contains(r.id())) rows.add(r);
        clampList();
    }

    private int selIndex() {
        String id = selected.get(fkey());
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).id().equals(id)) return i;
        return rows.isEmpty() ? -1 : 0;
    }

    private void select(int i) {
        if (rows.isEmpty()) return;
        i = Math.max(0, Math.min(rows.size() - 1, i));
        selected.put(fkey(), rows.get(i).id());
        int s = scroll();
        if (i < s) s = i;
        if (i >= s + visibleRows()) s = i - visibleRows() + 1;
        listScroll.put(fkey(), s);
        clampList();
    }

    private void clampList() {
        int max = Math.max(0, rows.size() - visibleRowsSafe());
        listScroll.put(fkey(), Math.max(0, Math.min(scroll(), max)));
    }

    /** До init() width = 0; тогда не ограничиваем снизу по видимым строкам. */
    private int visibleRowsSafe() { return width == 0 ? 1 : visibleRows(); }

    private void setFolder(Folder f) {
        if (f == folder) return;
        folder = f;
        lastFolder.put(sk + ":" + app.name(), f);
        rebuildRows();
    }

    private int unread() {
        if (!station.apps().contains(App.MAIL)) return 0;
        int n = 0;
        for (Rec r : ArchiveDefs.inFolder(station.id(), Folder.INBOX))
            if (listed.contains(r.id()) && !readMail.contains(sk + ":" + r.id())) n++;
        return n;
    }

    private boolean isUnread(Rec r) {
        return r.folder() == Folder.INBOX && !readMail.contains(sk + ":" + r.id());
    }

    private void open(int i) {
        if (i < 0 || i >= rows.size()) return;
        select(i);
        Rec r = rows.get(i);
        if (!readable.contains(r.id())) {
            flash = LabTuning.ARCHIVE_FLASH_TICKS;
            return;
        }
        viewing = r;
        if (r.app() == App.MAIL) readMail.add(sk + ":" + r.id());
        guardAt = Util.getMillis();
        docScroll = 0;
        layoutWidth = -1;
    }

    private void closeRecord() {
        viewing = null;
        lines.clear();
    }

    private void finishLoading() {
        loading = 0;
        indexRead = true;
    }

    @Override
    public void tick() {
        if (mode == PROFILE && --profile <= 0) mode = DESKTOP;
        if (loading > 0 && --loading == 0) indexRead = true;
        if (flash > 0) flash--;
    }

    /** Часы панели задач: время мира; в мире снов часы стоят. */
    private static String clock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "--:--";
        if (mc.level.dimension() == DreamDimension.LEVEL) return ArchiveDefs.DREAM_CLOCK;
        long t = Math.floorMod(mc.level.getDayTime(), 24000L);
        int hh = (int) ((t / 1000 + 6) % 24), mm = (int) (t % 1000 * 60 / 1000);
        return String.format(Locale.ROOT, "%02d:%02d", hh, mm);
    }

    private String trayText() {
        return station.user() + "   " + clock();
    }

    // ================= Документ =================

    private Component docText(int index, String fallback) {
        String key = "hypersomnia.archive." + viewing.id() + "." + index;
        // Шифры по миру (LabVariants): порядок в бланке SW-B1-01 и правило ночного кода. Пока зерно не пришло — текст записи.
        if (LabClientState.seeded) {
            long seed = LabClientState.cipherSeed;
            char rev = LabVariants.switchingRevision(viewing.id());
            if (rev != 0 && index == ArchiveDefs.SWITCHING_ORDER_ROW) return Component.literal(LabVariants.feedText(seed, rev));
            Minecraft mc = Minecraft.getInstance();
            boolean dream = mc.level != null && mc.level.dimension() == DreamDimension.LEVEL;
            if (viewing.id().equals("g_keypad") && index == 1 && !dream) {
                int v = LabVariants.hatchRule(seed);
                if (v > 0) return Component.translatableWithFallback(key + ".v" + v, ArchiveDefs.G_KEYPAD_RULES[v]);
            }
            if (viewing.id().equals(ArchiveDefs.BUFFER_LOG)) { // шифр 8: номера стоек в строках HOLD — по миру
                int[] hold = LabVariants.rackHold(seed);
                for (int k = 0; k < ArchiveDefs.BUFFER_ROWS.length && k < hold.length; k++) {
                    if (ArchiveDefs.BUFFER_ROWS[k] != index) continue;
                    String t = Component.translatableWithFallback(key, fallback).getString();
                    return Component.literal(t.replaceFirst("R\\d\\d", String.format(java.util.Locale.ROOT, "R%02d", hold[k])));
                }
                for (int k = 0; k < ArchiveDefs.ANTENNA_ROWS.length; k++) { // шифр 8: азимуты ANT-1 — по миру
                    if (ArchiveDefs.ANTENNA_ROWS[k] != index) continue;
                    String t = Component.translatableWithFallback(key, fallback).getString();
                    String to = String.format(java.util.Locale.ROOT, "%03d", LabVariants.antennaTarget(seed));
                    String from = String.format(java.util.Locale.ROOT, "%03d", LabVariants.antennaStart(seed));
                    return Component.literal(k == 0 ? t.replaceFirst("\\b\\d{3}\\b", to)
                            : t.replaceFirst("\\b\\d{3} > \\d{3}\\b", to + " > " + from));
                }
            }
        }
        return Component.translatableWithFallback(key, fallback);
    }

    private Line field(String key, String fallback, String value) {
        return new Line(null, uis("field." + key, fallback), value, SUB, 4, 10);
    }

    /** Шапка записи: архив — строка «тип, индекс, допуск»; почта, журналы, документы — поля. */
    private void header() {
        List<Line> f = new ArrayList<>();
        switch (viewing.app()) {
            case ARCHIVE -> {
                lines.add(new Line(null, typeName(viewing) + "   " + viewing.date() + "   " + viewing.access(), null, SUB, 0, 13));
                return;
            }
            case MAIL -> {
                f.add(field("from", "From:", viewing.ref()));
                f.add(field("to", "To:", viewing.to()));
                f.add(field("date", "Date:", viewing.date()));
                f.add(field("subject", "Subject:", name(viewing)));
            }
            case LOGS -> {
                f.add(field("source", "Source:", viewing.type()));
                f.add(field("modified", "Modified:", viewing.date()));
            }
            case FILES -> {
                f.add(field("type", "Type:", typeName(viewing)));
                f.add(field("modified", "Modified:", viewing.date()));
            }
        }
        fieldW = 0;
        for (Line l : f) fieldW = Math.max(fieldW, font.width(l.plain()));
        lines.addAll(f);
        lines.add(new Line(null, null, null, 0, 0, 3));
        lines.add(new Line(null, null, null, 0, 1, 8)); // линия под шапкой
    }

    private void layoutDoc() {
        int w = docW() - DOC_PAD * 2 - SCROLL_W;
        if (w == layoutWidth) return;
        layoutWidth = w;
        lines.clear();
        header();
        List<DocumentDefs.El> els = viewing.body();
        // Ячейка Mono — по самому широкому символу колонок записи (кроме последней в строке): начала колонок
        // совпадают между строками, а текст внутри колонки идёт обычным шрифтом и в свою ширину помещается.
        monoCell = LabTuning.ARCHIVE_MONO_CELL;
        for (int i = 0; i < els.size(); i++) {
            if (!(els.get(i) instanceof DocumentDefs.Mono m)) continue;
            String t = docText(i, m.text()).getString();
            List<int[]> segs = monoSegments(t);
            for (int k = 0; k < segs.size() - 1; k++)
                for (int c = segs.get(k)[0]; c < segs.get(k)[1]; c++) monoCell = Math.max(monoCell, font.width(String.valueOf(t.charAt(c))));
        }
        for (int i = 0; i < els.size(); i++) {
            DocumentDefs.El e = els.get(i);
            if (e instanceof DocumentDefs.Head h) {
                for (FormattedCharSequence l : font.split(docText(i, h.text()), w)) lines.add(new Line(l, null, null, TEXT, 1, 10));
                lines.add(new Line(null, null, null, 0, 1, 5)); // линия под заголовком
            } else if (e instanceof DocumentDefs.Para p) {
                for (FormattedCharSequence l : font.split(docText(i, p.text()), w)) lines.add(new Line(l, null, null, TEXT, 0, 10));
                lines.add(new Line(null, null, null, 0, 0, 3));
            } else if (e instanceof DocumentDefs.Stamp s) {
                lines.add(new Line(null, docText(i, s.text()).getString(), null, RED, 2, 16));
            } else if (e instanceof DocumentDefs.Mono m) {
                for (String l : wrapMono(docText(i, m.text()).getString(), w)) lines.add(new Line(null, l, null, TEXT, 3, 10));
                lines.add(new Line(null, null, null, 0, 0, 2));
            } else if (e instanceof DocumentDefs.Gap) {
                lines.add(new Line(null, null, null, 0, 0, 6));
            }
            // Остальные элементы движка документов на станциях не используются.
        }
        docHeight = 0;
        for (Line l : lines) docHeight += l.h();
        docHeight += DOC_PAD * 2;
        clampDoc();
    }

    /** Колонки Mono-строки: куски, разделённые двумя и более пробелами, как {начало, конец} в символах. */
    private static List<int[]> monoSegments(String s) {
        List<int[]> out = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            while (i < n && s.charAt(i) == ' ') i++;
            if (i >= n) break;
            int start = i;
            while (i < n && !(s.charAt(i) == ' ' && (i + 1 >= n || s.charAt(i + 1) == ' '))) i++;
            out.add(new int[]{start, i});
        }
        return out;
    }

    /**
     * Перенос Mono по ширине w (px GUI). Колонки стоят по сетке ячеек; последняя колонка переносится обычным
     * шрифтом и продолжается под своим началом («22:00  текст...»). Если она не помещается и начинается правее
     * ARCHIVE_MONO_HANG_MAX_PCT ширины, строка переносится целиком с начала, одиночными пробелами.
     */
    private List<String> wrapMono(String s, int w) {
        List<String> out = new ArrayList<>();
        List<int[]> segs = monoSegments(s);
        if (segs.isEmpty()) {
            out.add("");
            return out;
        }
        int[] last = segs.get(segs.size() - 1);
        int x0 = last[0] * monoCell;
        String tail = s.substring(last[0], last[1]);
        if (x0 < w && font.width(tail) <= w - x0) {
            out.add(s.substring(0, last[1]));
            return out;
        }
        if (x0 > w * LabTuning.ARCHIVE_MONO_HANG_MAX_PCT / 100) {
            for (FormattedText t : font.getSplitter().splitLines(s.strip().replaceAll(" {2,}", " "), w, Style.EMPTY))
                out.add(t.getString().strip());
            return out;
        }
        String head = s.substring(0, last[0]), pad = " ".repeat(last[0]);
        List<FormattedText> parts = font.getSplitter().splitLines(tail, w - x0, Style.EMPTY);
        if (parts.isEmpty()) out.add(head);
        for (int k = 0; k < parts.size(); k++) out.add((k == 0 ? head : pad) + parts.get(k).getString().strip());
        return out;
    }

    /** Каждая колонка — с начала своей ячейки monoCell, текст колонки обычным шрифтом. */
    private void drawMono(GuiGraphics g, String s, int x, int y, int color) {
        for (int[] seg : monoSegments(s))
            g.drawString(font, s.substring(seg[0], seg[1]), x + seg[0] * monoCell, y, color, false);
    }

    private int maxDocScroll() { return Math.max(0, docHeight - docH()); }

    private void clampDoc() { docScroll = Math.max(0, Math.min(docScroll, maxDocScroll())); }

    // ================= Отрисовка: примитивы =================

    private static void raised(GuiGraphics g, int x, int y, int w, int h, int body) {
        g.fill(x, y, x + w, y + h, body);
        g.fill(x, y, x + w, y + 1, LIGHT);
        g.fill(x, y, x + 1, y + h, LIGHT);
        g.fill(x, y + h - 1, x + w, y + h, SHADE);
        g.fill(x + w - 1, y, x + w, y + h, SHADE);
    }

    private static void sunken(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, WELL);
        g.fill(x, y, x + w, y + 1, SHADE);
        g.fill(x, y, x + 1, y + h, SHADE);
        g.fill(x, y + h - 1, x + w, y + h, LIGHT);
        g.fill(x + w - 1, y, x + w, y + h, LIGHT);
    }

    /** Нажатая кнопка: как sunken, но с телом цвета панели. */
    private static void pressed(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SEL_IDLE);
        g.fill(x, y, x + w, y + 1, SHADE);
        g.fill(x, y, x + 1, y + h, SHADE);
        g.fill(x, y + h - 1, x + w, y + h, LIGHT);
        g.fill(x + w - 1, y, x + w, y + h, LIGHT);
    }

    private static void folderIcon(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 4, y + 1, color);
        g.fill(x, y + 1, x + 9, y + 7, color);
        g.fill(x + 1, y + 2, x + 8, y + 3, WELL);
    }

    private static void docIcon(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 4, y + 7, color);
        g.fill(x + 4, y + 2, x + 6, y + 7, color);
        g.fill(x + 1, y + 3, x + 4, y + 4, WELL);
        g.fill(x + 1, y + 5, x + 5, y + 6, WELL);
    }

    private static void logIcon(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 6, y + 7, color);
        for (int k = 0; k < 3; k++) g.fill(x + 1, y + 1 + k * 2, x + (k == 1 ? 4 : 5), y + 2 + k * 2, WELL);
    }

    /** Конверт 8x6: непрочитанное — залитый, прочитанное — контур. */
    private static void mailIcon(GuiGraphics g, int x, int y, int color, boolean unread) {
        int top = y + 1;
        if (unread) g.fill(x, top, x + 8, top + 6, color);
        else {
            g.fill(x, top, x + 8, top + 1, color);
            g.fill(x, top + 5, x + 8, top + 6, color);
            g.fill(x, top, x + 1, top + 6, color);
            g.fill(x + 7, top, x + 8, top + 6, color);
        }
        int v = unread ? WELL : color;
        int[][] flap = {{1, 1}, {2, 2}, {3, 3}, {4, 3}, {5, 2}, {6, 1}};
        for (int[] p : flap) g.fill(x + p[0], top + p[1], x + p[0] + 1, top + p[1] + 1, v);
    }

    /** Значок приложения 16x14 из прямоугольников. Без лиц и «глаз»: конверт, картотека, лист журнала, папка. */
    private static void appIcon(GuiGraphics g, App a, int x, int y, int fg, int bg) {
        switch (a) {
            case MAIL -> {
                g.fill(x, y + 2, x + 16, y + 13, fg);
                g.fill(x + 1, y + 3, x + 15, y + 12, bg);
                for (int i = 0; i < 7; i++) {
                    g.fill(x + 1 + i, y + 3 + i, x + 2 + i, y + 4 + i, fg);
                    g.fill(x + 14 - i, y + 3 + i, x + 15 - i, y + 4 + i, fg);
                }
            }
            case ARCHIVE -> {
                g.fill(x + 2, y, x + 14, y + 14, fg);
                g.fill(x + 3, y + 1, x + 13, y + 13, bg);
                for (int k = 0; k < 3; k++) {
                    int dy = y + 1 + k * 4;
                    if (k > 0) g.fill(x + 3, dy, x + 13, dy + 1, fg);
                    g.fill(x + 6, dy + 2, x + 10, dy + 3, fg);
                }
            }
            case LOGS -> {
                g.fill(x + 3, y, x + 13, y + 14, fg);
                g.fill(x + 4, y + 1, x + 12, y + 13, bg);
                for (int k = 0; k < 5; k++) g.fill(x + 5, y + 3 + k * 2, x + (k % 2 == 0 ? 11 : 9), y + 4 + k * 2, fg);
            }
            case FILES -> {
                g.fill(x + 1, y + 2, x + 7, y + 4, fg);
                g.fill(x + 1, y + 4, x + 15, y + 14, fg);
                g.fill(x + 2, y + 6, x + 14, y + 13, bg);
            }
        }
    }

    /** Маленький значок окна для заголовка и панели задач. */
    private static void glyph(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 6, y + 6, LIGHT);
        g.fill(x + 1, y + 1, x + 5, y + 5, BAR);
        g.fill(x + 1, y + 1, x + 5, y + 2, LIGHT);
    }

    private String fit(String s, int w) {
        if (w <= 0) return "";
        if (font.width(s) <= w) return s;
        String ell = "..";
        return font.plainSubstrByWidth(s, Math.max(0, w - font.width(ell))) + ell;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean hot, boolean deflt) {
        raised(g, x, y, w, h, hot ? HOT : PANEL);
        if (deflt) { // кнопка по умолчанию (Enter): тёмная рамка вокруг
            g.fill(x - 1, y - 1, x + w + 1, y, SHADE);
            g.fill(x - 1, y + h, x + w + 1, y + h + 1, SHADE);
            g.fill(x - 1, y, x, y + h, SHADE);
            g.fill(x + w, y, x + w + 1, y + h, SHADE);
        }
        String t = fit(label, w - 6);
        g.drawString(font, t, x + (w - font.width(t)) / 2, y + (h - 8) / 2, TEXT, false);
    }

    // ================= Отрисовка: экраны =================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.fill(0, 0, width, height, VEIL);
        int x = dx(), y = dy(), w = dw(), h = dh();
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, BAR); // тёмная кромка кинескопа
        renderWallpaper(g, mode == DESKTOP);
        if (mode == LOGON) renderLogon(g, mouseX, mouseY);
        else if (mode == PROFILE) renderProfile(g);
        else {
            if (windowShown()) renderWindow(g, mouseX, mouseY);
            else renderIcons(g, mouseX, mouseY);
            renderTaskbar(g, mouseX, mouseY);
        }
        renderCrt(g, x, y, w, h);
    }

    /** Ровные обои и тусклая надпись CRADLE по центру. */
    private void renderWallpaper(GuiGraphics g, boolean desktop) {
        int x = dx(), y = dy(), w = dw(), h = dh();
        g.fill(x, y, x + w, y + h, WALL);
        int cx = x + w / 2, cy = y + (desktop ? (h - TASK_H) / 2 : h / 2);
        String mark = "CRADLE";
        g.pose().pushPose();
        g.pose().translate(cx, cy - 12, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, mark, -font.width(mark) / 2, 0, MARK, false);
        g.pose().popPose();
        if (desktop) {
            String host = station.host();
            g.drawString(font, host, cx - font.width(host) / 2, cy + 8, MARK, false);
        }
    }

    private void renderLogon(GuiGraphics g, int mouseX, int mouseY) {
        int x = logonX(), y = logonY(), w = logonW(), h = LOGON_H;
        raised(g, x, y, w, h, PANEL);
        g.fill(x + 1, y + 1, x + w - 1, y + 1 + BAR_H, BAR);
        glyph(g, x + 4, y + 4);
        g.drawString(font, fit(uis("logon.title", "Log On to CRADLE"), w - 18), x + 14, y + 3, TEXT, false);
        // Полоса с названием системы и местом станции
        int by = y + 1 + BAR_H;
        g.fill(x + 1, by, x + w - 1, by + 20, WELL);
        g.fill(x + 1, by + 20, x + w - 1, by + 21, LIGHT);
        g.drawString(font, "CRADLE", x + 8, by + 6, TEXT, false);
        int wsX = x + 8 + font.width("CRADLE ");
        g.drawString(font, "WORKSTATION", wsX, by + 6, SUB, false);
        String pl = place();
        int plX = Math.max(wsX + font.width("WORKSTATION") + 8, x + w - 8 - font.width(pl));
        g.drawString(font, fit(pl, x + w - 8 - plX), plX, by + 6, DIM, false);
        // Поля: имя и пароль сохранены на станции
        String[] labels = {uis("logon.user", "User name:"), uis("logon.password", "Password:"), uis("logon.host", "Workstation:")};
        String[] values = {station.user(), "********", station.host()};
        int lw = 0;
        for (String l : labels) lw = Math.max(lw, font.width(l));
        int fy = by + 29, lx = x + 10, fx = lx + lw + 6, fw = x + w - 10 - fx;
        for (int i = 0; i < 3; i++) {
            int ry = fy + i * 16;
            g.drawString(font, labels[i], lx, ry + 2, SUB, false);
            if (i < 2) {
                sunken(g, fx, ry, fw, 12);
                g.drawString(font, fit(values[i], fw - 6), fx + 3, ry + (i == 1 ? 4 : 2), TEXT, false); // '*' сидит высоко
            } else {
                raised(g, fx, ry, fw, 12, SEL_IDLE);
                g.drawString(font, fit(values[i], fw - 6), fx + 3, ry + 2, SUB, false);
            }
        }
        int bY = btnY();
        button(g, okX(), bY, BTN_W, BTN_H, uis("logon.ok", "OK"), in(mouseX, mouseY, okX(), bY, BTN_W, BTN_H), true);
        button(g, cancelX(), bY, BTN_W, BTN_H, uis("logon.cancel", "Cancel"), in(mouseX, mouseY, cancelX(), bY, BTN_W, BTN_H), false);
    }

    private void renderProfile(GuiGraphics g) {
        int w = Math.min(190, dw() - 16), h = 38;
        int x = dx() + (dw() - w) / 2, y = dy() + (dh() - h) / 2;
        raised(g, x, y, w, h, PANEL);
        g.drawString(font, fit(uis("logon.loading", "LOADING PROFILE..."), w - 16), x + 8, y + 8, SUB, false);
        progress(g, x + 8, y + 22, Math.min(120, w - 16), LabTuning.WS_LOGON_TICKS - profile, LabTuning.WS_LOGON_TICKS);
    }

    /** Полоса из отдельных сегментов, как у индикаторов того времени. */
    private static void progress(GuiGraphics g, int x, int y, int w, int done, int total) {
        g.fill(x, y, x + w, y + 5, PANEL);
        int filled = w * Math.max(0, done) / Math.max(1, total);
        for (int px = 0; px + 4 <= filled; px += 6) g.fill(x + px + 1, y + 1, x + px + 5, y + 4, LIGHT);
    }

    private void renderIcons(GuiGraphics g, int mouseX, int mouseY) {
        List<App> apps = station.apps();
        for (int i = 0; i < apps.size(); i++) {
            App a = apps.get(i);
            int x = iconX(i), y = iconY(i);
            boolean sel = i == icon, hot = in(mouseX, mouseY, x, y, ICON_W, ICON_H - 2);
            appIcon(g, a, x + (ICON_W - 16) / 2, y + 3, sel ? TEXT : SOFT, WALL);
            String label = appName(a);
            if (a == App.MAIL && unread() > 0) label += " (" + unread() + ")";
            // Подпись в две строки, как у значков того времени; лишнее обрезается
            List<String> ls = new ArrayList<>();
            for (FormattedText t : font.getSplitter().splitLines(label, ICON_W - 6, Style.EMPTY)) ls.add(t.getString().strip());
            if (ls.isEmpty()) ls.add("");
            if (ls.size() > 2) ls.set(1, ls.get(1) + " " + String.join(" ", ls.subList(2, ls.size())));
            while (ls.size() > 2) ls.remove(2);
            int lw = 0;
            for (int k = 0; k < ls.size(); k++) {
                ls.set(k, fit(ls.get(k), ICON_W - 6));
                lw = Math.max(lw, font.width(ls.get(k)) + 4);
            }
            int lx = x + (ICON_W - lw) / 2, ly = y + 21, lh = ls.size() * 10 + 1;
            if (sel) g.fill(lx, ly, lx + lw, ly + lh, SEL);
            else if (hot) g.fill(lx, ly, lx + lw, ly + lh, HOVER);
            for (int k = 0; k < ls.size(); k++) {
                String l = ls.get(k);
                g.drawString(font, l, x + (ICON_W - font.width(l)) / 2, ly + 2 + k * 10, sel ? TEXT : SOFT, false);
            }
        }
    }

    private void renderTaskbar(GuiGraphics g, int mouseX, int mouseY) {
        int x = dx(), y = taskY(), w = dw();
        g.fill(x, y, x + w, y + TASK_H, PANEL);
        g.fill(x, y, x + w, y + 1, LIGHT);
        int by = y + 2, bh = TASK_H - 4;
        int lw = logoffW();
        button(g, x + 2, by, lw, bh, uis("logoff", "LOG OFF"), in(mouseX, mouseY, x + 2, by, lw, bh), false);
        if (app != null) {
            int tx = taskBtnX(), tw = taskBtnW();
            if (tw > 16) {
                if (windowShown()) pressed(g, tx, by, tw, bh);
                else raised(g, tx, by, tw, bh, in(mouseX, mouseY, tx, by, tw, bh) ? HOT : PANEL);
                glyph(g, tx + 4, by + 3);
                g.drawString(font, fit(appName(app), tw - 18), tx + 14, by + 2, TEXT, false);
            }
        }
        String tray = trayText();
        int trw = trayW(), trx = x + w - 2 - trw;
        sunken(g, trx, by, trw, bh);
        g.drawString(font, tray, trx + 4, by + 2, SUB, false);
    }

    private void renderWindow(GuiGraphics g, int mouseX, int mouseY) {
        int x = wx(), y = wy(), w = ww(), h = wh();
        raised(g, x, y, w, h, PANEL);
        // Заголовок окна: приложение и станция, кнопка закрытия
        g.fill(x + 1, y + 1, x + w - 1, y + 1 + BAR_H, BAR);
        glyph(g, x + 4, y + 4);
        g.drawString(font, fit(appName(app) + " - " + station.host(), w - 34), x + 14, y + 3, TEXT, false);
        int cbx = closeX(), cby = closeY();
        raised(g, cbx, cby, 10, 9, in(mouseX, mouseY, cbx, cby, 10, 9) ? HOT : PANEL);
        for (int k = 0; k < 5; k++) {
            g.fill(cbx + 2 + k, cby + 2 + k, cbx + 3 + k, cby + 3 + k, TEXT);
            g.fill(cbx + 6 - k, cby + 2 + k, cbx + 7 - k, cby + 3 + k, TEXT);
        }

        if (hasTree()) {
            if (narrow()) renderTabs(g, mouseX, mouseY);
            else renderTree(g, mouseX, mouseY);
        }

        if (loading > 0) renderLoading(g);
        else if (viewing != null) renderDoc(g, mouseX, mouseY);
        else renderList(g, mouseX, mouseY);

        renderStatus(g);
    }

    /** Слабая развёртка, затемнение к краям и медленная светлая полоса. Только поверх экрана станции, текст остаётся читаемым. */
    private void renderCrt(GuiGraphics g, int x, int y, int w, int h) {
        if (!LabTuning.ARCHIVE_CRT) return;
        for (int py = y; py < y + h; py += LabTuning.ARCHIVE_SCANLINE_STEP)
            g.fill(x, py, x + w, py + 1, LabTuning.ARCHIVE_SCANLINE_ARGB);
        int v = Math.min(LabTuning.ARCHIVE_VIGNETTE_PX, h / 3), dark = LabTuning.ARCHIVE_VIGNETTE_ALPHA << 24;
        g.fillGradient(x, y, x + w, y + v, dark, 0);
        g.fillGradient(x, y + h - v, x + w, y + h, 0, dark);
        int band = LabTuning.ARCHIVE_ROLL_PX, period = LabTuning.ARCHIVE_ROLL_MS;
        int by = y - band + (int) (Util.getMillis() % period * (h + band) / period);
        int lite = (LabTuning.ARCHIVE_ROLL_ALPHA << 24) | (TEXT & 0xFFFFFF), clear = TEXT & 0xFFFFFF;
        g.enableScissor(x, y, x + w, y + h);
        g.fillGradient(x, by, x + w, by + band / 2, clear, lite);
        g.fillGradient(x, by + band / 2, x + w, by + band, lite, clear);
        g.disableScissor();
    }

    private String folderLabel(Folder f) {
        String s = folderName(f);
        if (f == Folder.INBOX) {
            int n = unread();
            if (n > 0) s += " (" + n + ")";
        }
        return s;
    }

    private void renderTree(GuiGraphics g, int mouseX, int mouseY) {
        int x = treeX(), y = treeY(), tw = treeW();
        sunken(g, x, y, tw, treeH());
        int ry = y + 3;
        folderIcon(g, x + 4, ry + 2, LIGHT);
        g.drawString(font, fit(appName(app), tw - 20), x + 16, ry + 2, SUB, false);
        List<Folder> all = Folder.of(app);
        int lineX = x + 8, bx = x + 13, bw = tw - 15;
        for (int i = 0; i < all.size(); i++) {
            int iy = ry + TREE_ROW * (i + 1);
            // пунктир дерева
            int top = iy - TREE_ROW + 10, mid = iy + 5;
            for (int py = top; py <= mid; py += 2) g.fill(lineX, py, lineX + 1, py + 1, DIM);
            for (int px = lineX; px < bx; px += 2) g.fill(px, mid, px + 1, mid + 1, DIM);
            Folder f = all.get(i);
            boolean sel = f == folder;
            if (sel) g.fill(bx, iy, bx + bw, iy + TREE_ROW - 1, focus == FOCUS_TREE && viewing == null ? SEL : SEL_IDLE);
            else if (in(mouseX, mouseY, bx, iy, bw, TREE_ROW - 1)) g.fill(bx, iy, bx + bw, iy + TREE_ROW - 1, HOVER);
            folderIcon(g, bx + 2, iy + 2, sel ? TEXT : LIGHT);
            g.drawString(font, fit(folderLabel(f), bw - 15), bx + 13, iy + 2, sel ? TEXT : SUB, false);
        }
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int x = wx() + 4, y = bodyY() + 4, tw = tabW();
        List<Folder> all = Folder.of(app);
        for (int i = 0; i < all.size(); i++) {
            int tx = x + i * tw;
            boolean sel = all.get(i) == folder;
            if (sel) raised(g, tx, y, tw - 1, TAB_H + 1, focus == FOCUS_TREE && viewing == null ? SEL : PANEL);
            else raised(g, tx, y + 1, tw - 1, TAB_H, in(mouseX, mouseY, tx, y, tw - 1, TAB_H) ? HOT : SEL_IDLE);
            g.drawString(font, fit(folderLabel(all.get(i)), tw - 7), tx + 3, y + 3, sel ? TEXT : SUB, false);
        }
    }

    private void renderLoading(GuiGraphics g) {
        int x = cx(), y = cy();
        sunken(g, x, y, cw(), ch());
        g.drawString(font, uis("loading", "READING INDEX..."), x + DOC_PAD, y + DOC_PAD, SUB, false);
        int total = LabTuning.ARCHIVE_LOADING_TICKS;
        progress(g, x + DOC_PAD, y + DOC_PAD + 13, Math.min(120, cw() - DOC_PAD * 2), total - loading, total);
    }

    /** Показывать ли необязательные колонки (тип, источник): решает columns(), читает cells(). */
    private boolean optCols = true;

    /**
     * Колонки списка. Фиксированные — по содержимому текущей папки, но не шире своего предела; первая
     * подходящая растягивается на остаток. Необязательные убираются, если остатка меньше COL_FLEX_MIN.
     */
    private List<Col> columns(int inner) {
        List<Col> out = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            optCols = pass == 0 && !narrow();
            List<Col> c = baseColumns();
            out.clear();
            int fixed = 0;
            for (int k = 0; k < c.size(); k++) {
                Col col = c.get(k);
                if (col.w() < 0) {
                    out.add(col);
                    continue;
                }
                int need = font.width(col.head()) + 6, pad = k == 0 ? 16 : 6; // в первой колонке ещё значок
                for (Rec r : rows) need = Math.max(need, font.width(cells(r, readable.contains(r.id())).get(k)) + pad);
                int w = Math.min(col.w(), need);
                out.add(new Col(col.head(), w));
                fixed += w;
            }
            if (!optCols || inner - fixed >= COL_FLEX_MIN) {
                int flex = Math.max(24, inner - fixed);
                for (int k = 0; k < out.size(); k++) if (out.get(k).w() < 0) out.set(k, new Col(out.get(k).head(), flex));
                return out;
            }
        }
        return out;
    }

    /** Набор колонок приложения: заголовок и предел ширины (−1 = растягивается). */
    private List<Col> baseColumns() {
        List<Col> c = new ArrayList<>();
        switch (app) {
            case ARCHIVE -> {
                c.add(new Col(uis("col.record", "Record"), -1));
                if (optCols) c.add(new Col(uis("col.type", "Type"), COL_TYPE));
                c.add(new Col(uis("col.date", "Date"), COL_DATE));
                c.add(new Col(uis("col.access", "Access"), COL_ACCESS));
            }
            case MAIL -> {
                c.add(folder == Folder.SENT ? new Col(uis("col.to", "To"), COL_FROM) : new Col(uis("col.from", "From"), COL_FROM));
                c.add(new Col(uis("col.subject", "Subject"), -1));
                c.add(new Col(uis("col.date", "Date"), COL_DATE));
            }
            case LOGS -> {
                c.add(new Col(uis("col.name", "Name"), -1));
                if (optCols) c.add(new Col(uis("col.source", "Source"), COL_SOURCE));
                c.add(new Col(uis("col.modified", "Modified"), COL_DATE));
            }
            case FILES -> {
                c.add(new Col(uis("col.name", "Name"), -1));
                if (optCols) c.add(new Col(uis("col.type", "Type"), COL_TYPE));
                c.add(new Col(uis("col.modified", "Modified"), COL_DATE));
            }
        }
        return c;
    }

    /** Текст ячеек строки в порядке columns(). */
    private List<String> cells(Rec r, boolean ok) {
        boolean wide = optCols;
        List<String> c = new ArrayList<>();
        switch (app) {
            case ARCHIVE -> {
                c.add(name(r));
                if (wide) c.add(typeName(r));
                c.add(r.date());
                c.add(ok ? r.access() : uis("restricted", "[RESTRICTED]"));
            }
            case MAIL -> {
                c.add(folder == Folder.SENT ? r.to() : r.ref());
                c.add(name(r));
                c.add(r.date());
            }
            case LOGS -> {
                c.add(r.ref());
                if (wide) c.add(r.type());
                c.add(r.date());
            }
            case FILES -> {
                c.add(r.ref());
                if (wide) c.add(typeName(r));
                c.add(r.date());
            }
        }
        return c;
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int x = cx(), y = cy(), w = cw();
        sunken(g, x, y, w, ch());
        int inner = w - 2 - SCROLL_W;
        List<Col> cols = columns(inner);
        int hx = x + 1, hy = y + 1, px = hx;
        for (Col c : cols) {
            raised(g, px, hy, c.w(), ROW, PANEL);
            g.drawString(font, fit(c.head(), c.w() - 4), px + 2, hy + 2, SUB, false);
            px += c.w();
        }
        raised(g, px, hy, SCROLL_W, ROW, PANEL);

        int top = listTop(), vis = visibleRows(), scr = scroll();
        if (rows.isEmpty()) {
            String key = "empty." + app.key;
            String fb = switch (app) {
                case ARCHIVE -> "NO RECORDS";
                case MAIL -> "NO MESSAGES";
                case LOGS, FILES -> "NO FILES";
            };
            g.drawString(font, uis(key, fb), hx + 4, top + 3, DIM, false);
        }
        int sel = selIndex();
        boolean mail = app == App.MAIL;
        for (int r = 0; r < vis; r++) {
            int i = scr + r;
            if (i >= rows.size()) break;
            Rec rec = rows.get(i);
            int ry = top + r * ROW;
            boolean ok = readable.contains(rec.id());
            boolean unread = mail && isUnread(rec);
            if (i == sel) g.fill(hx, ry, hx + inner, ry + ROW, focus == FOCUS_LIST ? SEL : SEL_IDLE);
            else if (in(mouseX, mouseY, hx, ry, inner, ROW)) g.fill(hx, ry, hx + inner, ry + ROW, HOVER);
            int fg = !ok ? (i == sel ? SUB : DIM) : i == sel || unread ? TEXT : SOFT; // DIM на SEL не читается
            int ic = !ok ? (i == sel ? SUB : DIM) : i == sel ? TEXT : LIGHT;
            switch (app) {
                case MAIL -> mailIcon(g, hx + 2, ry + 2, unread ? (i == sel ? TEXT : SOFT) : ic, unread);
                case LOGS -> logIcon(g, hx + 3, ry + 2, ic);
                default -> docIcon(g, hx + 3, ry + 2, ic);
            }
            List<String> cells = cells(rec, ok);
            int cx0 = hx;
            for (int c = 0; c < cols.size(); c++) {
                int cwid = cols.get(c).w();
                int pad = c == 0 ? 12 : 2;
                boolean main = c == 0 || (mail && c == 1);
                int color = !ok && app == App.ARCHIVE && c == cols.size() - 1 ? RED : main ? fg : ok || i == sel ? SUB : DIM;
                g.drawString(font, fit(cells.get(c), cwid - pad - 2), cx0 + pad, ry + 2, color, false);
                cx0 += cwid;
            }
        }
        scrollbar(g, x + w - 1 - SCROLL_W, top, vis * ROW, rows.size(), vis, scr);
    }

    private void scrollbar(GuiGraphics g, int x, int y, int h, int total, int visible, int pos) {
        g.fill(x, y, x + SCROLL_W, y + h, PANEL);
        if (total <= visible || h < 8) return;
        int th = Math.max(8, h * visible / total);
        int ty = y + (h - th) * pos / Math.max(1, total - visible);
        raised(g, x, ty, SCROLL_W, th, PANEL);
    }

    private String docTitle() {
        return switch (viewing.app()) {
            case ARCHIVE, LOGS, FILES -> viewing.ref() + "  " + name(viewing);
            case MAIL -> name(viewing);
        };
    }

    private void renderDoc(GuiGraphics g, int mouseX, int mouseY) {
        layoutDoc();
        int x = cx(), y = cy(), w = cw();
        // Панель инструментов: BACK и заголовок записи
        int bw = backW();
        boolean hot = in(mouseX, mouseY, x, y, bw, toolH());
        raised(g, x, y, bw, toolH(), hot ? HOT : PANEL);
        g.fill(x + 5, y + 6, x + 6, y + 8, TEXT);
        g.fill(x + 6, y + 5, x + 7, y + 9, TEXT);
        g.drawString(font, uis("back", "BACK"), x + 10, y + 3, TEXT, false);
        g.drawString(font, fit(docTitle(), w - bw - 8), x + bw + 6, y + 3, TEXT, false);

        int dx = docX() - 1, dy = docY() - 1, dw = docW() + 2, dh = docH() + 2;
        sunken(g, dx, dy, dw, dh);
        int tx = docX() + DOC_PAD, ty = docY() + DOC_PAD - docScroll;
        g.enableScissor(docX(), docY(), docX() + docW() - SCROLL_W, docY() + docH());
        int lineW = docW() - DOC_PAD * 2 - SCROLL_W;
        for (Line l : lines) {
            if (ty + l.h() >= docY() && ty < docY() + docH()) {
                if (l.kind() == 2) {
                    int sw = font.width(l.plain()) + 8;
                    g.fill(tx, ty + 1, tx + sw, ty + 2, RED);
                    g.fill(tx, ty + 12, tx + sw, ty + 13, RED);
                    g.fill(tx, ty + 1, tx + 1, ty + 13, RED);
                    g.fill(tx + sw - 1, ty + 1, tx + sw, ty + 13, RED);
                    g.drawString(font, l.plain(), tx + 4, ty + 3, RED, false);
                } else if (l.kind() == 4) {
                    g.drawString(font, l.plain(), tx, ty, SUB, false);
                    int vx = tx + fieldW + 6;
                    g.drawString(font, fit(l.value(), lineW - fieldW - 6), vx, ty, TEXT, false);
                } else if (l.text() != null) {
                    g.drawString(font, l.text(), tx, ty, l.color(), false);
                } else if (l.kind() == 3) {
                    drawMono(g, l.plain(), tx, ty, l.color());
                } else if (l.plain() != null) {
                    g.drawString(font, fit(l.plain(), lineW), tx, ty, l.color(), false);
                } else if (l.kind() == 1) {
                    g.fill(tx, ty + 1, tx + lineW, ty + 2, LIGHT);
                }
            }
            ty += l.h();
        }
        g.disableScissor();
        int vh = docH();
        g.fill(docX() + docW() - SCROLL_W, docY(), docX() + docW(), docY() + vh, PANEL);
        if (docHeight > vh) {
            int th = Math.max(8, vh * vh / docHeight);
            int tyb = docY() + (vh - th) * docScroll / Math.max(1, maxDocScroll());
            raised(g, docX() + docW() - SCROLL_W, tyb, SCROLL_W, th, PANEL);
        }
    }

    private void renderStatus(GuiGraphics g) {
        int x = wx() + 4, y = statusY(), w = ww() - 8;
        int right = Math.min(90, w / 3);
        sunken(g, x, y, w - right - 2, STATUS_H);
        sunken(g, x + w - right, y, right, STATUS_H);
        String path = station.host() + " / " + appName(app) + (hasTree() ? " / " + folderName(folder) : "");
        String left = flash > 0 ? uis("unavailable", "RECORD UNAVAILABLE") : path;
        g.drawString(font, fit(left, w - right - 8), x + 3, y + 2, flash > 0 ? RED : SUB, false);
        String fb = switch (app) {
            case ARCHIVE -> "RECORDS: %s";
            case MAIL -> "MESSAGES: %s";
            case LOGS, FILES -> "FILES: %s";
        };
        String count = Component.translatableWithFallback("hypersomnia.archive.ui.count." + app.key, fb, rows.size()).getString();
        g.drawString(font, fit(count, right - 6), x + w - right + 3, y + 2, SUB, false);
    }

    // ================= Ввод =================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mode == PROFILE) {
            mode = DESKTOP;
            return true;
        }
        if (button != 0) return true;
        if (mode == LOGON) {
            if (guarded()) return true;
            if (in(mx, my, okX(), btnY(), BTN_W, BTN_H)) logon();
            else if (in(mx, my, cancelX(), btnY(), BTN_W, BTN_H)) onClose();
            return true;
        }
        // Панель задач
        if (in(mx, my, dx(), taskY(), dw(), TASK_H)) {
            int by = taskY() + 2, bh = TASK_H - 4;
            if (in(mx, my, dx() + 2, by, logoffW(), bh)) logOff();
            else if (app != null && in(mx, my, taskBtnX(), by, taskBtnW(), bh)) minimized = !minimized;
            return true;
        }
        if (windowShown()) return windowClicked(mx, my);
        // Значки рабочего стола: один щелчок выделяет и открывает
        List<App> apps = station.apps();
        for (int i = 0; i < apps.size(); i++) {
            if (!in(mx, my, iconX(i), iconY(i), ICON_W, ICON_H - 2)) continue;
            icon = i;
            if (guarded()) return true;
            if (apps.get(i) == app) minimized = false;
            else openApp(apps.get(i));
            return true;
        }
        return true;
    }

    private boolean windowClicked(double mx, double my) {
        if (in(mx, my, closeX(), closeY(), 10, 9)) {
            closeApp();
            return true;
        }
        if (loading > 0) {
            finishLoading();
            return true;
        }
        if (hasTree()) {
            List<Folder> all = Folder.of(app);
            if (narrow()) {
                int x = wx() + 4, y = bodyY() + 4, tw = tabW();
                for (int i = 0; i < all.size(); i++)
                    if (in(mx, my, x + i * tw, y, tw - 1, TAB_H)) return clickFolder(all.get(i));
            } else {
                int bx = treeX() + 13, bw = treeW() - 15, ry = treeY() + 3;
                for (int i = 0; i < all.size(); i++)
                    if (in(mx, my, bx, ry + TREE_ROW * (i + 1), bw, TREE_ROW - 1)) return clickFolder(all.get(i));
            }
        }
        if (viewing != null) {
            if (guarded()) return true; // второй щелчок двойного клика
            if (in(mx, my, cx(), cy(), backW(), toolH())) closeRecord();
            return true;
        }
        int top = listTop(), inner = cw() - 2 - SCROLL_W;
        if (in(mx, my, cx() + 1, top, inner, visibleRows() * ROW)) {
            int i = scroll() + (int) ((my - top) / ROW);
            focus = FOCUS_LIST;
            if (i < rows.size() && !guarded()) open(i);
            return true;
        }
        return true;
    }

    private boolean clickFolder(Folder f) {
        closeRecord();
        setFolder(f);
        focus = FOCUS_TREE;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!windowShown() || loading > 0) return true;
        int step = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
        if (viewing != null) {
            docScroll += step * LabTuning.ARCHIVE_SCROLL_PX;
            clampDoc();
            return true;
        }
        if (in(mx, my, cx(), cy(), cw(), ch())) {
            listScroll.put(fkey(), scroll() + step);
            clampList();
        }
        return true;
    }

    private static boolean enter(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
    }

    /** Esc: запись -> список -> рабочий стол -> закрыть экран. Backspace так же, но экран не закрывает. */
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean esc = key == GLFW.GLFW_KEY_ESCAPE, back = key == GLFW.GLFW_KEY_BACKSPACE;
        if (mode == PROFILE) {
            if (esc) return super.keyPressed(key, scan, mods);
            mode = DESKTOP;
            return true;
        }
        if (mode == LOGON) {
            if (enter(key) || key == GLFW.GLFW_KEY_SPACE) {
                logon();
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
        if (windowShown()) {
            if (loading > 0) {
                if (esc || back) closeApp();
                else finishLoading();
                return true;
            }
            if (viewing != null) return docKey(key) || super.keyPressed(key, scan, mods);
            if (esc || back) {
                closeApp();
                return true;
            }
            return listKey(key) || super.keyPressed(key, scan, mods);
        }
        // Рабочий стол
        int n = station.apps().size(), rowsPerCol = iconRows();
        switch (key) {
            case GLFW.GLFW_KEY_UP -> icon = Math.max(0, icon - 1);
            case GLFW.GLFW_KEY_DOWN -> icon = Math.min(n - 1, icon + 1);
            case GLFW.GLFW_KEY_LEFT -> icon = Math.max(0, icon - rowsPerCol);
            case GLFW.GLFW_KEY_RIGHT -> icon = Math.min(n - 1, icon + rowsPerCol);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                App a = station.apps().get(icon);
                if (a == app) minimized = false;
                else openApp(a);
            }
            case GLFW.GLFW_KEY_BACKSPACE -> { }
            default -> { return super.keyPressed(key, scan, mods); }
        }
        return true;
    }

    private boolean listKey(int key) {
        if (key == GLFW.GLFW_KEY_TAB && hasTree()) {
            focus = focus == FOCUS_TREE ? FOCUS_LIST : FOCUS_TREE;
            return true;
        }
        if (focus == FOCUS_TREE && hasTree()) {
            List<Folder> all = Folder.of(app);
            int i = all.indexOf(folder);
            switch (key) {
                case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_LEFT -> setFolder(all.get(Math.max(0, i - 1)));
                case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT -> setFolder(all.get(Math.min(all.size() - 1, i + 1)));
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> focus = FOCUS_LIST;
                default -> { return false; }
            }
            return true;
        }
        int sel = selIndex(), page = visibleRows();
        switch (key) {
            case GLFW.GLFW_KEY_UP -> select(sel - 1);
            case GLFW.GLFW_KEY_DOWN -> select(sel + 1);
            case GLFW.GLFW_KEY_PAGE_UP -> select(sel - page);
            case GLFW.GLFW_KEY_PAGE_DOWN -> select(sel + page);
            case GLFW.GLFW_KEY_HOME -> select(0);
            case GLFW.GLFW_KEY_END -> select(rows.size() - 1);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> open(sel);
            case GLFW.GLFW_KEY_LEFT -> {
                if (!hasTree()) return false;
                focus = FOCUS_TREE;
            }
            default -> { return false; }
        }
        return true;
    }

    private boolean docKey(int key) {
        int line = LabTuning.ARCHIVE_SCROLL_PX, page = Math.max(line, docH() - line);
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_BACKSPACE -> closeRecord();
            case GLFW.GLFW_KEY_UP -> docScroll -= line;
            case GLFW.GLFW_KEY_DOWN -> docScroll += line;
            case GLFW.GLFW_KEY_PAGE_UP -> docScroll -= page;
            case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_SPACE -> docScroll += page;
            case GLFW.GLFW_KEY_HOME -> docScroll = 0;
            case GLFW.GLFW_KEY_END -> docScroll = maxDocScroll();
            default -> { return false; }
        }
        clampDoc();
        return true;
    }
}
