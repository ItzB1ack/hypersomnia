package com.hypersomnia.document;

import com.hypersomnia.ModItems;
import com.hypersomnia.client.DocumentClient;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

/** Один предмет на все страницы и воспоминания: id страницы лежит в custom_data ("doc"). */
public class DocumentItem extends Item {
    public static final String TAG = "doc";

    public DocumentItem(Properties properties) {
        super(properties);
    }

    public static ItemStack make(String id) {
        ItemStack stack = new ItemStack(ModItems.DOCUMENT.get());
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG, id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        // custom_model_data = индекс страницы в DocumentDefs + 1, модели генерирует tools/make_documents.py
        int model = DocumentDefs.modelIndex(id);
        if (model > 0) stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(model));
        return stack;
    }

    public static String idOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(TAG);
    }

    @Override
    public Component getName(ItemStack stack) {
        DocumentDefs.Doc doc = DocumentDefs.get(idOf(stack));
        if (doc == null) return super.getName(stack);
        return Component.translatableWithFallback("hypersomnia.doc." + doc.id() + ".title", doc.title());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String id = idOf(stack);
        if (DocumentDefs.get(id) == null) return InteractionResultHolder.fail(stack);
        if (level.isClientSide) DocumentClient.open(id);
        return InteractionResultHolder.success(stack);
    }
}