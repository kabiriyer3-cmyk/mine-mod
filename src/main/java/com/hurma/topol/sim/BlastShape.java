package com.hurma.topol.sim;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Block-world bowl and radial work order. All sizes are game settings, not real-world blast scaling. */
public final class BlastShape {
    private static final Map<Integer, long[]> CACHE = new ConcurrentHashMap<>();
    private BlastShape() {}
    public static long[] columns(int radius) {
        if (radius < 1 || radius > 384) throw new IllegalArgumentException("radius");
        return CACHE.computeIfAbsent(radius, r -> {
            long[] a = new long[(2*r+1)*(2*r+1)];
            int n=0;
            for (int x=-r;x<=r;x++) for (int z=-r;z<=r;z++) {
                int d=x*x+z*z;
                if (d<=r*r) a[n++]=((long)d<<22) | ((long)(x+512)<<11) | (z+512);
            }
            a=Arrays.copyOf(a,n); Arrays.sort(a); return a;
        });
    }
    public static int x(long key) { return (int)((key>>>11)&2047)-512; }
    public static int z(long key) { return (int)(key&2047)-512; }
    public static double distance(long key) { return Math.sqrt(key>>>22); }
    public static double noise(int x,int z,long seed) {
        long n=x*73428767L ^ z*912931L ^ seed;
        n=(n^(n>>>33))*0xff51afd7ed558ccdL;
        n=(n^(n>>>33))*0xc4ceb9fe1a85ec53L;
        return ((n^(n>>>33))&0xffff)/65535.0;
    }
    public static int floor(double ground, double distance, int radius, int depth, int x, int z, long seed) {
        double r=distance/Math.max(1,radius);
        return (int)Math.floor(ground-depth*Math.max(0,1-r*r)+(noise(x,z,seed)-.5)*2.8);
    }
}
