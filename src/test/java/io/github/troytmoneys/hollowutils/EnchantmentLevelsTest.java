package io.github.troytmoneys.hollowutils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnchantmentLevelsTest {
    @Test void acceptsRemovalAndNativeBoundaryLevelsExactly() {
        assertEquals(0,EnchantmentLevels.parse("0"));assertEquals(1,EnchantmentLevels.parse("1"));assertEquals(255,EnchantmentLevels.parse("255"));
    }
    @Test void unsupportedHighLevelsAreExplicitlyRejectedInsteadOfClampedOrWrapped() {
        for(String input:new String[]{"256","32767","32768","-1","999999999999999","NaN","1.5"})
            assertThrows(IllegalArgumentException.class,()->EnchantmentLevels.parse(input));
        assertTrue(assertThrows(IllegalArgumentException.class,()->EnchantmentLevels.parse("32768")).getMessage().contains("255"));
    }
}
