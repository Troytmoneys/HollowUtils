package io.github.troytmoneys.hollowutils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PunishmentTest {
    private Punishment entry(String type,long expires) {
        return new Punishment("id","alpha",type,"player","Name","staff","Staff","Reason",100,expires,0,"");
    }
    @Test void timedPunishmentsExpireAtTheirBoundaryAndPermanentBansDoNot() {
        assertTrue(entry("BAN",200).active(199));
        assertFalse(entry("BAN",200).active(200));
        assertTrue(entry("BAN",0).active(Long.MAX_VALUE));
        assertFalse(entry("KICK",0).active(101));
    }
    @Test void scopeIdentityAndTypeMustAllMatch() {
        var ban=entry("BAN",0);
        assertTrue(ban.applies("alpha","BAN","player",150));
        assertFalse(ban.applies("beta","BAN","player",150));
        assertFalse(ban.applies("alpha","MUTE","player",150));
        assertFalse(ban.applies("alpha","BAN","someone-else",150));
    }
    @Test void revocationPreservesEvidenceAndNeverReactivatesOnExpiry() {
        var original=entry("MUTE",200); var revoked=original.revoke(150,"reviewer");
        assertFalse(revoked.active(151)); assertFalse(revoked.active(300));
        assertTrue(original.active(151));
        assertEquals(original.reason(),revoked.reason()); assertEquals(original.actor(),revoked.actor());
        assertEquals("reviewer",revoked.revokedBy()); assertEquals(150,revoked.revoked());
    }
    @Test void unifiedBanAndMuteExpirySupportTimedAndInfiniteDurations() {
        long now=1_000_000;
        assertEquals(0,Punishment.expiry("infinite",now));
        assertEquals(0,Punishment.expiry("INFINITE",now));
        assertEquals(now+30*60_000,Punishment.expiry("30m",now));
        assertEquals(now+7*86_400_000L,Punishment.expiry("7d",now));
        assertTrue(entry("BAN",Punishment.expiry("infinite",now)).active(Long.MAX_VALUE));
        var mute=entry("MUTE",Punishment.expiry("1h",now));
        assertTrue(mute.active(now+3_600_000-1)); assertFalse(mute.active(now+3_600_000));
    }
    @Test void malformedOrOverflowingExpiriesCannotTurnIntoPermanentPunishments() {
        for(String bad:new String[]{"0m","forever","-3d","reason-without-duration","1e9h"})
            assertThrows(IllegalArgumentException.class,()->Punishment.expiry(bad,100));
        assertThrows(IllegalArgumentException.class,()->Punishment.expiry("1h",Long.MAX_VALUE));
    }
    @Test void durationsHaveExplicitUnitsAndRejectOverflowZeroAndUnboundedInput() {
        assertEquals(30*60_000,Punishment.duration("30m"));
        assertEquals(7*86_400_000L,Punishment.duration("1W"));
        assertEquals(12*3_600_000L,Punishment.duration("12h"));
        for (String bad:new String[]{"0m","-1d","30","1month","99999999999999999999999w","3651d","1h30m"})
            assertThrows(IllegalArgumentException.class,()->Punishment.duration(bad));
    }
}
