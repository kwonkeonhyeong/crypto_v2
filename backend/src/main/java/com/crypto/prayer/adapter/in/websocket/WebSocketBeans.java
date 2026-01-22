package com.crypto.prayer.adapter.in.websocket;

import com.crypto.prayer.adapter.in.websocket.ratelimit.TokenBucketRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSocketBeans {

    @Bean
    public TokenBucketRateLimiter tokenBucketRateLimiter() {
        return new TokenBucketRateLimiter();
    }
}
