package com.ayquza.addon.commands;



import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.ayquza.addon.modules.CrackedKickModule;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.PlayerListEntryArgumentType;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class CrackedKickCommand extends Command {
    public CrackedKickCommand() {
        super("cracked-kick", "Kicks a player on a cracked server. Configure with the module that has the same name.", "ckick");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(
            literal("*")
                .executes(ctx -> kick(null))
        ).then(
            argument("player", PlayerListEntryArgumentType.create())
                .executes(ctx -> kick(PlayerListEntryArgumentType.get(ctx)))
        );
    }

    private int kick(@Nullable PlayerListEntry entry) {
        if (mc.isInSingleplayer()) {
            warning("Not available in singleplayer!");
            return SINGLE_SUCCESS;
        }
        CrackedKickModule module = Modules.get().get(CrackedKickModule.class);
        if (entry == null) {
            Collection<PlayerListEntry> entries = mc.getNetworkHandler().getPlayerList();
            for (PlayerListEntry entry2 : entries)
                CrackedKickModule.kick(module, entry2);
        } else CrackedKickModule.kick(module, entry);
        return SINGLE_SUCCESS;
    }
}
