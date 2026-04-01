package com.example.vauch;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
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

    // Your active Cloudflare Edge Worker API
    private static final String API_BASE_URL = "https://vauch-check-api.ramirodelgadocortes.workers.dev/check?username=";
    
    // Memory Cache Configuration
    private static final ConcurrentHashMap<String, CachedReport> CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 Minutes in milliseconds

    // Inner Record to track cache timestamps
    private record CachedReport(VauchReport report, long timestamp) {}

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("check")
                .then(ClientCommandManager.argument("player", StringArgumentType.word())
                    .executes(context -> {
                        String target = StringArgumentType.getString(context, "player");

                        context.getSource().sendFeedback(Text.literal("Querying Vauch Network for ")
                                .append(Text.literal(target).formatted(Formatting.AQUA))
                                .append("...").formatted(Formatting.GRAY));

                        // Dispatch network/cache check asynchronously
                        performVouchCheck(target).thenAccept(resultText -> {
                            // SCHEDULE ON MAIN RENDER THREAD TO PREVENT HUD BLOCKING
                            MinecraftClient.getInstance().execute(() -> {
                                context.getSource().sendFeedback(resultText);
                            });
                        });

                        return 1;
                    })
                )
            );
        });
    }

    private CompletableFuture<Text> performVouchCheck(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = username.toLowerCase(); // Prevent duplicates like Steve vs steve

            // 1. Check Cache First
            if (CACHE.containsKey(cacheKey)) {
                CachedReport cached = CACHE.get(cacheKey);
                if (System.currentTimeMillis() - cached.timestamp() < CACHE_TTL_MS) {
                    return buildFeedbackText(cached.report(), true);
                } else {
                    CACHE.remove(cacheKey); // Purge expired
                }
            }

            // 2. Network Request Fallback
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE_URL + username))
                        .timeout(java.time.Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                
                // Map the JSON to our VauchReport Java record
                VauchReport report = new com.google.gson.Gson().fromJson(response.body(), VauchReport.class);

                // Handle missing users based on your API schema
                if (report == null || report.status() == null || report.status().equals("No Data")) {
                    return Text.literal("No data found for player: " + username).formatted(Formatting.GRAY);
                }

                // Cache successful fetches
                CACHE.put(cacheKey, new CachedReport(report, System.currentTimeMillis()));

                return buildFeedbackText(report, false);

            } catch (Exception e) {
                // Print stack trace to the Prism Launcher console if JSON parsing fails
                e.printStackTrace(); 
                return Text.literal("Network Error: Unable to reach the Vauch API.").formatted(Formatting.DARK_RED);
            }
        });
    }

    private MutableText buildFeedbackText(VauchReport report, boolean isCached) {
        // Determine Status Color
        Formatting statusColor = report.status().equalsIgnoreCase("Scammer") ? Formatting.RED : Formatting.GOLD;
        if (report.status().equalsIgnoreCase("Good") || report.status().equalsIgnoreCase("Vouched")) {
            statusColor = Formatting.GREEN;
        }

        // Build Title
        MutableText feedback = Text.literal("\n=== [" + report.status().toUpperCase() + "] " + report.user() + " ===\n").formatted(statusColor, Formatting.BOLD);
        
        // Add Admin Summary
        if (report.summary() != null && !report.summary().isEmpty()) {
            feedback.append(Text.literal(report.summary()).formatted(Formatting.GRAY, Formatting.ITALIC)).append("\n");
        }

        // Loop over the specific flags mapping
        if (report.flags() != null && !report.flags().isEmpty()) {
            feedback.append(Text.literal("Flags: ").formatted(Formatting.WHITE));
            report.flags().forEach((type, count) -> {
                Formatting flagColor = type.equalsIgnoreCase("vouch") ? Formatting.GREEN : Formatting.YELLOW;
                feedback.append(Text.literal("[" + type.toUpperCase() + ": " + count + "] ").formatted(flagColor));
            });
        } else {
            feedback.append(Text.literal("✔ No specific flags on record.").formatted(Formatting.GREEN));
        }

        // Show (Cached) tag if pulled from local memory
        if (isCached) {
            feedback.append(Text.literal(" (Cached)").formatted(Formatting.DARK_GRAY));
        }

        // Apply 1.21.10+ Clickable Component UI logic
        feedback.append(Text.literal("\n[VIEW FULL RECORD]")
                .formatted(Formatting.DARK_GRAY, Formatting.UNDERLINE)
                .styled(style -> style.withClickEvent(new ClickEvent.OpenUrl(URI.create("https://vauch-check-api.ramirodelgadocortes.workers.dev/admincheck?username=/user/" + report.user())))
                                      .withHoverEvent(new HoverEvent.ShowText(Text.literal("Requires Moderator Auth")))));

        return feedback;
    }
}