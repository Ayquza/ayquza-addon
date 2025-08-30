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
import java.util.concurrent.ScheduledFuture;
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

    // Global scheduler and timer - persistent across server switches
    private static ScheduledExecutorService globalScheduler;
    private static ScheduledFuture<?> currentTask;
    private static int activeModules = 0; // Counts active module instances
    private static int lastInterval = -1; // Last used interval

    // Synchronization object
    private static final Object schedulerLock = new Object();

    public ClearCrackedAccounts() {
        super(AyquzaAddon.CATEGORY, "clear-cracked-accounts", "Clears all cracked accounts from the Meteor account manager.");
    }

    @Override
    public void onActivate() {
        synchronized (schedulerLock) {
            activeModules++;

            // Initialize scheduler if needed
            if (globalScheduler == null || globalScheduler.isShutdown()) {
                globalScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "ClearCrackedAccounts-Timer");
                    thread.setDaemon(true);
                    return thread;
                });
            }

            // Start timer only if:
            // 1. No active timer exists, OR
            // 2. The interval has been changed
            if (currentTask == null || currentTask.isDone() || lastInterval != clearInterval.get()) {
                startTimer();
            }
        }
    }

    @Override
    public void onDeactivate() {
        synchronized (schedulerLock) {
            activeModules--;

            // Stop timer only when no modules are active anymore
            if (activeModules <= 0) {
                stopTimer();
                activeModules = 0; // Reset for safety
            }
        }
    }

    private void startTimer() {
        // Stop existing timer
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(false);
        }

        lastInterval = clearInterval.get();

        // Start new timer with fixed interval
        currentTask = globalScheduler.scheduleAtFixedRate(
            this::clearCrackedAccounts,
            clearInterval.get(), // First execution after full interval
            clearInterval.get(),
            TimeUnit.MINUTES
        );
    }

    private void stopTimer() {
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(false);
            currentTask = null;
        }
        lastInterval = -1;
    }

    private void clearCrackedAccounts() {
        // Only execute if at least one module is active
        if (activeModules <= 0) {
            return;
        }

        try {
            List<Account> accountsToRemove = new ArrayList<>();

            // Collect all cracked accounts
            for (Account account : Accounts.get()) {
                if (account.getType() == AccountType.Cracked) {
                    accountsToRemove.add(account);
                }
            }

            // Remove cracked accounts
            if (!accountsToRemove.isEmpty()) {
                for (Account account : accountsToRemove) {
                    Accounts.get().remove(account);
                }

                Accounts.get().save();

                // Optional: Debug message
                // info("Cleared " + accountsToRemove.size() + " cracked accounts");
            }

        } catch (Exception e) {
            // Silent error handling
            e.printStackTrace();
        }
    }

    // Method to update interval at runtime
    public void updateInterval() {
        if (isActive()) {
            synchronized (schedulerLock) {
                startTimer();
            }
        }
    }

    // Global shutdown for addon deactivation
    public static void shutdownGlobalScheduler() {
        synchronized (schedulerLock) {
            activeModules = 0;

            if (currentTask != null && !currentTask.isDone()) {
                currentTask.cancel(false);
                currentTask = null;
            }

            if (globalScheduler != null && !globalScheduler.isShutdown()) {
                globalScheduler.shutdown();
                try {
                    if (!globalScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                        globalScheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    globalScheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                globalScheduler = null;
            }

            lastInterval = -1;
        }
    }
}
