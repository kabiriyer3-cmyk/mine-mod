package com.hurma.topol.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GamePhysicsTest {
    @Test public void endpoints() {
        for (double range:new double[]{128,1000,6000,24000}) {
            int total=FlightPath.durationTicks(range);
            FlightPath p=new FlightPath(range,37,total);
            assertEquals(0,p.at(0).x(),1e-9);
            assertEquals(0,p.at(0).y(),1e-9);
            assertEquals(range,p.at(total/20.0).x(),1e-7);
            assertEquals(37,p.at(total/20.0).y(),1e-7);
            assertEquals(0,p.at(total/20.0).side(),1e-7);
        }
    }
    @Test public void finiteAndMonotonic() {
        FlightPath p=new FlightPath(5000,-60,FlightPath.durationTicks(5000));
        double previous=-1;
        for(int tick=0;tick<=4000;tick++) {
            FlightPath.Sample s=p.at(tick/20.0);
            assertTrue(Double.isFinite(s.y())&&Double.isFinite(s.vy()));
            assertTrue(s.x()>=previous);
            previous=s.x();
        }
    }
    @Test public void deterministic() {
        FlightPath a=new FlightPath(1250,0,2200), b=new FlightPath(1250,0,2200);
        for (int t=0;t<2200;t++) {
            assertEquals(a.at(t*.05).x(),b.at(t*.05).x(),0);
            assertEquals(a.at(t*.05).y(),b.at(t*.05).y(),0);
        }
    }
    @Test public void continuousColdLaunch() {
        FlightPath p=new FlightPath(1000,0,2200);
        for(double at:new double[]{2,3.2,57.2}) {
            assertTrue(Math.abs(p.at(at+.001).y()-p.at(at-.001).y())<.1);
        }
        assertEquals(22,p.at(2).y(),1e-8);
        assertTrue(!p.at(2.5).thrust());
        assertTrue(p.at(4).thrust());
    }
    @Test public void staging() {
        FlightPath p=new FlightPath(3000,0,2600);
        assertTrue(p.at(23.5).phase()==FlightPath.Phase.SEPARATION);
        assertTrue(p.at(25).stage()==1);
        assertTrue(p.at(42).stage()==2);
        assertTrue(p.at(55).stage()==3);
        assertTrue(p.at(120).phase()==FlightPath.Phase.REENTRY);
    }
    @Test public void craterOrderingAndCoverage() {
        long[] cells=BlastShape.columns(64);
        double prev=-1;
        int center=0;
        for(long k:cells) {
            int x=BlastShape.x(k),z=BlastShape.z(k);
            double d=BlastShape.distance(k);
            assertTrue(d>=prev); prev=d;
            assertTrue(x*x+z*z<=64*64);
            if(x==0&&z==0)center++;
        }
        assertEquals(1,center,0);
        assertTrue(cells.length>12800&&cells.length<13000);
    }
    @Test public void bowlAndNoise() {
        int center=BlastShape.floor(70,0,64,26,0,0,12);
        int rim=BlastShape.floor(70,64,64,26,64,0,12);
        assertTrue(center<rim-20);
        assertEquals(BlastShape.noise(34,-7,123),BlastShape.noise(34,-7,123),0);
    }
}
