package online.mtradius.mtsleep;

import org.junit.Test;
import java.util.Calendar;
import static org.junit.Assert.*;

public final class WakeChoicesTest {
    private static long stamp(int day,int hour,int minute){
        Calendar c=Calendar.getInstance();
        c.set(2026,Calendar.NOVEMBER,day,hour,minute,0);
        c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
    }
    @Test public void beforeAtSevenFiftyForEight(){
        WakeChoices.Options o=WakeChoices.plan(stamp(21,0,5),8,0);
        assertEquals(stamp(21,7,50),o.beforeAt);
        assertEquals(stamp(21,9,20),o.afterAt);
        assertEquals(WakeChoices.BEFORE,o.recommended);
        assertEquals(5,o.beforeCycles);
        assertEquals(-10,o.offsetMinutes(WakeChoices.BEFORE));
    }
    @Test public void afterIsSuggestedOnlyWhenClose(){
        WakeChoices.Options o=WakeChoices.plan(stamp(21,23,0),8,0);
        assertEquals(stamp(22,8,15),o.afterAt);
        assertEquals(WakeChoices.AFTER,o.recommended);
        assertEquals(15,o.offsetMinutes(WakeChoices.AFTER));
    }
    @Test public void cannotProposePastEarlyBoundary(){
        WakeChoices.Options o=WakeChoices.plan(stamp(21,7,50),8,0);
        assertEquals(-1,o.beforeAt);
        assertEquals(WakeChoices.EXACT,o.recommended);
        assertEquals(stamp(21,8,0),o.timeFor(o.recommended));
    }
    @Test public void exactMatchGivesDistinctChoices(){
        WakeChoices.Options o=WakeChoices.plan(stamp(21,0,15),8,0);
        // 00:30 + 5 cycles = 08:00, avoid a duplicate "before" button.
        assertEquals(stamp(21,6,30),o.beforeAt);
        assertEquals(stamp(21,9,30),o.afterAt);
        assertEquals(stamp(21,8,0),o.targetAt);
    }
}
