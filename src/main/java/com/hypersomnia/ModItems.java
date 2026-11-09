package com.hypersomnia;

import com.hypersomnia.syringe.AdrenalineSyringeItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hypersomnia.MOD_ID);

    public static final DeferredItem<Item> ADRENALINE_SYRINGE = ITEMS.register(
            "adrenaline_syringe",
            () -> new AdrenalineSyringeItem(new Item.Properties().stacksTo(8))
    );

    /** Один предмет на все страницы и воспоминания, id в custom_data. */
    public static final DeferredItem<Item> DOCUMENT = ITEMS.register(
            "document",
            () -> new com.hypersomnia.document.DocumentItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> DIARY = ITEMS.register(
            "diary",
            () -> new com.hypersomnia.document.DiaryItem(new Item.Properties().stacksTo(1))
    );

    /** Зайка (рисунок 4, запись «Норы»): след TOY у кровати рисуется этим предметом (TraceType.asset); в мир сам не выдаётся. */
    public static final DeferredItem<Item> BUNNY = ITEMS.registerSimpleItem("bunny", new Item.Properties().stacksTo(1));

    /** Блоки лаборатории — только для творческого режима и отладки: в лаборатории их ставит LabBuilder. Поставленный вручную щит пишет NOT CONNECTED. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BREAKER_PANEL =
            ITEMS.registerSimpleBlockItem("breaker_panel", ModBlocks.BREAKER_PANEL);
    /** Тоже только для творческого режима: поставленная вручную станция пишет NO NETWORK, станции лаборатории ставит LabBuilder. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> WORKSTATION =
            ITEMS.registerSimpleBlockItem("workstation", ModBlocks.WORKSTATION);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> DESK =
            ITEMS.registerSimpleBlockItem("desk", ModBlocks.DESK);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CABINET =
            ITEMS.registerSimpleBlockItem("cabinet", ModBlocks.CABINET);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MONITOR =
            ITEMS.registerSimpleBlockItem("monitor", ModBlocks.MONITOR);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CHAIR =
            ITEMS.registerSimpleBlockItem("chair", ModBlocks.CHAIR);
    /** Кодовая панель и панель лифта, поставленные вручную, пишут NOT CONNECTED: в лаборатории их ставит LabBuilder. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> KEYPAD =
            ITEMS.registerSimpleBlockItem("keypad", ModBlocks.KEYPAD);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SERVER_RACK =
            ITEMS.registerSimpleBlockItem("server_rack", ModBlocks.SERVER_RACK);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CRAC_UNIT =
            ITEMS.registerSimpleBlockItem("crac_unit", ModBlocks.CRAC_UNIT);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> UPS_CABINET =
            ITEMS.registerSimpleBlockItem("ups_cabinet", ModBlocks.UPS_CABINET);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BATTERY_CABINET =
            ITEMS.registerSimpleBlockItem("battery_cabinet", ModBlocks.BATTERY_CABINET);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> LIFT_PANEL =
            ITEMS.registerSimpleBlockItem("lift_panel", ModBlocks.LIFT_PANEL);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ANTENNA_DRIVE =
            ITEMS.registerSimpleBlockItem("antenna_drive", ModBlocks.ANTENNA_DRIVE);
    /** [новое] Потолочный светильник: в лаборатории его ставит LabBuilder, предмет — для творческого режима. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> LIGHT_TUBE =
            ITEMS.registerSimpleBlockItem("light_tube", ModBlocks.LIGHT_TUBE);

    /** [новое] Остальные блоки мода для творческой вкладки. Многоблочные (капсулы, часы) ставят себя целиком сами. */
    /** [новое] Ключ-карта капсул: ПКМ по капсуле (лежачей или стоячей) открывает/закрывает её целиком. Выдаётся из творческой вкладки. */
    public static final DeferredItem<Item> KEYCARD = ITEMS.registerSimpleItem("keycard", new Item.Properties().stacksTo(1));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CAPSULE =
            ITEMS.registerSimpleBlockItem("capsule", ModBlocks.CAPSULE);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CAPSULE_UPRIGHT =
            ITEMS.registerSimpleBlockItem("capsule_upright", ModBlocks.CAPSULE_UPRIGHT);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> UNLIT_TUBE =
            ITEMS.registerSimpleBlockItem("unlit_tube", ModBlocks.UNLIT_TUBE);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> LAB_SIGN =
            ITEMS.registerSimpleBlockItem("lab_sign", ModBlocks.LAB_SIGN);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> DREAM_CLOCK =
            ITEMS.registerSimpleBlockItem("dream_clock", ModBlocks.DREAM_CLOCK);
}
