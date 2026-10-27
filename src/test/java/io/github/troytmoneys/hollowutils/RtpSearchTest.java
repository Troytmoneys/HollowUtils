package io.github.troytmoneys.hollowutils;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class RtpSearchTest {
    @Test void generatedBlockCentersStayInTheSpawnAnnulusAndShiftedWorldBorder() {
        var random=new Random(7);var bounds=new RtpSearch.Bounds(80,410,180,510);
        for(int i=0;i<500;i++) {
            var point=RtpSearch.sample(random,200,300,40,5000,bounds).orElseThrow();
            double x=point.x()+.5,z=point.z()+.5;
            assertTrue(x>=80&&x<=410&&z>=180&&z<=510);
            assertTrue(Math.hypot(x-200,z-300)>=40);assertTrue(Math.hypot(x-200,z-300)<=5000);
        }
    }
    @Test void impossibleBordersAndRadiusIntersectionsReturnNoDestinationWithoutUnboundedLoops() {
        assertTrue(RtpSearch.sample(new Random(1),0,0,128,5000,new RtpSearch.Bounds(-5,5,-5,5)).isEmpty());
        assertTrue(RtpSearch.sample(new Random(1),0,0,0,10,new RtpSearch.Bounds(100,200,100,200)).isEmpty());
        assertThrows(IllegalArgumentException.class,()->RtpSearch.sample(new Random(),0,0,-1,500,new RtpSearch.Bounds(-500,500,-500,500)));
    }
}
