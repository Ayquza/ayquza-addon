package com.ayquza.addon;

import com.ayquza.addon.commands.SimpleLoginCommand;
import com.ayquza.addon.modules.AutoAccountsOnWhitelist;
import com.ayquza.addon.modules.ClipboardLoginModule;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

public class AyquzaAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("AyquzaAddon");

    private static final SimpleLoginCommand LOGIN_COMMAND = new SimpleLoginCommand();

    @Override
    public void onInitialize() {
        ChatUtils.info("AyquzaAddon loaded!");
        System.out.println("[AyquzaAddon] Addon wird geladen...");


        Commands.add(LOGIN_COMMAND);
        MeteorClient.EVENT_BUS.subscribe(LOGIN_COMMAND);


        Modules.get().add(new AutoAccountsOnWhitelist());
        System.out.println("[AyquzaAddon] AutoAccountsOnWhitelist Modul registriert!");
        Modules.get().add(new ClipboardLoginModule());
        System.out.println("[AyquzaAddon] ClipboardLoginModule registered!");


        System.out.println("[AyquzaAddon] Addon erfolgreich geladen!");
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
        System.out.println("[AyquzaAddon] Kategorie registriert!");
    }

    @Override
    public String getPackage() {
        return "com.ayquza.addon";
    }
}
