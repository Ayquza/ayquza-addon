package com.ayquza.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.systems.accounts.Accounts;
import meteordevelopment.meteorclient.systems.accounts.types.CrackedAccount;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;



public class SimpleLoginCommand extends Command {
    public SimpleLoginCommand() {
        super("login", "Re-logins you as a different cracked user / manage cracked accounts", "l");
    }

    private boolean validateUsername(String username) {
        if (username.isEmpty()) {
            error("Username cannot be empty.");
            return false;
        }

        if (username.length() > 17) {
            error("Username cannot be longer than 17 characters.");
            return false;
        }

        return true;
    }

    private void addAccount(CrackedAccount crackedAccount) {
        if (crackedAccount.fetchInfo()) {
            crackedAccount.getCache().loadHead();

            if (!Accounts.get().exists(crackedAccount)) {
                info("Added new cracked account: " + crackedAccount.getUsername());
                Accounts.get().add(crackedAccount);
            }

            if (crackedAccount.login()) {
                Accounts.get().save();
                info("Logged in as: " + crackedAccount.getUsername());
            } else {
                error("Failed to log in as: " + crackedAccount.getUsername());
            }
        } else {
            error("Failed to fetch info for: " + crackedAccount.getUsername());
        }
    }

    private void reconnect(ServerInfo serverInfo) {
        if (serverInfo != null) {
            mc.execute(() -> {
                info("Disconnecting...");

                if (mc.world != null) mc.world.disconnect(Text.of("Reconnecting"));

                if (mc.getCurrentServerEntry() == null)
                    mc.disconnectWithSavingScreen();
                else
                    mc.disconnectWithProgressScreen();

                mc.setScreen(new MultiplayerScreen(new TitleScreen()));

                info("Reconnecting to server: " + serverInfo.address);
                ConnectScreen.connect(
                    new MultiplayerScreen(new TitleScreen()),
                    mc,
                    ServerAddress.parse(serverInfo.address),
                    serverInfo,
                    false,
                    null
                );
            });
        }
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("username", StringArgumentType.word())
            .executes(context -> {
                String username = StringArgumentType.getString(context, "username");

                if (!validateUsername(username)) return SINGLE_SUCCESS;

                CrackedAccount crackedAccount = new CrackedAccount(username);
                ServerInfo currentServer = mc.getCurrentServerEntry();

                MeteorExecutor.execute(() -> {
                    addAccount(crackedAccount);
                    reconnect(currentServer);
                });

                return SINGLE_SUCCESS;
            }));

        builder.then(literal("list").executes(context -> {
            info("Cracked Accounts:");
            int count = 0;
            for (Account<?> acc : Accounts.get()) {
                if (acc instanceof CrackedAccount) {
                    CrackedAccount ca = (CrackedAccount) acc;
                    info("- " + ca.getUsername());
                    count++;
                }
            }
            if (count == 0) info("No cracked accounts saved.");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("remove").then(argument("username", StringArgumentType.word())
            .executes(context -> {
                String username = StringArgumentType.getString(context, "username");

                List<Account<?>> copy = new ArrayList<>();
                for (Account<?> a : Accounts.get()) copy.add(a);

                for (Account<?> acc : copy) {
                    if (acc instanceof CrackedAccount) {
                        CrackedAccount ca = (CrackedAccount) acc;
                        if (ca.getUsername().equalsIgnoreCase(username)) {
                            Accounts.get().remove(acc);
                            Accounts.get().save();
                            info("Removed cracked account: " + username);
                            return SINGLE_SUCCESS;
                        }
                    }
                }

                error("Cracked account not found: " + username);
                return SINGLE_SUCCESS;
            })));

        builder.then(literal("set").then(argument("username", StringArgumentType.word())
            .executes(context -> {
                String username = StringArgumentType.getString(context, "username");
                ServerInfo currentServer = mc.getCurrentServerEntry();

                CrackedAccount found = null;
                for (Account<?> acc : Accounts.get()) {
                    if (acc instanceof CrackedAccount) {
                        CrackedAccount ca = (CrackedAccount) acc;
                        if (ca.getUsername().equalsIgnoreCase(username)) {
                            found = ca;
                            break;
                        }
                    }
                }

                if (found == null) {
                    error("Cracked account not found: " + username);
                    return SINGLE_SUCCESS;
                }

                CrackedAccount toLogin = found;
                MeteorExecutor.execute(() -> {
                    if (toLogin.login()) {
                        Accounts.get().save();
                        info("Logged in as: " + toLogin.getUsername());
                        reconnect(currentServer);
                    } else {
                        error("Failed to login as: " + toLogin.getUsername());
                    }
                });

                return SINGLE_SUCCESS;
            })));

        builder.then(literal("clear").executes(context -> {
            List<Account<?>> copy = new ArrayList<>();
            for (Account<?> a : Accounts.get()) copy.add(a);

            int removed = 0;
            for (Account<?> acc : copy) {
                if (acc instanceof CrackedAccount) {
                    Accounts.get().remove(acc);
                    removed++;
                }
            }

            Accounts.get().save();

            if (removed > 0) {
                info("Cleared " + removed + " cracked accounts.");
            } else {
                info("No cracked accounts to clear.");
            }

            return SINGLE_SUCCESS;
        }));

        builder.then(literal("random").executes(context -> {
            List<CrackedAccount> crackedList = new ArrayList<>();
            for (Account<?> acc : Accounts.get()) {
                if (acc instanceof CrackedAccount) crackedList.add((CrackedAccount) acc);
            }

            if (crackedList.isEmpty()) {
                error("No cracked accounts saved.");
                return SINGLE_SUCCESS;
            }

            CrackedAccount pick = crackedList.get(ThreadLocalRandom.current().nextInt(crackedList.size()));
            ServerInfo currentServer = mc.getCurrentServerEntry();

            MeteorExecutor.execute(() -> {
                if (pick.login()) {
                    Accounts.get().save();
                    info("Logged in as (random): " + pick.getUsername());
                    reconnect(currentServer);
                } else {
                    error("Failed to login as: " + pick.getUsername());
                }
            });

            return SINGLE_SUCCESS;
        }));
    }
}
