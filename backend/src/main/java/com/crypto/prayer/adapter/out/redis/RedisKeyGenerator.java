package com.crypto.prayer.adapter.out.redis;

import com.crypto.prayer.domain.model.Side;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class RedisKeyGenerator {

    private static final String PREFIX = "prayer";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long TTL_HOURS = 48;

    public String generateKey(Side side) {
        return generateKey(LocalDate.now(), side);
    }

    public String generateKey(LocalDate date, Side side) {
        String dateStr = date.format(DATE_FORMAT);
        return String.format("%s:%s:%s", PREFIX, dateStr, side.getKey());
    }

    public String getUpKey() {
        return generateKey(Side.UP);
    }

    public String getDownKey() {
        return generateKey(Side.DOWN);
    }

    public long getTtlSeconds() {
        return TTL_HOURS * 60 * 60;
    }
}
