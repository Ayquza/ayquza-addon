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
        .onChanged(value -> {
            debug("Clear interval changed to: " + value + " minutes");
            updateServiceSettings();
        })
        .build()
    );

    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Enable debug output to console (10 second intervals)")
        .defaultValue(false)
        .onChanged(value -> {
            debug("Debug mode changed to: " + value);
            updateServiceSettings();
        })
        .build()
    );

    private final Setting<Boolean> enableService = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-service")
        .description("Enable the background service (works in all game states)")
        .defaultValue(true)
        .onChanged(value -> {
            debug("Enable service changed to: " + value);
            updateServiceSettings();
        })
        .build()
    );

    // COMPLETELY INDEPENDENT STATIC SYSTEM
    private static volatile ScheduledExecutorService backgroundService;
    private static volatile ScheduledFuture<?> clearTask;
    private static volatile ScheduledFuture<?> debugTask;

    // Atomic variables for thread-safe communication
    private static final AtomicBoolean serviceEnabled = new AtomicBoolean(false);
    private static final AtomicBoolean debugEnabled = new AtomicBoolean(false);
    private static final AtomicInteger intervalMinutes = new AtomicInteger(5);
    private static final AtomicLong lastClearTime = new AtomicLong(0);
    private static final AtomicLong debugCounter = new AtomicLong(0);
    private static final AtomicLong serviceStartTime = new AtomicLong(0);

    private static volatile ClearCrackedAccounts moduleInstance;
    private static final AtomicBoolean staticInitialized = new AtomicBoolean(false);

    // STATIC INITIALIZATION - RUNS IMMEDIATELY WHEN CLASS LOADS
    static {
        serviceStartTime.set(System.currentTimeMillis());
        initializeStaticService();
    }

    public ClearCrackedAccounts() {
        super(AyquzaAddon.CATEGORY, "clear-cracked-accounts", "Clears all cracked accounts from the Meteor account manager.");

        moduleInstance = this;
        debug("Module instance created");

        // Immediately start the service when module is created (not just when activated)
        startBackgroundService();
    }

    // Debug output method - only prints when debug is enabled
    private static void debug(String message) {
        if (debugEnabled.get()) {
            System.out.println("[ClearCrackedAccounts] " + message);
        }
    }

    // Info output method - always prints important info
    private static void info(String message) {
        System.out.println("[ClearCrackedAccounts] " + message);
    }

    // Error output method - always prints errors
    private static void error(String message) {
        System.out.println("[ClearCrackedAccounts] ERROR: " + message);
    }

    // STATIC SERVICE INITIALIZATION
    private static void initializeStaticService() {
        if (staticInitialized.getAndSet(true)) {
            return; // Already initialized
        }

        try {
            backgroundService = Executors.newScheduledThreadPool(3, r -> {
                Thread thread = new Thread(r, "ClearCrackedAccounts-Static");
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY);
                thread.setUncaughtExceptionHandler((t, e) -> {
                    error("THREAD ERROR in " + t.getName() + ": " + e.getMessage());
                    if (debugEnabled.get()) e.printStackTrace();
                });
                return thread;
            });

            debug("Background service pool created with 3 threads");

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                debug("JVM shutdown - stopping static service");
                stopStaticService();
            }));

            // Start the service immediately - don't wait for module activation
            serviceEnabled.set(true);
            intervalMinutes.set(5); // Set to 5 minutes by default until module settings are read
            startServiceTasks();

        } catch (Exception e) {
            error("CRITICAL: Failed to initialize static service: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // START SERVICE TASKS
    private static void startServiceTasks() {
        if (backgroundService == null || backgroundService.isShutdown()) {
            error("Background service not available or shutdown");
            return;
        }

        debug("Starting service tasks...");
        debug("Service enabled: " + serviceEnabled.get());
        debug("Debug enabled: " + debugEnabled.get());
        debug("Interval: " + intervalMinutes.get() + " minutes");

        // Stop existing tasks
        if (clearTask != null && !clearTask.isDone()) {
            debug("Cancelling existing clear task");
            clearTask.cancel(false);
        }
        if (debugTask != null && !debugTask.isDone()) {
            debug("Cancelling existing debug task");
            debugTask.cancel(false);
        }

        // Start clear task
        try {
            clearTask = backgroundService.scheduleAtFixedRate(() -> {
                try {
                    if (serviceEnabled.get()) {
                        debug("TIMER TRIGGERED - executing clear task");
                        executeStaticClearTask();
                    }
                } catch (Exception e) {
                    error("ERROR in clear task: " + e.getMessage());
                    if (debugEnabled.get()) e.printStackTrace();
                }
            }, intervalMinutes.get(), intervalMinutes.get(), TimeUnit.MINUTES);
            debug("Clear task scheduled successfully");
        } catch (Exception e) {
            error("ERROR scheduling clear task: " + e.getMessage());
            e.printStackTrace();
        }

        // Start debug task - only if debug is enabled
        if (debugEnabled.get()) {
            try {
                debugTask = backgroundService.scheduleAtFixedRate(() -> {
                    try {
                        executeStaticDebugTask();
                    } catch (Exception e) {
                        error("ERROR in debug task: " + e.getMessage());
                        e.printStackTrace();
                    }
                }, 5, 10, TimeUnit.SECONDS); // Start after 5 seconds, then every 10 seconds
                debug("Debug task scheduled successfully (every 10 seconds)");
            } catch (Exception e) {
                error("ERROR scheduling debug task: " + e.getMessage());
                e.printStackTrace();
            }
        }

        debug("Service tasks started - clear interval: " + intervalMinutes.get() + " minutes");

        // Run immediate test clear only in debug mode
        if (debugEnabled.get()) {
            try {
                backgroundService.schedule(() -> {
                    debug("Running immediate test clear...");
                    executeStaticClearTask();
                }, 3, TimeUnit.SECONDS);
                debug("Immediate test clear scheduled for 3 seconds");
            } catch (Exception e) {
                error("ERROR scheduling immediate test: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // STATIC CLEAR TASK EXECUTION
    private static void executeStaticClearTask() {
        if (!serviceEnabled.get()) {
            debug("Clear task called but service disabled");
            return;
        }

        try {
            String gameState = getStaticGameState();
            debug("=== EXECUTING CLEAR TASK ===");
            debug("Game State: " + gameState);
            debug("Thread: " + Thread.currentThread().getName());

            List<Account> accountsToRemove = new ArrayList<>();

            // Access Meteor's account system directly
            try {
                if (Accounts.get() != null) {
                    debug("Account manager accessible");

                    for (Account account : Accounts.get()) {
                        if (account != null && account.getType() == AccountType.Cracked) {
                            accountsToRemove.add(account);
                            debug("Found cracked account: " + account.getUsername());
                        }
                    }
                } else {
                    debug("Account manager is null in state: " + gameState);
                    return;
                }
            } catch (Exception e) {
                error("Failed to access accounts: " + e.getMessage());
                if (debugEnabled.get()) e.printStackTrace();
                return;
            }

            // Remove accounts
            if (!accountsToRemove.isEmpty()) {
                info("Removing " + accountsToRemove.size() + " cracked accounts...");

                for (Account account : accountsToRemove) {
                    try {
                        Accounts.get().remove(account);
                        debug("Removed: " + account.getUsername());
                    } catch (Exception e) {
                        error("Failed to remove " + account.getUsername() + ": " + e.getMessage());
                    }
                }

                // Save accounts
                try {
                    Accounts.get().save();
                    info("Successfully cleared " + accountsToRemove.size() + " cracked accounts");
                } catch (Exception e) {
                    error("Failed to save accounts: " + e.getMessage());
                }
            } else {
                debug("No cracked accounts found in " + gameState);
            }

            lastClearTime.set(System.currentTimeMillis());
            debug("=== CLEAR TASK COMPLETE ===");

        } catch (Exception e) {
            error("CRITICAL ERROR in static clear task: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // STATIC DEBUG TASK
    private static void executeStaticDebugTask() {
        if (!debugEnabled.get()) return;

        try {
            long counter = debugCounter.incrementAndGet();
            long currentTime = System.currentTimeMillis();
            long timeSinceLastClear = currentTime - lastClearTime.get();
            long intervalMs = intervalMinutes.get() * 60 * 1000L;
            long remainingMs = Math.max(0, intervalMs - timeSinceLastClear);

            int remainingMinutes = (int) (remainingMs / 60000);
            int remainingSeconds = (int) ((remainingMs % 60000) / 1000);

            String serviceStatus = serviceEnabled.get() ? "ENABLED" : "DISABLED";
            String debugStatus = debugEnabled.get() ? "ENABLED" : "DISABLED";
            String gameState = getStaticGameState();
            int crackedCount = getStaticCrackedAccountCount();

            // Calculate uptime
            long uptimeMs = currentTime - serviceStartTime.get();
            long uptimeMinutes = uptimeMs / 60000;
            long uptimeSeconds = (uptimeMs % 60000) / 1000;

            // Thread info
            String threadName = Thread.currentThread().getName();

            // Service health check
            boolean clearTaskAlive = clearTask != null && !clearTask.isDone() && !clearTask.isCancelled();
            boolean debugTaskAlive = debugTask != null && !debugTask.isDone() && !debugTask.isCancelled();
            boolean serviceHealthy = backgroundService != null && !backgroundService.isShutdown();

            System.out.println("=== CLEAR CRACKED ACCOUNTS DEBUG #" + counter + " ===");
            System.out.println("Service Status: " + serviceStatus + " | Debug: " + debugStatus);
            System.out.println("Game State: " + gameState + " | Thread: " + threadName);
            System.out.println("Uptime: " + uptimeMinutes + "m " + uptimeSeconds + "s");
            System.out.println("Next Clear: " + remainingMinutes + "m " + remainingSeconds + "s (interval: " + intervalMinutes.get() + "m)");
            System.out.println("Cracked Accounts: " + crackedCount);
            System.out.println("Tasks Health: Clear=" + clearTaskAlive + ", Debug=" + debugTaskAlive + ", Service=" + serviceHealthy);

            if (lastClearTime.get() > 0) {
                System.out.println("Last Clear: " + (timeSinceLastClear / 1000) + "s ago");
            } else {
                System.out.println("Last Clear: Never");
            }
            System.out.println("============================================");

        } catch (Exception e) {
            error("DEBUG ERROR: " + e.getMessage());
            if (debugEnabled.get()) e.printStackTrace();
        }
    }

    // STATIC UTILITY METHODS
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
                if (account != null && account.getType() == AccountType.Cracked) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            return -1;
        }
    }

    // MODULE METHODS (these are called by Meteor)
    @Override
    public void onActivate() {
        debug("Module activated - updating service settings");
        updateServiceSettings();
    }

    @Override
    public void onDeactivate() {
        debug("Module deactivated - service continues running");
        // Don't stop the service, just update settings
        updateServiceSettings();
    }

    // Start/stop background service based on module instance
    public void startBackgroundService() {
        debug("Starting background service from module");

        // Read current settings
        boolean newServiceEnabled = enableService.get();
        boolean newDebugEnabled = debugMode.get();
        int newInterval = clearInterval.get();

        debug("Current module settings:");
        debug("  Enable Service: " + newServiceEnabled);
        debug("  Debug Mode: " + newDebugEnabled);
        debug("  Clear Interval: " + newInterval + " minutes");

        // Update atomic variables
        serviceEnabled.set(newServiceEnabled);
        debugEnabled.set(newDebugEnabled);
        intervalMinutes.set(newInterval);

        debug("Updated service settings: enabled=" + serviceEnabled.get() +
            ", debug=" + debugEnabled.get() + ", interval=" + intervalMinutes.get() + " minutes");

        if (serviceEnabled.get() && staticInitialized.get()) {
            debug("Starting service tasks with " + newInterval + " minute interval");
            startServiceTasks();
        } else {
            debug("Not starting tasks: serviceEnabled=" + serviceEnabled.get() +
                ", staticInitialized=" + staticInitialized.get());
        }
    }

    public void stopBackgroundService() {
        debug("Stopping background service from module");
        serviceEnabled.set(false);
        debugEnabled.set(false);
    }

    // Update service settings
    public void updateServiceSettings() {
        if (moduleInstance != null) {
            boolean newServiceEnabled = moduleInstance.enableService.get();
            boolean newDebugEnabled = moduleInstance.debugMode.get();
            int newInterval = moduleInstance.clearInterval.get();

            debug("*** UPDATING SERVICE SETTINGS ***");
            debug("  Module Active: " + moduleInstance.isActive());
            debug("  Module Settings - Service: " + newServiceEnabled + ", Debug: " + newDebugEnabled + ", Interval: " + newInterval + "m");
            debug("  Current Atomic - Service: " + serviceEnabled.get() + ", Debug: " + debugEnabled.get() + ", Interval: " + intervalMinutes.get() + "m");

            boolean intervalChanged = newInterval != intervalMinutes.get();
            boolean anyChange = intervalChanged ||
                newServiceEnabled != serviceEnabled.get() ||
                newDebugEnabled != debugEnabled.get();

            // Update atomic variables
            serviceEnabled.set(newServiceEnabled);
            debugEnabled.set(newDebugEnabled);
            intervalMinutes.set(newInterval);

            if (anyChange) {
                debug("Settings changed - restarting service tasks");
                debug("  New Atomic Values - Service: " + serviceEnabled.get() + ", Debug: " + debugEnabled.get() + ", Interval: " + intervalMinutes.get() + "m");

                if (serviceEnabled.get()) {
                    startServiceTasks();
                } else {
                    debug("Service disabled - stopping tasks");
                }
            } else {
                debug("No settings changed");
            }

            debug("*** SERVICE SETTINGS UPDATE COMPLETE ***");
        } else {
            error("Cannot update settings - moduleInstance is null");
        }
    }

    // Manual clear trigger
    public void clearNow() {
        info("Manual clear requested");
        if (backgroundService != null && !backgroundService.isShutdown()) {
            backgroundService.execute(() -> executeStaticClearTask());
        } else {
            error("Cannot execute manual clear - service not available");
        }
    }

    // Service status
    public boolean isServiceRunning() {
        boolean running = staticInitialized.get() && serviceEnabled.get() &&
            backgroundService != null && !backgroundService.isShutdown();
        debug("Service running check: " + running);
        return running;
    }

    // STATIC SERVICE SHUTDOWN
    public static void stopStaticService() {
        debug("Stopping static service...");

        serviceEnabled.set(false);
        debugEnabled.set(false);

        if (clearTask != null && !clearTask.isDone()) {
            clearTask.cancel(true);
            clearTask = null;
        }

        if (debugTask != null && !debugTask.isDone()) {
            debugTask.cancel(true);
            debugTask = null;
        }

        if (backgroundService != null && !backgroundService.isShutdown()) {
            backgroundService.shutdown();
            try {
                if (!backgroundService.awaitTermination(3, TimeUnit.SECONDS)) {
                    backgroundService.shutdownNow();
                }
            } catch (InterruptedException e) {
                backgroundService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            backgroundService = null;
        }

        staticInitialized.set(false);
        moduleInstance = null;

        debug("Static service stopped");
    }

    // Legacy compatibility
    public static void shutdownGlobalScheduler() {
        stopStaticService();
    }
}
