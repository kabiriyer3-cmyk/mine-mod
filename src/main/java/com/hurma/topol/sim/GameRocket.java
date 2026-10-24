package com.hurma.topol.sim;

/**
 * Arcade projectile, in blocks/tick. Intentionally not a real-world guidance or propulsion model.
 * Short vertical clearance, then a bounded-rate turn towards a game cursor.
 */
public final class GameRocket {
    public record V(double x,double y,double z) {
        public V add(V b){return new V(x+b.x,y+b.y,z+b.z);}
        public V sub(V b){return new V(x-b.x,y-b.y,z-b.z);}
        public V mul(double m){return new V(x*m,y*m,z*m);}
        public double dot(V b){return x*b.x+y*b.y+z*b.z;}
        public double length(){return Math.sqrt(dot(this));}
        public V unit(){double n=length();return n<1e-9?new V(0,1,0):mul(1/n);}
    }
    public record Step(V position,V velocity){}
    public static final int CLEARANCE_TICKS=6, MAX_TICKS=240;
    public static final double MAX_SPEED=22,TURN=.24;
    private GameRocket(){}
    public static Step step(V pos,V velocity,V target,int age) {
        double speed=Math.min(MAX_SPEED,2+Math.max(0,age-3)*.65);
        if(age>=CLEARANCE_TICKS)speed=Math.min(speed,Math.max(1.2,target.sub(pos).length()*.18));
        V direction;
        if(age<CLEARANCE_TICKS)direction=new V(0,1,0);
        else {
            V old=velocity.unit(),goal=target.sub(pos).unit();
            double angle=Math.acos(Math.max(-1,Math.min(1,old.dot(goal))));
            if(angle<TURN)direction=goal;
            else {
                V side=goal.sub(old.mul(old.dot(goal)));
                if(side.length()<1e-7)side=Math.abs(old.y)<.9?new V(0,1,0):new V(1,0,0);
                side=side.sub(old.mul(side.dot(old))).unit();
                direction=old.mul(Math.cos(TURN)).add(side.mul(Math.sin(TURN))).unit();
            }
        }
        V v=direction.mul(speed);
        return new Step(pos.add(v),v);
    }
    /** Swept segment proximity, also used for collision-independent arrival at an airborne aim point. */
    public static double closestFraction(V a,V b,V point) {
        V d=b.sub(a);double n=d.dot(d);
        return n<1e-10?0:Math.max(0,Math.min(1,point.sub(a).dot(d)/n));
    }
}
