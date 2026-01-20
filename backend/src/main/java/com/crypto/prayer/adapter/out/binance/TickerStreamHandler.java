package com.crypto.prayer.adapter.out.binance;

import com.crypto.prayer.adapter.in.websocket.dto.TickerMessage;
import com.crypto.prayer.adapter.out.binance.dto.BinanceTickerEvent;
import com.crypto.prayer.application.port.out.BroadcastPort;
import com.crypto.prayer.domain.model.Ticker;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class TickerStreamHandler {

    private static final Logger log = LoggerFactory.getLogger(TickerStreamHandler.class);

    private final BinanceWebSocketClient webSocketClient;
    private final BinanceConfig config;
    private final BroadcastPort broadcastPort;
    private final ObjectMapper objectMapper;

    private final AtomicReference<Ticker> latestTicker = new AtomicReference<>();

    public TickerStreamHandler(
            BinanceWebSocketClient webSocketClient,
            BinanceConfig config,
            BroadcastPort broadcastPort,
            ObjectMapper objectMapper) {
        this.webSocketClient = webSocketClient;
        this.config = config;
        this.broadcastPort = broadcastPort;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        webSocketClient.connect(
            "ticker",
            config.getTickerStreamUrl(),
            this::handleMessage
        );
        log.info("Ticker stream handler started");
    }

    public void handleMessage(String message) {
        try {
            BinanceTickerEvent event = objectMapper.readValue(
                message, BinanceTickerEvent.class);

            // 도메인 모델로 변환
            Ticker ticker = Ticker.of(
                event.symbol(),
                event.getPrice(),
                event.getPriceChangePercent()
            );

            latestTicker.set(ticker);

            // 브로드캐스트 DTO로 변환 및 전송
            TickerMessage tickerMessage = TickerMessage.of(
                ticker.symbol(),
                ticker.price(),
                ticker.priceChange24h()
            );

            broadcastPort.broadcastTicker(tickerMessage);

        } catch (Exception e) {
            log.error("Failed to parse ticker message: {}", e.getMessage());
        }
    }

    public Ticker getLatestTicker() {
        return latestTicker.get();
    }
}
