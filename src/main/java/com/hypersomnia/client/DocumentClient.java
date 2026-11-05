package com.hypersomnia.client;

import net.minecraft.client.Minecraft;

/** Единственная точка входа в клиентский экран документа (вызывать только с клиента). */
public final class DocumentClient {
    private DocumentClient() {}

    public static void open(String id) {
        Minecraft.getInstance().setScreen(new DocumentScreen(id));
    }
}