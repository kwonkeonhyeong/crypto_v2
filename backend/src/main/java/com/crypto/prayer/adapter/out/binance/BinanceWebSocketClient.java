package com.crypto.prayer.adapter.out.binance;

import com.crypto.prayer.adapter.out.binance.reconnect.ExponentialBackoff;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Component
public class BinanceWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceWebSocketClient.class);

    private final BinanceConfig config;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, WebSocketConnection> connections;

    public BinanceWebSocketClient(BinanceConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.connections = new ConcurrentHashMap<>();
    }

    @PreDestroy
    public void destroy() {
        connections.values().forEach(WebSocketConnection::close);
        scheduler.shutdown();
        log.info("BinanceWebSocketClient destroyed");
    }

    /**
     * 스트림 연결
     */
    public void connect(String streamName, String url, Consumer<String> messageHandler) {
        WebSocketConnection connection = new WebSocketConnection(
            streamName, url, messageHandler);
        connections.put(streamName, connection);
        connection.connect();
    }

    /**
     * 스트림 연결 해제
     */
    public void disconnect(String streamName) {
        WebSocketConnection connection = connections.remove(streamName);
        if (connection != null) {
            connection.close();
        }
    }

    /**
     * 연결 상태 확인
     */
    public boolean isConnected(String streamName) {
        WebSocketConnection connection = connections.get(streamName);
        return connection != null && connection.isConnected();
    }

    private class WebSocketConnection implements WebSocket.Listener {

        private final String streamName;
        private final String url;
        private final Consumer<String> messageHandler;
        private final ExponentialBackoff backoff;
        private final StringBuilder messageBuffer;

        private volatile WebSocket webSocket;
        private volatile boolean closed = false;
        private volatile boolean connected = false;

        WebSocketConnection(String streamName, String url, Consumer<String> messageHandler) {
            this.streamName = streamName;
            this.url = url;
            this.messageHandler = messageHandler;
            this.backoff = new ExponentialBackoff(
                config.getReconnectInitialDelayMs(),
                config.getReconnectMaxDelayMs(),
                2.0, 0.1
            );
            this.messageBuffer = new StringBuilder();
        }

        void connect() {
            if (closed) return;

            log.info("Connecting to {} stream: {}", streamName, url);

            httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(url), this)
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    this.connected = true;
                    backoff.reset();
                    log.info("Connected to {} stream", streamName);
                })
                .exceptionally(ex -> {
                    log.error("Failed to connect to {} stream: {}",
                        streamName, ex.getMessage());
                    scheduleReconnect();
                    return null;
                });
        }

        void close() {
            closed = true;
            connected = false;
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Shutdown");
            }
        }

        boolean isConnected() {
            return connected;
        }

        private void scheduleReconnect() {
            if (closed) return;

            long delay = backoff.nextDelayMs();
            log.info("Scheduling reconnect for {} in {}ms (attempt {})",
                streamName, delay, backoff.getAttempt());

            scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            log.debug("{} WebSocket opened", streamName);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);

            if (last) {
                String message = messageBuffer.toString();
                messageBuffer.setLength(0);

                try {
                    messageHandler.accept(message);
                } catch (Exception e) {
                    log.error("Error processing {} message: {}",
                        streamName, e.getMessage());
                }
            }

            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.warn("{} WebSocket closed: {} - {}",
                streamName, statusCode, reason);
            connected = false;

            if (!closed) {
                scheduleReconnect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.error("{} WebSocket error: {}",
                streamName, error.getMessage());
            connected = false;

            if (!closed) {
                scheduleReconnect();
            }
        }
    }
}
