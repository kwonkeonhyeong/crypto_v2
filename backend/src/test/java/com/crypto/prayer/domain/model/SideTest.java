package com.crypto.prayer.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Side enum")
class SideTest {

    @Nested
    @DisplayName("기본 속성 테스트")
    class BasicProperties {

        @Test
        @DisplayName("UP은_key가_up이다")
        void UP은_key가_up이다() {
            assertEquals("up", Side.UP.getKey());
        }

        @Test
        @DisplayName("DOWN은_key가_down이다")
        void DOWN은_key가_down이다() {
            assertEquals("down", Side.DOWN.getKey());
        }

        @Test
        @DisplayName("UP은_displayName이_상승이다")
        void UP은_displayName이_상승이다() {
            assertEquals("상승", Side.UP.getDisplayName());
        }

        @Test
        @DisplayName("DOWN은_displayName이_하락이다")
        void DOWN은_displayName이_하락이다() {
            assertEquals("하락", Side.DOWN.getDisplayName());
        }
    }

    @Nested
    @DisplayName("fromKey 메서드")
    class FromKey {

        @Test
        @DisplayName("up_문자열로_UP을_반환한다")
        void up_문자열로_UP을_반환한다() {
            assertEquals(Side.UP, Side.fromKey("up"));
        }

        @Test
        @DisplayName("down_문자열로_DOWN을_반환한다")
        void down_문자열로_DOWN을_반환한다() {
            assertEquals(Side.DOWN, Side.fromKey("down"));
        }

        @Test
        @DisplayName("대소문자_구분없이_UP을_반환한다")
        void 대소문자_구분없이_UP을_반환한다() {
            assertEquals(Side.UP, Side.fromKey("UP"));
            assertEquals(Side.UP, Side.fromKey("Up"));
        }

        @Test
        @DisplayName("대소문자_구분없이_DOWN을_반환한다")
        void 대소문자_구분없이_DOWN을_반환한다() {
            assertEquals(Side.DOWN, Side.fromKey("DOWN"));
            assertEquals(Side.DOWN, Side.fromKey("Down"));
        }

        @Test
        @DisplayName("잘못된_key는_IllegalArgumentException을_발생시킨다")
        void 잘못된_key는_IllegalArgumentException을_발생시킨다() {
            assertThrows(IllegalArgumentException.class, () -> Side.fromKey("invalid"));
        }
    }
}
