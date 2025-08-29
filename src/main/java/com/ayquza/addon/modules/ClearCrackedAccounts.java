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

    // Static scheduler shared across all instances to persist across server switches
    private static ScheduledExecutorService globalScheduler;
    private static ScheduledFuture<?> currentTask;
    private static long taskStartTime;
    private static int currentInterval;

    // Track if this module instance is active
    private boolean isModuleActive = false;

    public ClearCrackedAccounts() {
        super(AyquzaAddon.CATEGORY, "clear-cracked-accounts", "Clears all cracked accounts from the Meteor account manager.");
    }

    @Override
    public void onActivate() {
        isModuleActive = true;

        // Initialize global scheduler if it doesn't exist
        if (globalScheduler == null || globalScheduler.isShutdown()) {
            globalScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "ClearCrackedAccounts-GlobalTimer");
                thread.setDaemon(true);
                return thread;
            });
        }

        // Only start a new timer if there's no active task or if the interval has changed
        if (currentTask == null || currentTask.isDone() || currentInterval != clearInterval.get()) {
            startNewTimer();
        }
    }

    @Override
    public void onDeactivate() {
        isModuleActive = false;

        // Cancel the current task but keep the scheduler running for potential reactivation
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(false);
            currentTask = null;
        }

        // Only shutdown the global scheduler if no other instance needs it
        // In practice, you might want to keep it running until Minecraft shuts down
    }

    private void startNewTimer() {
        // Cancel existing task if running
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(false);
        }

        // Calculate remaining time for first execution if continuing an existing timer
        long initialDelay;
        long currentTime = System.currentTimeMillis();

        if (taskStartTime > 0 && currentInterval == clearInterval.get()) {
            // Calculate how much time has passed since the timer was supposed to start
            long elapsedMinutes = (currentTime - taskStartTime) / (60 * 1000);
            long remainingMinutes = clearInterval.get() - (elapsedMinutes % clearInterval.get());

            // If less than 30 seconds remaining, execute immediately and start new cycle
            initialDelay = remainingMinutes <= 0 ? 0 : remainingMinutes;
        } else {
            // Fresh start
            initialDelay = clearInterval.get();
            taskStartTime = currentTime;
        }

        currentInterval = clearInterval.get();

        // Schedule the recurring task
        currentTask = globalScheduler.scheduleAtFixedRate(
            this::clearCrackedAccounts,
            initialDelay,
            clearInterval.get(),
            TimeUnit.MINUTES
        );
    }

    private void clearCrackedAccounts() {
        // Only execute if the module is currently active
        if (!isModuleActive) {
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

                // Save changes
                Accounts.get().save();

                // Optional: Debug message (for testing only)
                // info("Cleared " + accountsToRemove.size() + " cracked accounts");
            }

        } catch (Exception e) {
            // Silent error handling - no chat messages
            e.printStackTrace(); // For debugging purposes
        }
    }

    // Method to update interval at runtime
    public void updateInterval() {
        if (isActive()) {
            startNewTimer(); // Restart with new interval
        }
    }

    // Static method to properly shutdown the global scheduler when addon is disabled
    public static void shutdownGlobalScheduler() {
        if (globalScheduler != null && !globalScheduler.isShutdown()) {
            if (currentTask != null && !currentTask.isDone()) {
                currentTask.cancel(false);
            }

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
            currentTask = null;
            taskStartTime = 0;
            currentInterval = 0;
        }
    }
}
