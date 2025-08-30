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

    // Global scheduler and timer - runs independently of module state
    private static ScheduledExecutorService globalScheduler;
    private static ScheduledFuture<?> currentTask;
    private static int lastInterval = -1;
    private static boolean timerEnabled = false;

    // Synchronization object
    private static final Object schedulerLock = new Object();

    // Reference to the module instance for settings access
    private static ClearCrackedAccounts moduleInstance;

    public ClearCrackedAccounts() {
        super(AyquzaAddon.CATEGORY, "clear-cracked-accounts", "Clears all cracked accounts from the Meteor account manager.");
        moduleInstance = this;
    }

    @Override
    public void onActivate() {
        synchronized (schedulerLock) {
            timerEnabled = true;
            startTimer();
        }
    }

    @Override
    public void onDeactivate() {
        synchronized (schedulerLock) {
            timerEnabled = false;
            stopTimer();
        }
    }

    private void startTimer() {
        // Initialize scheduler if needed
        if (globalScheduler == null || globalScheduler.isShutdown()) {
            globalScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "ClearCrackedAccounts-GlobalTimer");
                thread.setDaemon(true);
                return thread;
            });
        }

        // Stop existing timer
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(false);
        }

        int currentInterval = clearInterval.get();
        lastInterval = currentInterval;

        // Start new timer - runs regardless of game state (main menu, server, singleplayer)
        currentTask = globalScheduler.scheduleAtFixedRate(
            this::clearCrackedAccountsTask,
            currentInterval, // First execution after full interval
            currentInterval,
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

    // Method to update interval at runtime (call this manually when settings change)
    public void updateInterval() {
        synchronized (schedulerLock) {
            if (timerEnabled && (lastInterval != clearInterval.get())) {
                startTimer();
            }
        }
    }

    private void clearCrackedAccountsTask() {
        // Only execute if timer is enabled (module is active)
        if (!timerEnabled) {
            return;
        }

        // Check if interval has changed and restart timer if needed
        if (lastInterval != clearInterval.get()) {
            synchronized (schedulerLock) {
                startTimer();
                return; // Skip this execution, new timer will handle it
            }
        }

        try {
            List<Account> accountsToRemove = new ArrayList<>();

            // Collect all cracked accounts
            // This works in main menu, server, and singleplayer
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

                // Optional: Debug message (only if module instance exists and is active)
                if (moduleInstance != null && moduleInstance.isActive()) {
                    // moduleInstance.info("Cleared " + accountsToRemove.size() + " cracked accounts");
                }
            }

        } catch (Exception e) {
            // Silent error handling
            e.printStackTrace();
        }
    }

    // Public method to manually trigger clearing
    public void clearNow() {
        clearCrackedAccountsTask();
    }

    // Check if timer is currently running
    public boolean isTimerRunning() {
        synchronized (schedulerLock) {
            return timerEnabled && currentTask != null && !currentTask.isDone();
        }
    }

    // Get time until next execution (in minutes)
    public long getTimeUntilNextExecution() {
        synchronized (schedulerLock) {
            if (currentTask != null && !currentTask.isDone()) {
                return currentTask.getDelay(TimeUnit.MINUTES);
            }
            return -1;
        }
    }

    // Global shutdown for addon deactivation
    public static void shutdownGlobalScheduler() {
        synchronized (schedulerLock) {
            timerEnabled = false;

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
            moduleInstance = null;
        }
    }
}
