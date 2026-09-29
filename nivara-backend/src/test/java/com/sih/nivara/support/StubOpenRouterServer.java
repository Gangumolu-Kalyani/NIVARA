package com.sih.nivara.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * A local stand-in for OpenRouter's chat completions endpoint: answers with scripted responses in
 * order and records every request it receives. Nothing leaves the machine.
 */
public final class StubOpenRouterServer implements AutoCloseable {

    /** One scripted answer: HTTP status, body, optional Retry-After, and a delay before answering. */
    public record Scripted(int status, String body, String retryAfter, long delayMillis) {
        public static Scripted ok(String body) {
            return new Scripted(200, body, null, 0);
        }

        public static Scripted status(int status) {
            return new Scripted(status, "{\"error\":{\"code\":" + status + ",\"message\":\"stub\"}}", null, 0);
        }

        public Scripted delayed(long millis) {
            return new Scripted(status, body, retryAfter, millis);
        }

        public Scripted withRetryAfter(String seconds) {
            return new Scripted(status, body, seconds, delayMillis);
        }
    }

    /** What the stub received. */
    public record Received(String method, String path, Map<String, List<String>> headers, String body) {
        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().get(0))
                    .findFirst().orElse(null);
        }
    }

    private final HttpServer server;
    private final ConcurrentLinkedQueue<Scripted> script = new ConcurrentLinkedQueue<>();
    private final List<Received> received = new CopyOnWriteArrayList<>();

    public StubOpenRouterServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    /** The base URL to configure, like https://openrouter.ai/api/v1. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1";
    }

    public void enqueue(Scripted... responses) {
        script.addAll(List.of(responses));
    }

    public List<Received> received() {
        return List.copyOf(received);
    }

    public void reset() {
        script.clear();
        received.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                Map.copyOf(exchange.getRequestHeaders()), body));
        Scripted next = script.poll();
        if (next == null) {
            next = Scripted.status(500);
        }
        if (next.delayMillis() > 0) {
            try {
                Thread.sleep(next.delayMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = next.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (next.retryAfter() != null) {
            exchange.getResponseHeaders().add("Retry-After", next.retryAfter());
        }
        try {
            exchange.sendResponseHeaders(next.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException ignored) {
            // the client gave up waiting (a timeout test); nothing to answer
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ---- canned OpenRouter bodies ------------------------------------------------------------

    public static String textResponse(String model, String content) {
        return "{\"id\":\"gen-1\",\"model\":\"" + model + "\",\"choices\":[{\"index\":0,\"finish_reason\":\"stop\","
                + "\"message\":{\"role\":\"assistant\",\"content\":" + quote(content) + "}}]}";
    }

    /** calls alternate id, name, arguments JSON. */
    public static String toolCallResponse(String model, String... calls) {
        StringBuilder toolCalls = new StringBuilder();
        for (int i = 0; i < calls.length; i += 3) {
            if (i > 0) {
                toolCalls.append(',');
            }
            toolCalls.append("{\"id\":\"").append(calls[i]).append("\",\"type\":\"function\",\"function\":{\"name\":\"")
                    .append(calls[i + 1]).append("\",\"arguments\":").append(quote(calls[i + 2])).append("}}");
        }
        return "{\"id\":\"gen-2\",\"model\":\"" + model + "\",\"choices\":[{\"index\":0,\"finish_reason\":\"tool_calls\","
                + "\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[" + toolCalls + "]}}]}";
    }

    public static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
