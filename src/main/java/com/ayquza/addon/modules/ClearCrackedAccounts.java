package com.ayquza.addon.modules;

import com.ayquza.addon.AyquzaAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.systems.accounts.AccountType;
import meteordevelopment.meteorclient.systems.accounts.Accounts;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

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

    private int tickCounter = 0;

    public ClearCrackedAccounts() {
        super(AyquzaAddon.CATEGORY, "clear-cracked-accounts", "Clears all cracked accounts from the Meteor account manager.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tickCounter++;

        // 20 ticks = 1 second, 1200 ticks = 1 minute
        int ticksPerInterval = clearInterval.get() * 1200;

        if (tickCounter >= ticksPerInterval) {
            clearCrackedAccounts();
            tickCounter = 0; // Reset counter for next interval
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
            for (Account account : accountsToRemove) {
                Accounts.get().remove(account);
            }

            // Speichere die Änderungen
            Accounts.get().save();

        } catch (Exception e) {
            // Silent error handling - no chat messages
        }
    }

    @Override
    public void onDeactivate() {
        tickCounter = 0;
    }
}
