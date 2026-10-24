package com.hurma.topol.sim;

/**
 * Deterministic cinematic GAME trajectory in blocks and seconds, not a real missile model.
 * Art-directed Hermite ascent, a smooth downrange track and a steep final dive.
 * No real propulsion, guidance, targeting, yield or weapon performance data.
 */
public final class FlightPath {
    public static final double EXIT_TIME = 2.0, IGNITION_TIME = 3.2;
    public static final double STAGE_1_END = 23.2, STAGE_2_START = 24.2,
            STAGE_2_END = 39.2, STAGE_3_START = 40.2, STAGE_3_END = 50.2;
    public enum Phase { TUBE, RISE, BURN, SEPARATION, COAST, REENTRY, IMPACT }
    public record Sample(double x, double y, double side, double vx, double vy, double vs,
                         int stage, Phase phase, boolean thrust) {}
    private final double range, endHeight, duration, peak, apex;

    public FlightPath(double range, double endHeight, int durationTicks) {
        this.range = Math.max(1, range);
        this.endHeight = endHeight;
        this.duration = Math.max(80, durationTicks / 20.0);
        this.peak = Math.max(380, Math.min(1800, 380 + range * .12)) + Math.max(0, endHeight);
        this.apex = duration * .52;
    }

    public static int durationTicks(double range) {
        return (int) Math.round(Math.max(100, Math.min(190, 90 + Math.sqrt(Math.max(0, range)) * .6)) * 20);
    }

    public Sample at(double time) {
        double t = clamp(time, 0, duration);
        double[] p = position(t);
        double ta = Math.max(0, t - .01), tb = Math.min(duration, t + .01);
        double[] a = position(ta), b = position(tb);
        double dt = Math.max(.001, tb - ta);
        int stage = t < STAGE_1_END ? 0 : t < STAGE_2_END ? 1 : t < STAGE_3_END ? 2 : 3;
        Phase phase;
        if (t >= duration) phase = Phase.IMPACT;
        else if (t < EXIT_TIME) phase = Phase.TUBE;
        else if (t < IGNITION_TIME) phase = Phase.RISE;
        else if (t < STAGE_1_END || t >= STAGE_2_START && t < STAGE_2_END ||
                t >= STAGE_3_START && t < STAGE_3_END) phase = Phase.BURN;
        else if (t < STAGE_3_START) phase = Phase.SEPARATION;
        else if (t > duration - 24) phase = Phase.REENTRY;
        else phase = Phase.COAST;
        return new Sample(p[0], p[1], p[2], (b[0]-a[0])/dt, (b[1]-a[1])/dt,
                (b[2]-a[2])/dt, stage, phase, phase == Phase.BURN);
    }

    private double[] position(double t) {
        if (t <= EXIT_TIME) return new double[]{0, 5.5*t*t, 0};
        if (t <= IGNITION_TIME) {
            double q = t - EXIT_TIME;
            return new double[]{0, 22 + 22*q - 4.5*q*q, 0};
        }
        double u = clamp((t - IGNITION_TIME) / (duration - IGNITION_TIME), 0, 1);
        double progress = smooth(u);
        double y;
        if (t <= apex) {
            double len = apex - IGNITION_TIME, v = (t - IGNITION_TIME) / len;
            double v2 = v*v, v3 = v2*v;
            y = (2*v3-3*v2+1)*41.92 + (v3-2*v2+v)*len*11.2 + (-2*v3+3*v2)*peak;
        } else {
            double v = (t - apex) / (duration - apex);
            y = peak * (1 - Math.pow(v, 8));
        }
        // A small, purely visual sidestep; it returns to zero at impact.
        double side = 3.0 * Math.sin(Math.PI*u) * smooth(Math.min(1, (t-IGNITION_TIME)/8));
        return new double[]{range * progress, y + endHeight * progress, side};
    }

    public static double smooth(double x) { x = clamp(x,0,1); return x*x*(3-2*x); }
    public static double clamp(double x, double lo, double hi) { return Math.max(lo, Math.min(hi,x)); }
}
