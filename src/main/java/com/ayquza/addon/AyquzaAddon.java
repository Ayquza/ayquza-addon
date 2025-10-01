package com.ayquza.addon;

import com.ayquza.addon.commands.SimpleLoginCommand;
import com.ayquza.addon.hud.ClipboardHUD;
import com.ayquza.addon.modules.*;
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
        Modules.get().add(new AccountMenuHotkey());
        System.out.println("[AyquzaAddon] AutoAccountsOnWhitelist module registered!");
        Modules.get().add(new ClipboardLoginModule());
        System.out.println("[AyquzaAddon] ClipboardLoginModule module registered!");
        Modules.get().add(new ClearCrackedAccounts());
        System.out.println("[AyquzaAddon] ClearCrackedAccounts module registered!");
        Modules.get().add(new CopyServerIPKeybind());
        System.out.println("[AyquzaAddon] CopyServerIPKeybind module registered!");
        Modules.get().add(new DisconnectScreenshot());
        System.out.println("[AyquzaAddon] DisconnectScreenshot module registered!");
        Modules.get().add(new ClipboardConnectModule());
        System.out.println("[AyquzaAddon] ClipboardConnectModule module registered!");
        Modules.get().add(new CustomRPC());
        System.out.println("[AyquzaAddon] CustomRPC module registered!");
        Modules.get().add(new FakeDisconnect());
        System.out.println("[AyquzaAddon] FakeDisconnect module registered!");


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

    // Add shutdown hook to cleanup resources when Minecraft closes
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[AyquzaAddon] Addon is shutting down...");

            // Cleanup ClearCrackedAccounts global scheduler
            ClearCrackedAccounts.shutdownGlobalScheduler();
            System.out.println("[AyquzaAddon] ClearCrackedAccounts scheduler cleaned up!");

            System.out.println("[AyquzaAddon] Addon shutdown complete!");
        }));
    }

    @Override
    public String getPackage() {
        return "com.ayquza.addon";
    }
}
