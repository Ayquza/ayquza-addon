package com.ayquza.addon;

import com.ayquza.addon.commands.SimpleLoginCommand;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class AyquzaAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("AyquzaAddon");

    private static final SimpleLoginCommand LOGIN_COMMAND = new SimpleLoginCommand();

    @Override
    public void onInitialize() {
        ChatUtils.info("AyquzaAddon loaded!");
        Commands.add(LOGIN_COMMAND);
        MeteorClient.EVENT_BUS.subscribe(LOGIN_COMMAND);

    }

    public class QuickJoinGuiAddon extends MeteorAddon {
        @Override
        public void onInitialize() {
            MeteorClient.LOG.info("Initializing Quick Join GUI Addon");
            // No modules or commands needed, mixins handle everything
        }

        @Override
        public String getPackage() {
            return "com.example.addon";
        }
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.ayquza.addon";
    }

}
