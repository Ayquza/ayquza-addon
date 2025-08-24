package com.ayquza.addon.modules;

import com.ayquza.addon.AyquzaAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.systems.accounts.AccountType;
import meteordevelopment.meteorclient.systems.accounts.Accounts;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public class ClearCrackedAccounts extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> clearInterval = sgGeneral.add(new IntSetting.Builder()
        .name("clear-interval-in-min")
        .description("Interval in minutes to clear cracked accounts.")
        .defaultValue(5)
        .min(1)
        .max(60)
        .sliderMax(30)
        .build()
    );

    private long startTime;
    private long lastClearTime;

    public ClearCrackedAccounts() {
        super(AyquzaAddon.CATEGORY, "clear-cracked-accounts", "Clears all cracked accounts from the Meteor account manager.");
    }

    @Override
    public void onActivate() {
        startTime = System.currentTimeMillis();
        lastClearTime = startTime;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Prüfe jeden Tick (aber nur wenn wir in einer Welt sind)
        checkAndClearAccounts();
    }

    // Alternative: Verwende einen separaten Thread für kontinuierliche Zeitprüfung
    private void checkAndClearAccounts() {
        long currentTime = System.currentTimeMillis();
        long intervalMillis = clearInterval.get() * 60 * 1000; // Minuten zu Millisekunden

        if (currentTime - lastClearTime >= intervalMillis) {
            clearCrackedAccounts();
            lastClearTime = currentTime;
        }
    }

    // Zusätzliche Methode: Prüfung auch außerhalb von Ticks
    public void checkClearAccounts() {
        if (isActive()) {
            checkAndClearAccounts();
        }
    }

    private void clearCrackedAccounts() {
        try {
            List<Account> accountsToRemove = new ArrayList<>();

            // Sammle alle cracked accounts
            for (Account account : Accounts.get()) {
                if (account.getType() == AccountType.Cracked) {
                    accountsToRemove.add(account);
                }
            }

            // Entferne die cracked accounts
            if (!accountsToRemove.isEmpty()) {
                for (Account account : accountsToRemove) {
                    Accounts.get().remove(account);
                }

                // Speichere die Änderungen
                Accounts.get().save();

                // Optional: Debug-Nachricht (nur für Testing)
                // info("Cleared " + accountsToRemove.size() + " cracked accounts");
            }

        } catch (Exception e) {
            // Silent error handling - no chat messages
        }
    }

    @Override
    public void onDeactivate() {
        // Reset timers
        startTime = 0;
        lastClearTime = 0;
    }
}
