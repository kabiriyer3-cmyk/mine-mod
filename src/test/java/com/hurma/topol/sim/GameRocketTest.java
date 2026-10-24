package com.hurma.topol.sim;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GameRocketTest {
    private int flight(double range,boolean change){
        GameRocket.V p=new GameRocket.V(0,1,0),v=new GameRocket.V(0,2,0),target=new GameRocket.V(range,0,0);
        for(int tick=0;tick<240;tick++){
            if(change&&tick==25)target=new GameRocket.V(range,0,40);
            var step=GameRocket.step(p,v,target,tick);
            double f=GameRocket.closestFraction(p,step.position(),target);
            if(p.add(step.position().sub(p).mul(f)).sub(target).length()<1)return tick;
            assertTrue(step.velocity().length()<=22.000001);
            assertTrue(Double.isFinite(step.position().length()));
            assertTrue(step.position().y()<120,"No high-altitude arc");
            p=step.position();v=step.velocity();
        }
        throw new AssertionError("Did not reach game target: "+range);
    }
    @Test public void shortRange(){assertTrue(flight(32,false)<100);}
    @Test public void mediumRange(){assertTrue(flight(128,false)<110);}
    @Test public void fastLongRange(){assertTrue(flight(1000,false)<100);}
    @Test public void liveCorrection(){assertTrue(flight(500,true)<110);}
    @Test public void deterministic(){
        var p=new GameRocket.V(0,0,0);var v=new GameRocket.V(0,2,0);var t=new GameRocket.V(500,0,0);
        for(int age=0;age<100;age++){
            var a=GameRocket.step(p,v,t,age);var b=GameRocket.step(p,v,t,age);
            assertEquals(a.position().x(),b.position().x(),0);p=a.position();v=a.velocity();
        }
    }
    @Test public void sweptArrival(){
        var a=new GameRocket.V(0,0,0);var b=new GameRocket.V(22,0,0);var t=new GameRocket.V(10,0,0);
        assertEquals(10.0/22,GameRocket.closestFraction(a,b,t),1e-10);
    }
    @Test public void finiteOppositeTurn(){
        var s=GameRocket.step(new GameRocket.V(0,50,0),new GameRocket.V(0,10,0),new GameRocket.V(0,0,0),30);
        assertTrue(Double.isFinite(s.velocity().length()));
    }
    @Test public void craterOrder(){
        double previous=-1;int center=0;
        for(long k:BlastShape.columns(24)){
            double d=BlastShape.distance(k);assertTrue(d>=previous);previous=d;
            if(BlastShape.x(k)==0&&BlastShape.z(k)==0)center++;
        }
        assertEquals(1,center,0);
    }
}
