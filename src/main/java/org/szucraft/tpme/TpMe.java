package org.szucraft.tpme;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class TpMe implements ModInitializer {

    public static final String MOD_ID = "tpme";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final HashMap<String, BlockPos> locations = new HashMap<>();
    private static final Path configFile = FabricLoader.getInstance().getConfigDir().resolve("tpme.dat");

    @Override
    public void onInitialize() {
        loadLocations();
        LOGGER.info("[TpMe] Started up with loaded {} locations.", locations.size());
        CommandRegistrationCallback.EVENT.register(this::registerCommands);
        LOGGER.info("[TpMe] Loaded.");
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {

        LiteralArgumentBuilder<ServerCommandSource> tpMeCommand = CommandManager.literal("tpme");
        tpMeCommand.then(CommandManager.literal("add")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("location", StringArgumentType.word())
                        .executes(context -> addLocation(context, context.getArgument("location", String.class)))));

        tpMeCommand.then(CommandManager.literal("remove")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("location", StringArgumentType.word())
                        .suggests(new TpMeSuggestionProvider())
                        .executes(context -> removeLocation(context, context.getArgument("location", String.class)))));

        tpMeCommand.then(CommandManager.literal("update")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("location", StringArgumentType.string())
                        .suggests(new TpMeSuggestionProvider())
                        .executes(context -> updateLocation(context, context.getArgument("location", String.class)))));

        tpMeCommand.then(CommandManager.literal("list")
                .executes(this::listLocations));

        tpMeCommand.then(CommandManager.argument("location", StringArgumentType.word())
                .suggests(new TpMeSuggestionProvider())
                .executes(context -> goToLocation(context, context.getArgument("location", String.class))));

        dispatcher.register(tpMeCommand);
    }

    private int listLocations(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        if (locations.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No locations saved."), false);
        } else {
            source.sendFeedback(() -> Text.literal("Saved locations: \n" + String.join(", \n", locations.keySet())), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int goToLocation(CommandContext<ServerCommandSource> context, String location) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        BlockPos pos = locations.get(location);
        if (pos == null) {
            source.sendFeedback(() -> Text.literal("Location '" + location + "' not found."), false);
        } else if (player != null) {
            player.teleport(player.getServerWorld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), player.getPitch());
            source.sendFeedback(() -> Text.literal("Teleported to '" + location + "'"), false);
        } else {
            source.sendFeedback(() -> Text.literal("Not terminal command."), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int addLocation(CommandContext<ServerCommandSource> context, String location) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendFeedback(() -> Text.literal("Not terminal command."), false);
        } else if (locations.containsKey(location)) {
            source.sendFeedback(() -> Text.literal("Location '" + location + "' already exists."), false);
        } else if (Objects.equals(location, "list")) {
            source.sendFeedback(() -> Text.literal("Invalid location name."), false);
        } else {
            BlockPos pos = player.getBlockPos();
            locations.put(location, pos);
            source.sendFeedback(() -> Text.literal("Added location '" + location + "' at " + pos), false);
            saveLocations();
        }
        return Command.SINGLE_SUCCESS;
    }

    private int removeLocation(CommandContext<ServerCommandSource> context, String location) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        if (locations.remove(location) != null) {
            source.sendFeedback(() -> Text.literal("Removed location '" + location + "'"), false);
            saveLocations();
        } else {
            source.sendFeedback(() -> Text.literal("Location '" + location + "' not found."), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int updateLocation(CommandContext<ServerCommandSource> context, String location) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        if (locations.containsKey(location)) {
            ServerPlayerEntity player = source.getPlayer();
            if (player != null) {
                BlockPos pos = player.getBlockPos();
                locations.put(location, pos);
                source.sendFeedback(() -> Text.literal("Updated location '" + location + "' at " + pos), false);
                saveLocations();
            } else {
                source.sendFeedback(() -> Text.literal("Not terminal command."), false);
            }
        } else {
            source.sendFeedback(() -> Text.literal("Location '" + location + "' not found."), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    public Collection<String> getLocations() {
        return locations.keySet();
    }

    public class TpMeSuggestionProvider implements SuggestionProvider<ServerCommandSource> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) throws CommandSyntaxException {
            Collection<String> locationNames = getLocations();
            for (String locationName : locationNames) {
                builder.suggest(locationName);
            }
            return builder.buildFuture();
        }
    }

    private void saveLocations() {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(configFile.toFile()))) {
            out.writeInt(locations.size());
            for (var entry : locations.entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeInt(entry.getValue().getX());
                out.writeInt(entry.getValue().getY());
                out.writeInt(entry.getValue().getZ());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save Locations: {}", String.valueOf(e));
        }
    }

    private void loadLocations() {
        if (configFile.toFile().exists()) {
            try (DataInputStream in = new DataInputStream(new FileInputStream(configFile.toFile()))) {
                int size = in.readInt();
                for (int i = 0; i < size; i++) {
                    String location = in.readUTF();
                    int x = in.readInt();
                    int y = in.readInt();
                    int z = in.readInt();
                    locations.put(location, new BlockPos(x, y, z));
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load Locations: {}", String.valueOf(e));
            }
        }
    }

}