package com.hypersomnia.server;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/** Проверяет журнал скрытых блоков лифта: версию, round-trip, дубликаты и битые записи. */
public final class LiftRideStorageSelfTest {
    private static int checks;

    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }

    /** Bootstrap без запуска игры: NeoForge ждёт загруженный список модов даже для ванильных блоков. */
    private static void bootstrap() throws Exception {
        Class<?> loading = Class.forName("net.neoforged.fml.loading.LoadingModList");
        Method of = loading.getMethod("of", List.class, List.class, List.class, List.class, Map.class);
        Object instance = of.invoke(null, List.of(), List.of(), List.of(), List.of(), Map.of());
        Field field = loading.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        field.set(null, instance);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static CompoundTag state(String name) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        return tag;
    }

    private static BlockState stateAt(CompoundTag saved, HolderLookup.Provider provider, long pos) {
        ListTag list = saved.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.getLong("pos") == pos)
                return NbtUtils.readBlockState(provider.lookupOrThrow(Registries.BLOCK), entry.getCompound("state"));
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        bootstrap();
        HolderLookup.Provider provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

        LiftRideStorage empty = new LiftRideStorage();
        CompoundTag emptyTag = empty.save(new CompoundTag(), null);
        check(emptyTag.getInt("version") == LiftRideStorage.DATA_VERSION, "lift save writes the current version");
        LiftRideStorage emptyCopy = LiftRideStorage.load(emptyTag, null);
        check(emptyCopy.save(new CompoundTag(), provider).getList("blocks", Tag.TAG_COMPOUND).isEmpty(),
                "empty journal loads without a registry provider");

        BlockPos stone = new BlockPos(1, 64, 2);
        BlockPos planks = new BlockPos(-3, 70, 8);
        LiftRideStorage storage = new LiftRideStorage();
        storage.remember(stone, Blocks.STONE.defaultBlockState());
        storage.remember(planks, Blocks.OAK_PLANKS.defaultBlockState());
        CompoundTag saved = storage.save(new CompoundTag(), null);
        check(saved.getList("blocks", Tag.TAG_COMPOUND).size() == 2, "both hidden blocks are saved");
        LiftRideStorage copy = LiftRideStorage.load(saved, provider);
        CompoundTag copyTag = copy.save(new CompoundTag(), provider);
        check(copyTag.getList("blocks", Tag.TAG_COMPOUND).size() == 2, "both hidden blocks survive reload");
        check(Blocks.STONE.defaultBlockState().equals(stateAt(copyTag, provider, stone.asLong())),
                "stone state round-trips");
        check(Blocks.OAK_PLANKS.defaultBlockState().equals(stateAt(copyTag, provider, planks.asLong())),
                "planks state round-trips");
        copy.forget(stone);
        CompoundTag forgotten = copy.save(new CompoundTag(), provider);
        check(forgotten.getList("blocks", Tag.TAG_COMPOUND).size() == 1
                && stateAt(forgotten, provider, stone.asLong()) == null, "forget removes one block");
        check(stateAt(forgotten, provider, planks.asLong()) != null, "forget keeps the other block");

        // Дубликат позиции: карта хранит последнее состояние.
        CompoundTag duplicate = new CompoundTag();
        ListTag duplicateList = new ListTag();
        CompoundTag first = new CompoundTag();
        first.putLong("pos", stone.asLong());
        first.put("state", NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));
        CompoundTag second = new CompoundTag();
        second.putLong("pos", stone.asLong());
        second.put("state", NbtUtils.writeBlockState(Blocks.OAK_PLANKS.defaultBlockState()));
        duplicateList.add(first);
        duplicateList.add(second);
        duplicate.put("blocks", duplicateList);
        LiftRideStorage dedup = LiftRideStorage.load(duplicate, provider);
        CompoundTag dedupTag = dedup.save(new CompoundTag(), provider);
        check(dedupTag.getList("blocks", Tag.TAG_COMPOUND).size() == 1, "duplicate position is stored once");
        check(Blocks.OAK_PLANKS.defaultBlockState().equals(stateAt(dedupTag, provider, stone.asLong())),
                "duplicate position keeps the last state");

        // Битые записи не должны ронять загрузку и не должны возвращать воздух в мир.
        CompoundTag broken = new CompoundTag();
        ListTag brokenList = new ListTag();
        CompoundTag valid = new CompoundTag();
        valid.putLong("pos", planks.asLong());
        valid.put("state", NbtUtils.writeBlockState(Blocks.OAK_PLANKS.defaultBlockState()));
        brokenList.add(valid);
        CompoundTag noPos = new CompoundTag();
        noPos.put("state", NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));
        brokenList.add(noPos);
        CompoundTag noState = new CompoundTag();
        noState.putLong("pos", stone.asLong());
        brokenList.add(noState);
        CompoundTag unknown = new CompoundTag();
        unknown.putLong("pos", BlockPos.asLong(0, 0, 0));
        unknown.put("state", state("hypersomnia:no_such_block"));
        brokenList.add(unknown);
        CompoundTag invalid = new CompoundTag();
        invalid.putLong("pos", BlockPos.asLong(0, 1, 0));
        invalid.put("state", state("not a valid id"));
        brokenList.add(invalid);
        broken.put("blocks", brokenList);
        LiftRideStorage cleaned = LiftRideStorage.load(broken, provider);
        CompoundTag cleanedTag = cleaned.save(new CompoundTag(), provider);
        check(cleanedTag.getList("blocks", Tag.TAG_COMPOUND).size() == 1, "malformed journal entries are skipped");
        check(stateAt(cleanedTag, provider, planks.asLong()) != null, "valid journal entry is kept");
        check(stateAt(cleanedTag, provider, BlockPos.asLong(0, 0, 0)) == null,
                "unknown block is not restored at the origin");
        check(stateAt(cleanedTag, provider, BlockPos.asLong(0, 1, 0)) == null,
                "invalid block id is not restored at the origin");

        System.out.println("LiftRideStorageSelfTest: " + checks + " checks passed.");
    }
}
