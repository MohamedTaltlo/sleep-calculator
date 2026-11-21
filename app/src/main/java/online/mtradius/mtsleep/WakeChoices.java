package online.mtradius.mtsleep;

/**
 * Pure arithmetic planner. The user is assumed to go to bed NOW.
 * The fifteen-minute sleep-onset buffer and 90-minute cycle are personal
 * defaults, not measurements of the user's physiological sleep stages.
 */
public final class WakeChoices {
    public static final int BEFORE = 0;
    public static final int EXACT = 1;
    public static final int AFTER = 2;
    public static final int ONSET_MINUTES = 15;
    public static final int CYCLE_MINUTES = 90;
    private static final long MINUTE = 60000L;

    private WakeChoices() {}

    public static final class Options {
        public final long startedAt, asleepAt, targetAt, beforeAt, afterAt;
        public final int beforeCycles, afterCycles, recommended;
        Options(long start,long asleep,long target,long before,long after,
                int beforeCount,int afterCount,int suggested) {
            startedAt=start; asleepAt=asleep; targetAt=target;
            beforeAt=before; afterAt=after;
            beforeCycles=beforeCount; afterCycles=afterCount; recommended=suggested;
        }
        public long timeFor(int mode) {
            if(mode==BEFORE && beforeAt>0)return beforeAt;
            if(mode==AFTER)return afterAt;
            return targetAt;
        }
        public int cyclesFor(int mode) {
            if(mode==BEFORE)return beforeCycles;
            if(mode==AFTER)return afterCycles;
            return Math.max(0,(int)((targetAt-asleepAt)/(CYCLE_MINUTES*MINUTE)));
        }
        public int sleepMinutesFor(int mode) {
            return Math.max(0,(int)((timeFor(mode)-asleepAt)/MINUTE));
        }
        public int offsetMinutes(int mode){
            return (int)((timeFor(mode)-targetAt)/MINUTE);
        }
    }

    public static Options plan(long now,int targetHour,int targetMinute) {
        if(targetHour<0||targetHour>23||targetMinute<0||targetMinute>59)
            throw new IllegalArgumentException("Invalid clock time");
        long target=Planner.nextTarget(now,targetHour,targetMinute);
        long asleep=now+ONSET_MINUTES*MINUTE;
        long cycle=CYCLE_MINUTES*MINUTE;

        long last=(target-asleep)/cycle;
        int beforeCount=(int)Math.max(0,last);
        long before=beforeCount>=1?asleep+beforeCount*cycle:-1;
        if(before>=target){
            beforeCount--;
            before=beforeCount>=1?asleep+beforeCount*cycle:-1;
        }
        if(before<=now){before=-1;beforeCount=0;}

        int afterCount=(int)Math.max(1,last+1);
        long after=asleep+afterCount*cycle;
        if(after<=target){afterCount++;after+=cycle;}

        int recommended=EXACT;
        long early=before>0?target-before:Long.MAX_VALUE;
        long late=after-target;
        // Being on time is safer than unexpectedly missing an appointment.
        // An early boundary within 45 min is preferable; a late boundary
        // within 20 min is suggested only with its delay clearly visible.
        if(early<=45*MINUTE)recommended=BEFORE;
        else if(late<=20*MINUTE)recommended=AFTER;
        return new Options(now,asleep,target,before,after,beforeCount,afterCount,recommended);
    }
}
