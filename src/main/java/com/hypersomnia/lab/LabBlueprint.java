package com.hypersomnia.lab;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ASCII-чертёж этажа. Формат файла:
 * <pre>
 * style real|dream
 * height 5
 * ---
 * (сетка: строка = z сверху вниз, колонка = x слева направо; север сверху)
 * ---
 * sign 1 = LINE1|LINE2|LINE3|LINE4
 * lock K = 1
 * panel B = 0        (щит рубильников; 0 = MAIN FEED, отрицательный — декоративный щит без связи)
 * station P = sec_b1 (рабочая станция ArchiveDefs.Station с этим key; можно добавить сторону экрана: station P = sec_b1 east)
 * cabinet f = cab_intake (картотечный шкаф с этой страницей DocumentDefs, кроме обычной бумаги; см. LabCabinets)
 * syringe f = 2      (в шкафу этой клетки (cabinet или обычный шкаф F/p) лежат 2 адреналиновых шприца)
 * seal o = 2         (шкаф из строки cabinet запечатан замком 2: без бумаги, открывается вместе с замком, KIND_SEAL)
 * gate G = 3         (решётка-ворота: проём как '=', 3x3, закрыт железными прутьями, пока замок 3 закрыт, KIND_GATE)
 * finale S = bed     (детская финала: bed|door|recorder|fragment|toy, FinaleTuning.PARTS, KIND_FINALE)
 * bed b = white      (декоративная кровать цвета DyeColor: изголовье к стене, изножье в комнату; обычная ванильная кровать)
 * capsule A = 47     (капсула сна с номером, KIND_CAPSULE)
 * rack a = 1 S E     (серверные стойки ServerRackBlock в линию: номер первой, куда растут номера, куда смотрят лицом; KIND_RACK)
 * stack q = 1 S E    (ряд хранилища: номер ряда, куда идут места 1.. от таблички, куда смотрят полки; KIND_STACK)
 * knock k = 8        (заложенная дверь: клетка стены, только осмотр; KIND_KNOCK, код больше не выстукивается)
 * equip e = crac N   (оборудование LabEquipmentBlock, 2 высотой: crac|ups|battery (см. EQUIP) и куда смотрит лицом)
 * </pre>
 * Символы сетки: см. {@link LabBuilder}. '@' = центр кабины лифта (общая точка выравнивания этажей).
 */
public final class LabBlueprint {
    private static final String BASE = "/data/hypersomnia/lab/";
    private static final Map<String, LabBlueprint> CACHE = new HashMap<>();
    /** Виды оборудования строки equip: прецизионный кондиционер, шкаф ИБП, батарейный шкаф (индекс = equips[c][0]). */
    public static final String[] EQUIP = {"crac", "ups", "battery"};

    /** Символ, который LabBuilder.furniture обрабатывает как мебель; карты и замки важнее. */
    public static boolean furnitureSymbol(char c) {
        return switch (c) {
            case 'T', 'R', 'D', 'd', 'V', 'Z', 'F', 'p', 'h', 'H', 'C', 'I', 'v', 'w', 'r', 'u', '+', '~' -> true;
            default -> false;
        };
    }

    public final String name;
    public final boolean dream;
    public final int height;
    public final int w;
    public final int d;
    public final int ax;
    public final int az;
    public final Map<Character, String[]> signs = new HashMap<>();
    public final Map<Character, Integer> locks = new HashMap<>();
    /** Символ клетки -> id щита (LabBreakers; 0 = MAIN FEED, < 0 — декоративный, без связи). */
    public final Map<Character, Integer> panels = new HashMap<>();
    /** Символ клетки -> {key станции, сторона экрана или null (тогда от стены, как у табличек)}. */
    public final Map<Character, String[]> stations = new HashMap<>();
    /** Символ клетки -> id страницы DocumentDefs в этом шкафу. */
    public final Map<Character, String> cabinets = new HashMap<>();
    /** Символ шкафа (cabinet или обычный F/p) -> сколько адреналиновых шприцев лежит внутри. */
    public final Map<Character, Integer> syringes = new HashMap<>();
    /** Символ шкафа из cabinets -> id замка, который его запечатывает. */
    public final Map<Character, Integer> seals = new HashMap<>();
    /** Символ клетки -> id замка решётки-ворот. */
    public final Map<Character, Integer> gates = new HashMap<>();
    /** Символ клетки -> часть детской финала (FinaleTuning.PART_*). */
    public final Map<Character, Integer> finale = new HashMap<>();
    /** Символ клетки -> номер капсулы. */
    public final Map<Character, Integer> capsules = new HashMap<>();
    /** Символ клетки -> цвет декоративной кровати (имя DyeColor: white, light_blue...). */
    public final Map<Character, String> beds = new HashMap<>();
    /** Символ клетки -> {номер первой стойки, Direction 2D роста номеров, Direction 2D лица}. */
    public final Map<Character, int[]> racks = new HashMap<>();
    /** Символ клетки -> {номер ряда, Direction 2D роста мест, Direction 2D лица полок}. */
    public final Map<Character, int[]> stacks = new HashMap<>();
    /** Символ клетки -> id замка, чей код выстукивают за этой стеной. */
    public final Map<Character, Integer> knocks = new HashMap<>();
    /** Символ клетки -> {индекс вида в EQUIP, Direction 2D лица}. */
    public final Map<Character, int[]> equips = new HashMap<>();
    private final char[][] grid;

    private LabBlueprint(String name, boolean dream, int height, char[][] grid, int w, int d, int ax, int az) {
        this.name = name;
        this.dream = dream;
        this.height = height;
        this.grid = grid;
        this.w = w;
        this.d = d;
        this.ax = ax;
        this.az = az;
    }

    public static boolean exists(String name) {
        return LabBlueprint.class.getResource(BASE + name + ".txt") != null;
    }

    public static synchronized LabBlueprint get(String name) {
        LabBlueprint bp = CACHE.get(name);
        if (bp == null) {
            bp = load(name);
            CACHE.put(name, bp);
        }
        return bp;
    }

    /** Символ клетки; за пределами сетки пробел (пусто). */
    public char at(int x, int z) {
        if (z < 0 || z >= d || x < 0 || x >= w) return ' ';
        return grid[z][x];
    }

    /** «1 S E» -> {1, Direction 2D (S), Direction 2D (E)}; стороны — N, S, E, W. */
    private static int[] line(String name, String val) {
        String[] v = val.trim().split("\\s+");
        if (v.length != 3) throw new IllegalStateException("Lab blueprint " + name + ": expected '<n> <dir> <dir>': " + val);
        return new int[]{Integer.parseInt(v[0]), side(name, v[1]).get2DDataValue(), side(name, v[2]).get2DDataValue()};
    }

    /** «crac N» -> {индекс вида в EQUIP, Direction 2D лица}. */
    private static int[] equip(String name, String val) {
        String[] v = val.trim().split("\\s+");
        int kind = v.length == 2 ? java.util.Arrays.asList(EQUIP).indexOf(v[0]) : -1;
        if (kind < 0) throw new IllegalStateException("Lab blueprint " + name + ": expected '<crac|ups|battery> <dir>': " + val);
        return new int[]{kind, side(name, v[1]).get2DDataValue()};
    }

    private static net.minecraft.core.Direction side(String name, String v) {
        return switch (v.toUpperCase(java.util.Locale.ROOT)) {
            case "N" -> net.minecraft.core.Direction.NORTH;
            case "S" -> net.minecraft.core.Direction.SOUTH;
            case "E" -> net.minecraft.core.Direction.EAST;
            case "W" -> net.minecraft.core.Direction.WEST;
            default -> throw new IllegalStateException("Lab blueprint " + name + ": unknown side " + v);
        };
    }

    private static LabBlueprint load(String name) {
        try (InputStream in = LabBlueprint.class.getResourceAsStream(BASE + name + ".txt")) {
            if (in == null) throw new IllegalStateException("Lab blueprint not found: " + name);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> rows = new ArrayList<>();
            Map<Character, String[]> signs = new HashMap<>();
            Map<Character, Integer> locks = new HashMap<>();
            Map<Character, Integer> panels = new HashMap<>();
            Map<Character, String[]> stations = new HashMap<>();
            Map<Character, String> cabinets = new HashMap<>();
            Map<Character, Integer> syringes = new HashMap<>();
            Map<Character, Integer> seals = new HashMap<>();
            Map<Character, Integer> gates = new HashMap<>();
            Map<Character, Integer> finale = new HashMap<>();
            Map<Character, Integer> capsules = new HashMap<>();
            Map<Character, String> beds = new HashMap<>();
            Map<Character, int[]> racks = new HashMap<>();
            Map<Character, int[]> stacks = new HashMap<>();
            Map<Character, Integer> knocks = new HashMap<>();
            Map<Character, int[]> equips = new HashMap<>();
            boolean dream = false;
            int height = 5;
            int section = 0;
            String line;
            while ((line = r.readLine()) != null) {
                if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
                if (line.equals("---")) {
                    section++;
                    continue;
                }
                if (section == 1) {
                    rows.add(line);
                    continue;
                }
                String t = line.trim();
                if (t.isEmpty() || t.startsWith(";")) continue;
                if (section == 0) {
                    if (t.startsWith("style ")) dream = t.substring(6).trim().equals("dream");
                    else if (t.startsWith("height ")) height = Integer.parseInt(t.substring(7).trim());
                } else {
                    int eq = t.indexOf('=');
                    if (eq < 0) continue;
                    String key = t.substring(0, eq).trim();
                    String val = t.substring(eq + 1).trim();
                    if (key.startsWith("sign ")) signs.put(key.substring(5).trim().charAt(0), val.split("\\|", -1));
                    else if (key.startsWith("lock ")) locks.put(key.substring(5).trim().charAt(0), Integer.parseInt(val));
                    else if (key.startsWith("panel ")) panels.put(key.substring(6).trim().charAt(0), Integer.parseInt(val));
                    else if (key.startsWith("cabinet ")) cabinets.put(key.substring(8).trim().charAt(0), val);
                    else if (key.startsWith("syringe ")) syringes.put(key.substring(8).trim().charAt(0), Integer.parseInt(val));
                    else if (key.startsWith("seal ")) seals.put(key.substring(5).trim().charAt(0), Integer.parseInt(val));
                    else if (key.startsWith("gate ")) gates.put(key.substring(5).trim().charAt(0), Integer.parseInt(val));
                    else if (key.startsWith("capsule ")) capsules.put(key.substring(8).trim().charAt(0), Integer.parseInt(val));
                    else if (key.startsWith("bed ")) beds.put(key.substring(4).trim().charAt(0), val);
                    else if (key.startsWith("rack ")) racks.put(key.substring(5).trim().charAt(0), line(name, val));
                    else if (key.startsWith("stack ")) stacks.put(key.substring(6).trim().charAt(0), line(name, val));
                    else if (key.startsWith("knock ")) knocks.put(key.substring(6).trim().charAt(0), Integer.parseInt(val));
                    else if (key.startsWith("equip ")) equips.put(key.substring(6).trim().charAt(0), equip(name, val));
                    else if (key.startsWith("finale ")) {
                        int part = java.util.Arrays.asList(com.hypersomnia.finale.FinaleTuning.PARTS).indexOf(val);
                        if (part < 0) throw new IllegalStateException("Lab blueprint " + name + ": unknown finale part " + val);
                        finale.put(key.substring(7).trim().charAt(0), part);
                    }
                    else if (key.startsWith("station ")) {
                        String[] v = val.split("\\s+");
                        stations.put(key.substring(8).trim().charAt(0), new String[]{v[0], v.length > 1 ? v[1] : null});
                    }
                }
            }
            int w = 0;
            for (String s : rows) w = Math.max(w, s.length());
            int d = rows.size();
            char[][] grid = new char[d][w];
            int ax = -1, az = -1;
            for (int z = 0; z < d; z++) {
                String s = rows.get(z);
                for (int x = 0; x < w; x++) {
                    char c = x < s.length() ? s.charAt(x) : ' ';
                    grid[z][x] = c;
                    if (c == '@') {
                        ax = x;
                        az = z;
                    }
                }
            }
            if (ax < 0) throw new IllegalStateException("Lab blueprint " + name + ": no '@' cell");
            LabBlueprint bp = new LabBlueprint(name, dream, height, grid, w, d, ax, az);
            bp.signs.putAll(signs);
            bp.locks.putAll(locks);
            bp.panels.putAll(panels);
            bp.stations.putAll(stations);
            bp.cabinets.putAll(cabinets);
            bp.syringes.putAll(syringes);
            bp.seals.putAll(seals);
            bp.gates.putAll(gates);
            bp.finale.putAll(finale);
            bp.capsules.putAll(capsules);
            bp.beds.putAll(beds);
            bp.racks.putAll(racks);
            bp.stacks.putAll(stacks);
            bp.knocks.putAll(knocks);
            bp.equips.putAll(equips);
            return bp;
        } catch (IOException e) {
            throw new IllegalStateException("Lab blueprint " + name + ": " + e.getMessage(), e);
        }
    }
}
