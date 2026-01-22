package com.crypto.prayer.application.service;

import com.crypto.prayer.adapter.in.websocket.dto.LiquidationMessage;
import com.crypto.prayer.adapter.in.websocket.dto.PrayerResponse;
import com.crypto.prayer.adapter.in.websocket.dto.TickerMessage;
import com.crypto.prayer.application.port.out.BroadcastPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class BroadcastService implements BroadcastPort {

    private static final Logger log = LoggerFactory.getLogger(BroadcastService.class);

    private static final String TOPIC_PRAYER = "/topic/prayer";
    private static final String TOPIC_TICKER = "/topic/ticker";
    private static final String TOPIC_LIQUIDATION = "/topic/liquidation";

    private final SimpMessagingTemplate messagingTemplate;

    public BroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void broadcastPrayerStats(PrayerResponse stats) {
        messagingTemplate.convertAndSend(TOPIC_PRAYER, stats);
    }

    @Override
    public void broadcastTicker(TickerMessage ticker) {
        messagingTemplate.convertAndSend(TOPIC_TICKER, ticker);
        log.debug("Ticker broadcast: symbol={}, price={}",
            ticker.symbol(), ticker.price());
    }

    @Override
    public void broadcastLiquidation(LiquidationMessage liquidation) {
        messagingTemplate.convertAndSend(TOPIC_LIQUIDATION, liquidation);
        log.debug("Liquidation broadcast: symbol={}, side={}, value=${}",
            liquidation.symbol(), liquidation.side(), liquidation.usdValue());
    }
}
