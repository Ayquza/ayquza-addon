package com.ayquza.addon.modules;

import com.ayquza.addon.AyquzaAddon;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.systems.accounts.AccountType;
import meteordevelopment.meteorclient.systems.accounts.Accounts;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ClearCrackedAccounts extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();



    private final Setting<Integer> clearInterval = sgGeneral.add(new IntSetting.Builder()
        .name("clear-interval-minutes")
        .description("Interval in minutes to clear cracked accounts.")
        .defaultValue(5)
        .min(1)
        .max(60)
        .sliderMax(30)
        .onChanged(value -> restartClearTask())
        .build()
    );

    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Enable debug output to console every 10 seconds.")
        .defaultValue(false)
        .onChanged(value -> restartDebugTask())
        .build()
    );

    private static ScheduledExecutorService executor;
    private static ScheduledFuture<?> clearTask;
    private static ScheduledFuture<?> debugTask;
    private static boolean serviceStarted = false;

    public ClearCrackedAccounts() {
        super(AyquzaAddon.CATEGORY, "clear-cracked-accounts", "Clears cracked accounts periodically. Timer starts on first server join, then keeps running in background.");
    }

    @Override
    public void onActivate() {
        if (serviceStarted) return;

        executor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ClearCrackedAccounts");
            t.setDaemon(true);
            return t;
        });

        serviceStarted = true;

        restartClearTask();
        restartDebugTask();

        System.out.println("[ClearCrackedAccounts] Service started. Interval: " + clearInterval.get() + " min.");
    }

    @Override
    public void onDeactivate() {
        // Service intentionally kept alive — only stopped via stopService()
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (serviceStarted && (executor == null || executor.isShutdown())) {
            serviceStarted = false;
            onActivate();
        }
    }

    private void restartClearTask() {
        if (executor == null || executor.isShutdown()) return;

        cancelTask(clearTask);

        int interval = clearInterval.get();
        clearTask = executor.scheduleAtFixedRate(this::executeClear, interval, interval, TimeUnit.MINUTES);

        System.out.println("[ClearCrackedAccounts] Clear task restarted. Interval: " + interval + " min.");
    }

    private void restartDebugTask() {
        if (executor == null || executor.isShutdown()) return;

        cancelTask(debugTask);

        if (debugMode.get()) {
            debugTask = executor.scheduleAtFixedRate(this::printDebug, 10, 10, TimeUnit.SECONDS);
            System.out.println("[ClearCrackedAccounts] Debug task started.");
        }
    }

    private void cancelTask(ScheduledFuture<?> task) {
        if (task != null && !task.isDone()) task.cancel(false);
    }

    private void executeClear() {
        try {
            Accounts accounts = Accounts.get();
            if (accounts == null) return;

            List<Account> toRemove = new ArrayList<>();

            synchronized (accounts) {
                for (Account account : accounts) {
                    if (account != null && account.getType() == AccountType.Cracked) {
                        toRemove.add(account);
                    }
                }

                if (!toRemove.isEmpty()) {
                    for (Account account : toRemove) {
                        accounts.remove(account);
                    }
                    accounts.save();
                }
            }

            if (!toRemove.isEmpty()) {
                System.out.println("[ClearCrackedAccounts] Removed " + toRemove.size() + " cracked account(s).");
            }

        } catch (Exception e) {
            System.err.println("[ClearCrackedAccounts] Error during clear: " + e.getMessage());
        }
    }

    private void printDebug() {
        if (!debugMode.get()) return;

        System.out.println("[ClearCrackedAccounts] [DEBUG]"
            + " Cracked accounts: " + getCrackedAccountCount()
            + " | Interval: " + clearInterval.get() + " min"
            + " | Next clear in: " + (clearTask != null ? clearTask.getDelay(TimeUnit.SECONDS) : -1) + "s"
            + " | Service alive: " + serviceStarted);
    }

    private int getCrackedAccountCount() {
        try {
            Accounts accounts = Accounts.get();
            if (accounts == null) return -1;
            int count = 0;
            for (Account account : accounts) {
                if (account != null && account.getType() == AccountType.Cracked) count++;
            }
            return count;
        } catch (Exception e) {
            return -1;
        }
    }

    public static void stopService() {
        cancelStaticTask(clearTask);
        cancelStaticTask(debugTask);
        clearTask = null;
        debugTask = null;

        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) executor.shutdownNow();
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        serviceStarted = false;
        System.out.println("[ClearCrackedAccounts] Service fully stopped.");
    }

    private static void cancelStaticTask(ScheduledFuture<?> task) {
        if (task != null && !task.isDone()) task.cancel(false);
    }
}
