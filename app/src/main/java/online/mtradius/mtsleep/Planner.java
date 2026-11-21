package online.mtradius.mtsleep;

import java.util.Calendar;

public final class Planner {
    private Planner() {}
    public static final long MINUTE = 60000L;

    public static final class Plan {
        public final long bedMillis;
        public final long asleepMillis;
        public final long targetMillis;
        public final long wakeMillis;
        public final int completeCycles;
        public final int sleepMinutes;
        public final int differenceMinutes;
        public final boolean cycleAligned;
        public Plan(long bed, long asleep, long target, long wake, int cycles, int minutes, boolean aligned) {
            bedMillis = bed;
            asleepMillis = asleep;
            targetMillis = target;
            wakeMillis = wake;
            completeCycles = cycles;
            sleepMinutes = Math.max(0, (int)((wake - asleep) / MINUTE));
            differenceMinutes = (int)((wake - target) / MINUTE);
            cycleAligned = aligned;
        }
    }

    public static long nextTarget(long after, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(after);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= after) c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    /** The suggested alarm must never exceed the target, unless allowedLateMin > 0.
     * Default settings: 90 minute cycle, 15 minute onset, 45 minute early window.
     * A target fallback is returned if no plausible boundary matches the window.
     */
    public static Plan calculate(long bedMillis, int targetHour, int targetMinute,
                                 int cycleMinutes, int onsetMinutes,
                                 int maxEarlyMinutes, int allowedLateMinutes) {
        if (cycleMinutes < 60 || cycleMinutes > 150 || onsetMinutes < 0 || onsetMinutes > 120)
            throw new IllegalArgumentException("Invalid cycle or onset");
        long asleep = bedMillis + onsetMinutes * MINUTE;
        long target = nextTarget(bedMillis, targetHour, targetMinute);
        // Keep the nearest upcoming requested alarm even when sleep onset is later;
        // never silently skip an appointment by moving the alarm to tomorrow.
        long chosen = target;
        int chosenCycles = Math.max(0, (int)((target - asleep) / (cycleMinutes * MINUTE)));
        boolean aligned = false;
        long bestAbs = Long.MAX_VALUE;
        for (int n = 1; n <= 10; n++) {
            long candidate = asleep + (long)n * cycleMinutes * MINUTE;
            long diff = candidate - target;
            if (diff < -(long)maxEarlyMinutes * MINUTE || diff > (long)allowedLateMinutes * MINUTE) continue;
            long abs = Math.abs(diff);
            if (abs < bestAbs || (abs == bestAbs && candidate < chosen)) {
                chosen = candidate;
                chosenCycles = n;
                aligned = true;
                bestAbs = abs;
            }
        }
        return new Plan(bedMillis, asleep, target, chosen, chosenCycles, 0, aligned);
    }

    public static long suggestBedtime(long now, int targetHour, int targetMinute,
                                      int cycles, int cycleMinutes, int onsetMinutes) {
        long target = nextTarget(now, targetHour, targetMinute);
        return target - (long)(cycles * cycleMinutes + onsetMinutes) * MINUTE;
    }
}
