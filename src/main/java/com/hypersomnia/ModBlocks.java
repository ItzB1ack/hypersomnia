package com.hypersomnia;

import com.hypersomnia.dream.DreamClockBlock;
import com.hypersomnia.lab.AntennaDriveBlock;
import com.hypersomnia.lab.BreakerPanelBlock;
import com.hypersomnia.lab.CabinetBlock;
import com.hypersomnia.lab.CapsuleBlock;
import com.hypersomnia.lab.ChairBlock;
import com.hypersomnia.lab.FurnitureBlock;
import com.hypersomnia.lab.KeypadBlock;
import com.hypersomnia.lab.LabEquipmentBlock;
import com.hypersomnia.lab.LabSignBlock;
import com.hypersomnia.lab.LabTuning;
import com.hypersomnia.lab.LiftPanelBlock;
import com.hypersomnia.lab.LightTubeBlock;
import com.hypersomnia.lab.MonitorBlock;
import com.hypersomnia.lab.ServerRackBlock;
import com.hypersomnia.lab.UnlitTubeBlock;
import com.hypersomnia.lab.WorkstationBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hypersomnia.MOD_ID);

    /** Общие свойства блоков лаборатории: неразрушаемые, без дропа, не толкаются поршнем. */
    private static BlockBehaviour.Properties lab(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .sound(sound)
                .strength(-1.0f, 3600000.0f)
                .noLootTable()
                .pushReaction(PushReaction.BLOCK);
    }

    /** Щит MAIN FEED и щиты этажей: один блок на все рубильники (экран щита). Лампа ON слабо светится (LabTuning.PANEL_LIGHT),
     *  TRIPPED — ещё слабее (PANEL_TRIPPED_LIGHT): в темноте «обесточено» видно щит. */
    public static final DeferredBlock<BreakerPanelBlock> BREAKER_PANEL = BLOCKS.register("breaker_panel", () -> new BreakerPanelBlock(
            lab(MapColor.METAL, SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> switch (state.getValue(BreakerPanelBlock.LAMP)) {
                        case ON -> LabTuning.PANEL_LIGHT;
                        case TRIPPED -> LabTuning.PANEL_TRIPPED_LIGHT;
                        default -> 0;
                    })));

    /** Рабочая станция CRADLE WORKSTATION: стоит на столе DESK; экран слабо светится (LabTuning.WS_LIGHT). */
    public static final DeferredBlock<WorkstationBlock> WORKSTATION = BLOCKS.register("workstation", () -> new WorkstationBlock(
            lab(MapColor.COLOR_GRAY, SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> LabTuning.WS_LIGHT)));

    /** Стол: цельный блок (столешница и тумба). Под станциями и мониторами, а также сам по себе. */
    public static final DeferredBlock<FurnitureBlock> DESK = BLOCKS.register("desk", () -> new FurnitureBlock(
            lab(MapColor.COLOR_GRAY, SoundType.METAL)));

    /** Картотечный шкаф: три ящика (экран CabinetScreen), OPEN = верхний ящик выдвинут, пока шкаф открыт. Содержимое кладёт LabCabinets. */
    public static final DeferredBlock<CabinetBlock> CABINET = BLOCKS.register("cabinet", () -> new CabinetBlock(
            lab(MapColor.METAL, SoundType.METAL)));

    /** Офисный стул: сиденье на газлифте и спинка, FACING = куда смотрит сиденье. На стул можно сесть (SeatEntity). */
    public static final DeferredBlock<ChairBlock> CHAIR = BLOCKS.register("chair", () -> new ChairBlock(
            lab(MapColor.COLOR_BLACK, SoundType.METAL)
                    .noOcclusion()));

    /**
     * Капсула сна зала сеанса B5 (2 блока CC): ложе с бортами и крышкой. OPEN = крышка поднята:
     * в открытой можно лежать (SeatEntity.lie), но сон не начинается; закрытая — только осмотр.
     */
    public static final DeferredBlock<CapsuleBlock> CAPSULE = BLOCKS.register("capsule", () -> new CapsuleBlock(
            lab(MapColor.COLOR_LIGHT_GRAY, SoundType.METAL)
                    .noOcclusion()));

    /** Монитор наблюдения: погасший или со «снегом» (LIT, свет LabTuning.MONITOR_LIGHT). */
    public static final DeferredBlock<MonitorBlock> MONITOR = BLOCKS.register("monitor", () -> new MonitorBlock(
            lab(MapColor.COLOR_GRAY, SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(MonitorBlock.LIT) ? LabTuning.MONITOR_LIGHT : 0)));

    /** Кодовая панель замка на стене: OPEN = замок открыт (светлая лампа), иначе лампа красная. Ввод кода — экран LabLockScreen. */
    public static final DeferredBlock<KeypadBlock> KEYPAD = BLOCKS.register("keypad", () -> new KeypadBlock(
            lab(MapColor.METAL, SoundType.METAL)
                    .noOcclusion()));

    /** Панель лифта в кабине: STOP = этаж (номер на табло, кнопка этажа светится). Выбор этажа — экран LabLiftScreen. */
    public static final DeferredBlock<LiftPanelBlock> LIFT_PANEL = BLOCKS.register("lift_panel", () -> new LiftPanelBlock(
            lab(MapColor.METAL, SoundType.METAL)
                    .noOcclusion()));

    /** Серверная стойка B3 (2 блока): лампы состояния и адреса буфера; верхняя половина с горящей лампой слабо светится. */
    public static final DeferredBlock<ServerRackBlock> SERVER_RACK = BLOCKS.register("server_rack", () -> new ServerRackBlock(
            lab(MapColor.COLOR_BLACK, SoundType.METAL)
                    .lightLevel(state -> state.getValue(ServerRackBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                            && state.getValue(ServerRackBlock.STATUS) != ServerRackBlock.Status.OFF ? LabTuning.RACK_LIGHT : 0)));

    /** Оборудование серверной B3 (2 блока, декор, LabEquipmentBlock): прецизионный кондиционер, шкаф ИБП, батарейный шкаф. */
    public static final DeferredBlock<LabEquipmentBlock> CRAC_UNIT = BLOCKS.register("crac_unit", () -> new LabEquipmentBlock(
            lab(MapColor.COLOR_LIGHT_GRAY, SoundType.METAL)));
    public static final DeferredBlock<LabEquipmentBlock> UPS_CABINET = BLOCKS.register("ups_cabinet", () -> new LabEquipmentBlock(
            lab(MapColor.COLOR_GRAY, SoundType.METAL)));
    public static final DeferredBlock<LabEquipmentBlock> BATTERY_CABINET = BLOCKS.register("battery_cabinet", () -> new LabEquipmentBlock(
            lab(MapColor.COLOR_GRAY, SoundType.METAL)));

    /** Поворотная каретка антенны ANT-1 на крыше павильона: штурвал (FACING) поворачивает тарелку, LabAntenna через LabHandler. */
    public static final DeferredBlock<AntennaDriveBlock> ANTENNA_DRIVE = BLOCKS.register("antenna_drive", () -> new AntennaDriveBlock(
            lab(MapColor.COLOR_GRAY, SoundType.METAL)
                    .noOcclusion()));

    /** [новое] Потолочный светильник: металлическая планка и матовая трубка (LightTubeBlock), свет LabTuning.TUBE_LIGHT. */
    public static final DeferredBlock<LightTubeBlock> LIGHT_TUBE = BLOCKS.register("light_tube", () -> new LightTubeBlock(
            lab(MapColor.QUARTZ, SoundType.GLASS)
                    .noOcclusion()
                    .forceSolidOff()
                    .lightLevel(state -> LabTuning.TUBE_LIGHT)));

    /** Погасшая трубка (LabOutage, «обесточено»): та же модель светильника без света; предмета нет. */
    public static final DeferredBlock<UnlitTubeBlock> UNLIT_TUBE = BLOCKS.register("unlit_tube", () -> new UnlitTubeBlock(
            lab(MapColor.QUARTZ, SoundType.GLASS)
                    .noOcclusion()
                    .forceSolidOff()));

    /** Табличка лаборатории: тёмная пластина на стене; строки хранит LabSignBlockEntity, рисует LabSignRenderer. */
    public static final DeferredBlock<LabSignBlock> LAB_SIGN = BLOCKS.register("lab_sign", () -> new LabSignBlock(
            lab(MapColor.COLOR_BLACK, SoundType.METAL)
                    .noOcclusion()
                    .noCollission()));

    /**
     * [новое] Напольные часы в мире снов (DreamClock): два блока в высоту, неразрушаемые, без дропа.
     * Предмета нет: часы ставит серверный код, ПКМ по ним будит игрока.
     */
    public static final DeferredBlock<DreamClockBlock> DREAM_CLOCK = BLOCKS.register("dream_clock", () -> new DreamClockBlock(
            lab(MapColor.WOOD, SoundType.WOOD)
                    .noOcclusion()));
}
