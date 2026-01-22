package com.crypto.prayer.application.port.out;

import com.crypto.prayer.adapter.in.websocket.dto.LiquidationMessage;
import com.crypto.prayer.adapter.in.websocket.dto.PrayerResponse;
import com.crypto.prayer.adapter.in.websocket.dto.TickerMessage;

public interface BroadcastPort {

    void broadcastPrayerStats(PrayerResponse stats);

    void broadcastTicker(TickerMessage ticker);

    void broadcastLiquidation(LiquidationMessage liquidation);
}
