package io.github.troytmoneys.hollowutils;

import org.bukkit.World;
import org.bukkit.WorldType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ServerCreationTest {
    @Test void legacyCommandsStillCreateFullServersAndPreserveSeeds() {
        var defaults=ServerCreation.parse(new String[]{"survival"},()->123);
        assertEquals(ServerCreation.Mode.FULL,defaults.mode()); assertEquals(123,defaults.seed());
        var seeded=ServerCreation.parse(new String[]{"survival","-42"},()->{throw new AssertionError("Explicit seed must not use randomness");});
        assertEquals(ServerCreation.Mode.FULL,seeded.mode()); assertEquals(-42,seeded.seed());
    }
    @Test void fullIncludesOnlyTheExpectedThreeDimensions() {
        var plan=ServerCreation.parse(new String[]{"survival","FULL","0"},()->999);
        assertEquals(0,plan.seed());
        assertEquals(List.of("hu_survival","hu_survival_nether","hu_survival_the_end"),plan.worldNames());
        assertEquals(List.of(World.Environment.NORMAL,World.Environment.NETHER,World.Environment.THE_END),plan.dimensions().stream().map(ServerCreation.Dimension::environment).toList());
        assertTrue(plan.dimensions().stream().allMatch(d->d.terrain()==WorldType.NORMAL));
    }
    @Test void normalAndFlatCreateOnlyAnOverworldWithTheCorrectGenerator() {
        var normal=ServerCreation.parse(new String[]{"solo","normal","9223372036854775807"},()->999);
        var flat=ServerCreation.parse(new String[]{"solo","flat"},()->-7);
        assertEquals(List.of("hu_solo"),normal.worldNames()); assertEquals(List.of("hu_solo"),flat.worldNames());
        assertEquals(WorldType.NORMAL,normal.dimensions().getFirst().terrain()); assertEquals(WorldType.FLAT,flat.dimensions().getFirst().terrain());
        assertEquals(World.Environment.NORMAL,flat.dimensions().getFirst().environment());assertEquals(-7,flat.seed());
        assertEquals(Long.MAX_VALUE,normal.seed());
    }
    @Test void invalidPlansAreRejectedBeforeWorldOperations() {
        for(String[] args:List.of(new String[0],new String[]{"../bad","flat"},new String[]{"solo","skyblock"},new String[]{"solo","flat","text"},
                new String[]{"solo","flat","9223372036854775808"},new String[]{"solo","12","34"},new String[]{"solo","full","1","extra"}))
            assertThrows(IllegalArgumentException.class,()->ServerCreation.parse(args,()->1));
    }
}
