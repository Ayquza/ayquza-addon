package com.ayquza.addon;

import com.ayquza.addon.commands.SimpleLoginCommand;
import com.ayquza.addon.hud.ClipboardHUD;
import com.ayquza.addon.modules.AutoAccountsOnWhitelist;
import com.ayquza.addon.modules.ClearCrackedAccounts;
import com.ayquza.addon.modules.ClipboardLoginModule;
import com.ayquza.addon.modules.CopyServerIPKeybind;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

public class AyquzaAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("AyquzaAddon");

    private static final SimpleLoginCommand LOGIN_COMMAND = new SimpleLoginCommand();


    @Override
    public void onInitialize() {
        ChatUtils.info("AyquzaAddon loaded!");
        System.out.println("[AyquzaAddon] Addon is loading...");

        // Register commands
        Commands.add(LOGIN_COMMAND);
        MeteorClient.EVENT_BUS.subscribe(LOGIN_COMMAND);


        // Register modules
        Modules.get().add(new AutoAccountsOnWhitelist());
        System.out.println("[AyquzaAddon] AutoAccountsOnWhitelist module registered!");
        Modules.get().add(new ClipboardLoginModule());
        System.out.println("[AyquzaAddon] ClipboardLoginModule module registered!");
        Modules.get().add(new ClearCrackedAccounts());
        System.out.println("[AyquzaAddon] ClearCrackedAccounts module registered!");
        Modules.get().add(new CopyServerIPKeybind());
        System.out.println("[AyquzaAddon] CopyServerIPKeybind module registered!");


        // Register HUD elements
        Hud.get().register(ClipboardHUD.INFO);
        System.out.println("[AyquzaAddon] ClipboardHUD registered!");

        System.out.println("[AyquzaAddon] Addon loaded successfully!");
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
        System.out.println("[AyquzaAddon] Category registered!");
    }

    @Override
    public String getPackage() {
        return "com.ayquza.addon";
    }
}
