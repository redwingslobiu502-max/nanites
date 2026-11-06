package dev.jevmod.core;

import com.sun.net.httpserver.HttpServer;
import dev.jevmod.core.jev.Question;
import dev.jevmod.core.jev.TypeSafeProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TypeSafeTest {
    @TempDir Path temp;

    @Test void newProfilesUseOpenRouterJevAndRequireItsKey() throws Exception {
        var config = JevConfig.load(temp.resolve("jevmod.json"));
        assertEquals("openrouter", config.provider);
        assertEquals("openrouter:~typesafe/jev-latest", config.provider(null, null, key -> "dummy-key").name());
        var error = assertThrows(IllegalStateException.class, () -> config.provider(null, null, key -> null));
        assertTrue(error.getMessage().contains("OPENROUTER_API_KEY"));
        assertFalse(error.getMessage().contains("TYPESAFE_API_KEY"));
    }

    @Test void explicitExistingConfigurationIsPreserved() throws Exception {
        Path file = temp.resolve("jevmod.json");
        String existing = "{\"provider\":\"openrouter\",\"allow_world_edits\":false}";
        Files.writeString(file, existing);
        var config = JevConfig.load(file);
        assertEquals("openrouter", config.provider);
        assertFalse(config.allowWorldEdits);
        assertEquals(existing, Files.readString(file));
        assertEquals("typesafe:jev-latest", config.provider("typesafe", null, key -> "dummy-key").name());
    }

    @Test void openRouterDecisionsRequestAndTypedResponseMatchTheDocumentedContract() throws Exception {
        var request = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        var method = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/alpha/decisions", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            method.set(exchange.getRequestMethod());
            byte[] response = """
                    {"model":"jev-1.13.0","answers":{
                      "activate":{"type":"noul","noul":0.95},
                      "color":{"type":"choice","choice":"blue","probabilities":{"blue":0.9,"red":0.1},"confidence":0.8},
                      "strength":{"type":"score","score":1.5,"legend":{"0":"low","1":"medium","2":"high"},"probabilities":{"0":0,"1":0.5,"2":0.5},"confidence":0.6}
                    },"usage":{"input_tokens":300,"output_tokens":20,"cost":0.0000126}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var out = exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        try {
            var questions = Map.of(
                    "activate", new Question("activate", Question.Type.NOUL, Json.parseObject("{\"question\":\"Activate?\"}"), null),
                    "color", new Question("color", Question.Type.CHOICE, new com.google.gson.JsonPrimitive("Which colour?"), Json.parseObject("{\"blue\":null,\"red\":null}")),
                    "strength", new Question("strength", Question.Type.SCORE, new com.google.gson.JsonPrimitive("Which strength?"), com.google.gson.JsonParser.parseString("[\"low\",\"medium\",\"high\"]")));
            var provider = new dev.jevmod.core.jev.OpenRouterProvider("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/", "dummy-key", "~typesafe/jev-latest", 5000, true);
            var result = provider.evaluate(Json.parseObject("{\"health\":8}"), questions).get(10, java.util.concurrent.TimeUnit.SECONDS);
            var body = Json.parseObject(request.get());
            assertEquals("POST", method.get());
            assertEquals("Bearer dummy-key", auth.get());
            assertEquals("~typesafe/jev-latest", body.get("model").getAsString());
            assertFalse(body.has("messages"));
            assertFalse(body.has("response_format"));
            assertEquals(8, body.getAsJsonObject("state").get("health").getAsInt());
            assertEquals(3, body.getAsJsonObject("questions").size());
            assertEquals("jev-1.13.0", result.model);
            assertEquals(0.95, result.answers.get("activate").noul);
            assertEquals("blue", result.answers.get("color").choice);
            assertEquals(1.5, result.answers.get("strength").score);
            assertEquals(300, result.inputTokens);
            assertEquals(20, Json.parseObject(result.rawResponse).getAsJsonObject("usage").get("output_tokens").getAsInt());
            assertEquals(0.0000126, result.cost);
        } finally {
            server.stop(0);
        }
    }

    @Test void chatRouterAndGeminiKeepTheChatProtocol() {
        assertFalse(dev.jevmod.core.jev.OpenRouterProvider.isDecisionModel("typesafe/jev-router"));
        assertFalse(dev.jevmod.core.jev.OpenRouterProvider.isDecisionModel("google/gemini-2.5-flash-lite"));
        assertTrue(dev.jevmod.core.jev.OpenRouterProvider.isDecisionModel("typesafe/jev-1.13"));
        var chat = new dev.jevmod.core.jev.OpenRouterProvider("https://openrouter.ai/api/v1", "dummy-key", "google/gemini-2.5-flash-lite", 5000, true);
        var body = chat.buildRequest(Json.parseObject("{}"), Map.of());
        assertTrue(body.has("messages"));
        assertTrue(body.has("response_format"));
    }
}
