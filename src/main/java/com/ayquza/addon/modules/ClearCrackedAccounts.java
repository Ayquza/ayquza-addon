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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ClearCrackedAccounts extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> clearInterval = sgGeneral.add(new IntSetting.Builder()
        .name("clear-interval-in-min")
        .description("Interval in minutes to clear cracked accounts.")
        .defaultValue(5)
        .min(1)
        .max(60)
        .sliderMax(30)
        .onChanged(value -> updateServiceSettings())
        .build()
    );

    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Enable debug output to console (10 second intervals)")
        .defaultValue(false)
        .onChanged(value -> updateServiceSettings())
        .build()
    );

    private final Setting<Boolean> enableService = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-service")
        .description("Enable the background service (works in all game states)")
        .defaultValue(true)
        .onChanged(value -> updateServiceSettings())
        .build()
    );

    private static volatile ScheduledExecutorService backgroundService;
    private static volatile ScheduledFuture<?> clearTask;
    private static volatile ScheduledFuture<?> debugTask;

    private static final AtomicBoolean serviceEnabled = new AtomicBoolean(false);
    private static final AtomicBoolean debugEnabled = new AtomicBoolean(false);
    private static final AtomicInteger intervalMinutes = new AtomicInteger(5);
    private static final AtomicLong lastClearTime = new AtomicLong(0);
    private static final AtomicLong debugCounter = new AtomicLong(0);
    private static final AtomicLong serviceStartTime = new AtomicLong(0);

    private static volatile ClearCrackedAccounts moduleInstance;
    private static final AtomicBoolean staticInitialized = new AtomicBoolean(false);

    static {
        serviceStartTime.set(System.currentTimeMillis());
        initializeStaticService();
    }

    public ClearCrackedAccounts() {
        super(AyquzaAddon.CATEGORY, "clear-cracked-accounts", "Clears all cracked accounts from the Meteor account manager.");
        moduleInstance = this;
        startBackgroundService();
    }

    private static void initializeStaticService() {
        if (staticInitialized.getAndSet(true)) return;

        try {
            backgroundService = Executors.newScheduledThreadPool(3, r -> {
                Thread thread = new Thread(r, "ClearCrackedAccounts-Static");
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            });

            Runtime.getRuntime().addShutdownHook(new Thread(ClearCrackedAccounts::stopStaticService));

            serviceEnabled.set(true);
            intervalMinutes.set(5);
            startServiceTasks();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void startServiceTasks() {
        if (backgroundService == null || backgroundService.isShutdown()) return;

        if (clearTask != null && !clearTask.isDone()) clearTask.cancel(false);
        if (debugTask != null && !debugTask.isDone()) debugTask.cancel(false);

        try {
            clearTask = backgroundService.scheduleAtFixedRate(ClearCrackedAccounts::executeStaticClearTask, intervalMinutes.get(), intervalMinutes.get(), TimeUnit.MINUTES);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void executeStaticClearTask() {
        if (!serviceEnabled.get()) return;

        try {
            List<Account> accountsToRemove = new ArrayList<>();
            if (Accounts.get() != null) {
                for (Account account : Accounts.get()) {
                    if (account != null && account.getType() == AccountType.Cracked) {
                        accountsToRemove.add(account);
                    }
                }
            }

            if (!accountsToRemove.isEmpty()) {
                for (Account account : accountsToRemove) {
                    try {
                        Accounts.get().remove(account);
                    } catch (Exception ignored) {}
                }

                try {
                    Accounts.get().save();
                } catch (Exception ignored) {}
            }

            lastClearTime.set(System.currentTimeMillis());

        } catch (Exception ignored) {}
    }

    private static String getStaticGameState() {
        try {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null) return "NO_CLIENT";
            if (client.world == null) {
                if (client.currentScreen != null) {
                    String screenName = client.currentScreen.getClass().getSimpleName();
                    if (screenName.contains("TitleScreen")) return "MAIN_MENU";
                    if (screenName.contains("MultiplayerScreen")) return "SERVER_LIST";
                    return "MENU_" + screenName.replace("Screen", "");
                }
                return "LOADING";
            } else {
                if (client.getServer() != null) return "SINGLEPLAYER";
                return "MULTIPLAYER";
            }
        } catch (Exception e) {
            return "ERROR_" + e.getClass().getSimpleName();
        }
    }

    private static int getStaticCrackedAccountCount() {
        try {
            if (Accounts.get() == null) return -1;
            int count = 0;
            for (Account account : Accounts.get()) {
                if (account != null && account.getType() == AccountType.Cracked) count++;
            }
            return count;
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public void onActivate() { updateServiceSettings(); }

    @Override
    public void onDeactivate() { updateServiceSettings(); }

    public void startBackgroundService() {
        boolean newServiceEnabled = enableService.get();
        boolean newDebugEnabled = debugMode.get();
        int newInterval = clearInterval.get();

        serviceEnabled.set(newServiceEnabled);
        debugEnabled.set(newDebugEnabled);
        intervalMinutes.set(newInterval);

        if (serviceEnabled.get() && staticInitialized.get()) startServiceTasks();
    }

    public void stopBackgroundService() {
        serviceEnabled.set(false);
        debugEnabled.set(false);
    }

    public void updateServiceSettings() {
        if (moduleInstance == null) return;

        boolean newServiceEnabled = moduleInstance.enableService.get();
        boolean newDebugEnabled = moduleInstance.debugMode.get();
        int newInterval = moduleInstance.clearInterval.get();

        boolean anyChange = newInterval != intervalMinutes.get() || newServiceEnabled != serviceEnabled.get() || newDebugEnabled != debugEnabled.get();

        serviceEnabled.set(newServiceEnabled);
        debugEnabled.set(newDebugEnabled);
        intervalMinutes.set(newInterval);

        if (anyChange && serviceEnabled.get()) startServiceTasks();
    }

    public void clearNow() {
        if (backgroundService != null && !backgroundService.isShutdown()) backgroundService.execute(ClearCrackedAccounts::executeStaticClearTask);
    }

    public boolean isServiceRunning() {
        return staticInitialized.get() && serviceEnabled.get() && backgroundService != null && !backgroundService.isShutdown();
    }

    public static void stopStaticService() {
        serviceEnabled.set(false);
        debugEnabled.set(false);

        if (clearTask != null && !clearTask.isDone()) clearTask.cancel(true);
        if (debugTask != null && !debugTask.isDone()) debugTask.cancel(true);

        if (backgroundService != null && !backgroundService.isShutdown()) {
            backgroundService.shutdown();
            try {
                if (!backgroundService.awaitTermination(3, TimeUnit.SECONDS)) backgroundService.shutdownNow();
            } catch (InterruptedException e) {
                backgroundService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            backgroundService = null;
        }

        staticInitialized.set(false);
        moduleInstance = null;
    }

    public static void shutdownGlobalScheduler() {
        stopStaticService();
    }
}
