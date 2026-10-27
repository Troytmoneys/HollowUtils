package io.github.troytmoneys.hollowutils;

import java.util.*;
import java.util.random.RandomGenerator;

final class RtpSearch {
    record Point(int x,int z) { }
    record Bounds(double minX,double maxX,double minZ,double maxZ) { }
    private RtpSearch() { }
    static Optional<Point> sample(RandomGenerator random,double centerX,double centerZ,double min,double max,Bounds border) {
        if(!Double.isFinite(min)||!Double.isFinite(max)||min<0||max<min||max>29_000_000)throw new IllegalArgumentException("Invalid RTP radius settings.");
        double lowX=Math.max(centerX-max,border.minX()),highX=Math.min(centerX+max,border.maxX());
        double lowZ=Math.max(centerZ-max,border.minZ()),highZ=Math.min(centerZ+max,border.maxZ());
        if(lowX>=highX||lowZ>=highZ)return Optional.empty();
        for(int n=0;n<64;n++) {
            int x=(int)Math.floor(random.nextDouble(lowX,highX)),z=(int)Math.floor(random.nextDouble(lowZ,highZ));
            double dx=x+.5-centerX,dz=z+.5-centerZ,distance=dx*dx+dz*dz;
            if(x+.5<border.minX()||x+.5>border.maxX()||z+.5<border.minZ()||z+.5>border.maxZ())continue;
            if(distance>=min*min&&distance<=max*max)return Optional.of(new Point(x,z));
        }
        return Optional.empty();
    }
}
