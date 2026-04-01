package com.example.vauch;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class VauchCheck implements ClientModInitializer {

    // API Endpoints
    private static final String API_CHECK_URL = "https://vauch-check-api.ramirodelgadocortes.workers.dev/check?username=";
    private static final String API_REPORT_URL = "https://vauch-check-api.ramirodelgadocortes.workers.dev/report";
    
    private static final ConcurrentHashMap<String, CachedReport> CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; 

    private record CachedReport(VauchReport report, long timestamp) {}

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            
            // 1. Existing /check command
            dispatcher.register(ClientCommandManager.literal("check")
                .then(ClientCommandManager.argument("player", StringArgumentType.word())
                    .executes(context -> {
                        String target = StringArgumentType.getString(context, "player");
                        context.getSource().sendFeedback(Text.literal("Querying Vauch Network for ").append(Text.literal(target).formatted(Formatting.AQUA)).append("...").formatted(Formatting.GRAY));
                        performVouchCheck(target).thenAccept(resultText -> {
                            MinecraftClient.getInstance().execute(() -> context.getSource().sendFeedback(resultText));
                        });
                        return 1;
                    })
                )
            );

            // 2. Register Submission Commands
            registerReportCommand(dispatcher, "scammer", "Scammer");
            registerReportCommand(dispatcher, "thief", "Thief");
            registerReportCommand(dispatcher, "griefer", "Griefer");
            registerReportCommand(dispatcher, "vouch", "Vouch");
        });
    }

    // Helper method to register multiple report types cleanly
    private void registerReportCommand(CommandDispatcher<FabricClientCommandSource> dispatcher, String command, String flagType) {
        dispatcher.register(ClientCommandManager.literal(command)
            .then(ClientCommandManager.argument("player", StringArgumentType.word())
                .then(ClientCommandManager.argument("proof_url", StringArgumentType.greedyString())
                    .executes(context -> {
                        String target = StringArgumentType.getString(context, "player");
                        String proof = StringArgumentType.getString(context, "proof_url");
                        
                        context.getSource().sendFeedback(Text.literal("Submitting " + flagType + " report for " + target + "...").formatted(Formatting.GRAY));
                        
                        submitReport(target, flagType, proof).thenAccept(resultText -> {
                            MinecraftClient.getInstance().execute(() -> context.getSource().sendFeedback(resultText));
                        });
                        return 1;
                    })
                )
            )
        );
    }

    // The new POST network request
    private CompletableFuture<Text> submitReport(String targetPlayer, String flagType, String proofUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get the username of the person typing the command
                String reporter = MinecraftClient.getInstance().getSession().getUsername();

                // Construct JSON Payload
                JsonObject json = new JsonObject();
                json.addProperty("target", targetPlayer);
                json.addProperty("type", flagType);
                json.addProperty("proof", proofUrl);
                json.addProperty("reporter", reporter);

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_REPORT_URL))
                        .timeout(java.time.Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return Text.literal("✔ Report successfully submitted for review!").formatted(Formatting.GREEN);
                } else {
                    return Text.literal("❌ Server rejected report (Code: " + response.statusCode() + ")").formatted(Formatting.RED);
                }

            } catch (Exception e) {
                e.printStackTrace();
                return Text.literal("Network Error: Could not reach the server.").formatted(Formatting.DARK_RED);
            }
        });
    }

    // [KEEP YOUR EXISTING performVouchCheck and buildFeedbackText METHODS DOWN HERE]
    private CompletableFuture<Text> performVouchCheck(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = username.toLowerCase();
            if (CACHE.containsKey(cacheKey)) {
                CachedReport cached = CACHE.get(cacheKey);
                if (System.currentTimeMillis() - cached.timestamp() < CACHE_TTL_MS) {
                    return buildFeedbackText(cached.report(), true);
                } else { CACHE.remove(cacheKey); }
            }
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_CHECK_URL + username)).timeout(java.time.Duration.ofSeconds(5)).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                VauchReport report = new com.google.gson.Gson().fromJson(response.body(), VauchReport.class);

                if (report == null || report.status() == null || report.status().equals("No Data")) {
                    return Text.literal("No data found for player: " + username).formatted(Formatting.GRAY);
                }
                CACHE.put(cacheKey, new CachedReport(report, System.currentTimeMillis()));
                return buildFeedbackText(report, false);
            } catch (Exception e) {
                e.printStackTrace();
                return Text.literal("Network Error: Unable to reach the Vauch API.").formatted(Formatting.DARK_RED);
            }
        });
    }

    private MutableText buildFeedbackText(VauchReport report, boolean isCached) {
        Formatting statusColor = report.status().equalsIgnoreCase("Scammer") ? Formatting.RED : Formatting.GOLD;
        if (report.status().equalsIgnoreCase("Good") || report.status().equalsIgnoreCase("Vouched")) statusColor = Formatting.GREEN;

        MutableText feedback = Text.literal("\n=== [" + report.status().toUpperCase() + "] " + report.user() + " ===\n").formatted(statusColor, Formatting.BOLD);
        if (report.summary() != null && !report.summary().isEmpty()) feedback.append(Text.literal(report.summary()).formatted(Formatting.GRAY, Formatting.ITALIC)).append("\n");

        if (report.flags() != null && !report.flags().isEmpty()) {
            feedback.append(Text.literal("Flags: ").formatted(Formatting.WHITE));
            report.flags().forEach((type, count) -> {
                Formatting flagColor = type.equalsIgnoreCase("vouch") ? Formatting.GREEN : Formatting.YELLOW;
                feedback.append(Text.literal("[" + type.toUpperCase() + ": " + count + "] ").formatted(flagColor));
            });
        } else {
            feedback.append(Text.literal("✔ No specific flags on record.").formatted(Formatting.GREEN));
        }

        if (isCached) feedback.append(Text.literal(" (Cached)").formatted(Formatting.DARK_GRAY));

        feedback.append(Text.literal("\n[VIEW FULL RECORD]")
                .formatted(Formatting.DARK_GRAY, Formatting.UNDERLINE)
                .styled(style -> style.withClickEvent(new ClickEvent.OpenUrl(URI.create("https://your-admin-dashboard.com/user/" + report.user())))
                                      .withHoverEvent(new HoverEvent.ShowText(Text.literal("Requires Moderator Auth")))));
        return feedback;
    }
}