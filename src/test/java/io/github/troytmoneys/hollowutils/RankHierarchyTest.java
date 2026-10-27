package io.github.troytmoneys.hollowutils;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RankHierarchyTest {
    @Test void strictWeightOrderProtectsEqualAndHigherRanksRegardlessOfOtherPermissions() {
        UUID staff=UUID.randomUUID(),target=UUID.randomUUID();
        var check=List.of(new RankHierarchy.Check(target,false));
        RankHierarchy.require(staff,check,id->id.equals(staff)?100:10);
        assertThrows(IllegalArgumentException.class,()->RankHierarchy.require(staff,check,id->100));
        assertThrows(IllegalArgumentException.class,()->RankHierarchy.require(staff,check,id->id.equals(staff)?10:100));
        assertThrows(IllegalArgumentException.class,()->RankHierarchy.require(staff,check,id->0));
    }
    @Test void sharedIpCannotBeUsedToPunishAnyProtectedAssociatedIdentity() {
        UUID staff=UUID.randomUUID(),low=UUID.randomUUID(),high=UUID.randomUUID();
        var weights=Map.of(staff,50,low,0,high,100);
        assertThrows(IllegalArgumentException.class,()->RankHierarchy.require(staff,List.of(new RankHierarchy.Check(low,false),new RankHierarchy.Check(high,false)),weights::get));
    }
    @Test void ownReversalIsAllowedButSelfPunishmentAndHigherStaffRollbackAreNot() {
        UUID staff=UUID.randomUUID(),higher=UUID.randomUUID();
        RankHierarchy.require(staff,List.of(new RankHierarchy.Check(staff,true)),id->0);
        assertThrows(IllegalArgumentException.class,()->RankHierarchy.require(staff,List.of(new RankHierarchy.Check(staff,false)),id->10));
        assertThrows(IllegalArgumentException.class,()->RankHierarchy.require(staff,List.of(new RankHierarchy.Check(higher,true)),id->id.equals(staff)?10:100));
    }
}
