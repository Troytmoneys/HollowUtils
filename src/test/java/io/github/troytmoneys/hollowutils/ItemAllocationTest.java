package io.github.troytmoneys.hollowutils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemAllocationTest {
    @Test void mergesOnlyCompatibleStacksBeforeUsingEmptySlots() {
        int[] existing={60,32,0,0};boolean[] compatible={true,false,false,false};
        int[] added=ItemAllocation.plan(existing,compatible,64,100);
        assertArrayEquals(new int[]{4,0,64,32},added);
        assertArrayEquals(new int[]{60,32,0,0},existing);
    }
    @Test void rejectsOverflowWithoutMutatingAnyInput() {
        int[] existing={63,64};boolean[] same={true,true};
        assertThrows(IllegalArgumentException.class,()->ItemAllocation.plan(existing,same,64,2));
        assertArrayEquals(new int[]{63,64},existing);
    }
    @Test void unstackableAndSixteenStackItemsKeepTheirNativeLimits() {
        assertArrayEquals(new int[]{1,1,1},ItemAllocation.plan(new int[3],new boolean[3],1,3));
        assertArrayEquals(new int[]{1,16},ItemAllocation.plan(new int[]{15,0},new boolean[]{true,false},16,17));
        assertThrows(IllegalArgumentException.class,()->ItemAllocation.plan(new int[2],new boolean[2],1,3));
    }
    @Test void unrelatedAndAlreadyOverstackedItemsAreNeverChanged() {
        assertArrayEquals(new int[]{0,0,32},ItemAllocation.plan(new int[]{99,2,0},new boolean[]{true,false,false},64,32));
    }
    @Test void amountsRequireBoundedPositiveIntegersAndCannotOverflow() {
        assertEquals(32768,ItemAllocation.amount("32768"));
        for(String input:new String[]{"0","-1","32769","2147483648","NaN","1.5"})assertThrows(IllegalArgumentException.class,()->ItemAllocation.amount(input));
        assertThrows(IllegalArgumentException.class,()->ItemAllocation.plan(new int[36],new boolean[36],64,32768));
    }
}
