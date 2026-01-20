package com.crypto.prayer.adapter.out.binance;

import com.crypto.prayer.adapter.in.websocket.dto.LiquidationMessage;
import com.crypto.prayer.adapter.out.binance.dto.BinanceLiquidationEvent;
import com.crypto.prayer.application.port.out.BroadcastPort;
import com.crypto.prayer.domain.model.Liquidation;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LiquidationStreamHandler {

    private static final Logger log = LoggerFactory.getLogger(LiquidationStreamHandler.class);

    private final BinanceWebSocketClient webSocketClient;
    private final BinanceConfig config;
    private final BroadcastPort broadcastPort;
    private final ObjectMapper objectMapper;

    public LiquidationStreamHandler(
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
            "liquidation",
            config.getLiquidationStreamUrl(),
            this::handleMessage
        );
        log.info("Liquidation stream handler started");
    }

    public void handleMessage(String message) {
        try {
            BinanceLiquidationEvent event = objectMapper.readValue(
                message, BinanceLiquidationEvent.class);

            // 도메인 모델로 변환
            Liquidation liquidation = Liquidation.of(
                event.getSymbol(),
                event.getSide(),
                event.getQuantity(),
                event.getPrice()
            );

            // 브로드캐스트 DTO로 변환
            LiquidationMessage liqMessage = LiquidationMessage.of(
                liquidation.symbol(),
                liquidation.side().name(),
                liquidation.quantity(),
                liquidation.price()
            );

            broadcastPort.broadcastLiquidation(liqMessage);

            if (liquidation.isLarge()) {
                log.info("Large liquidation detected: {} {} {}",
                    liquidation.symbol(),
                    liquidation.side(),
                    liquidation.formattedValue());
            }

        } catch (Exception e) {
            log.error("Failed to parse liquidation message: {}", e.getMessage());
        }
    }
}
