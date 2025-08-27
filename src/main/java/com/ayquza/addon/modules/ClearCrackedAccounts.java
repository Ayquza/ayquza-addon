package com.ayquza.addon.modules;

import com.ayquza.addon.AyquzaAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.systems.accounts.AccountType;
import meteordevelopment.meteorclient.systems.accounts.Accounts;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    private ScheduledExecutorService scheduler;

    public ClearCrackedAccounts() {
        super(AyquzaAddon.CATEGORY, "clear-cracked-accounts", "Clears all cracked accounts from the Meteor account manager.");
    }

    @Override
    public void onActivate() {
        // Erstelle einen neuen Scheduler
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ClearCrackedAccounts-Timer");
            thread.setDaemon(true); // Daemon Thread - wird beendet wenn Minecraft schließt
            return thread;
        });

        // Starte den Timer - läuft unabhängig von Tick-Events
        scheduler.scheduleAtFixedRate(
            this::clearCrackedAccounts,
            clearInterval.get(), // Initial delay
            clearInterval.get(), // Repeat interval
            TimeUnit.MINUTES
        );
    }

    @Override
    public void onDeactivate() {
        // Stoppe und bereinige den Scheduler
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                // Warte maximal 1 Sekunde auf das Beenden
                if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
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
            // Silent error handling - keine Chat-Nachrichten
            e.printStackTrace(); // Für Debug-Zwecke
        }
    }

    // Optional: Methode um das Intervall zur Laufzeit zu ändern
    public void updateInterval() {
        if (isActive()) {
            onDeactivate(); // Stoppe aktuellen Timer
            onActivate();   // Starte mit neuem Intervall
        }
    }
}
