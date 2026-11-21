package online.mtradius.mtsleep;

import org.junit.Test;
import java.util.Calendar;
import static org.junit.Assert.*;

public class PlannerTest {
    private long time(int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(year, month - 1, day, hour, minute, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    @Test public void choosesCycleBoundaryTenMinutesBeforeEight() {
        // 00:05 bed + 15 mins to sleep + 5 * 90 mins => 07:50
        Planner.Plan p = Planner.calculate(time(2026, 11, 21, 0, 5),8,0,90,15,45,0);
        assertTrue(p.cycleAligned);
        assertEquals(5, p.completeCycles);
        assertEquals(-10, p.differenceMinutes);
    }

    @Test public void respectsStrictTargetIfBoundaryTooEarly() {
        Planner.Plan p = Planner.calculate(time(2026, 11, 21, 0, 30),8,0,90,15,20,0);
        assertFalse(p.cycleAligned);
        assertEquals(0, p.differenceMinutes);
    }

    @Test public void doesNotSkipNextAlarmIfSleepOnsetIsAfterTarget() {
        Planner.Plan p = Planner.calculate(time(2026, 11, 21, 7, 50),8,0,90,15,45,0);
        Calendar wake = Calendar.getInstance();
        wake.setTimeInMillis(p.wakeMillis);
        assertEquals(21, wake.get(Calendar.DAY_OF_MONTH));
        assertEquals(8, wake.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, p.sleepMinutes);
    }

    @Test public void calculatesAcrossMidnight() {
        Planner.Plan p = Planner.calculate(time(2026, 11, 21, 23, 35),8,0,90,15,45,0);
        assertTrue(p.targetMillis > p.bedMillis);
        Calendar wake = Calendar.getInstance();
        wake.setTimeInMillis(p.wakeMillis);
        assertEquals(22, wake.get(Calendar.DAY_OF_MONTH));
        assertEquals(7, wake.get(Calendar.HOUR_OF_DAY));
        assertEquals(20, wake.get(Calendar.MINUTE));
    }
}
