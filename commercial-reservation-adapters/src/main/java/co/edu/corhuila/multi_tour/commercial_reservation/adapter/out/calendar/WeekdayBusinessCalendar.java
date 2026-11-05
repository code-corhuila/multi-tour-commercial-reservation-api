package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.calendar;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.BusinessCalendar;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

// HU-COM-016 DP-03 (PROPOSED default), declared debt DD-6: Monday to Friday in UTC, no holidays, because the country of the tenant gives no source yet.
// The time of day is kept; a start on the weekend counts from the next working day.
public class WeekdayBusinessCalendar implements BusinessCalendar {

    @Override
    public Instant addBusinessDays(Instant from, int days) {
        Instant day = from;
        int counted = 0;
        while (counted < days) {
            day = day.plus(Duration.ofDays(1));
            DayOfWeek weekday = day.atOffset(ZoneOffset.UTC).getDayOfWeek();
            if (weekday != DayOfWeek.SATURDAY && weekday != DayOfWeek.SUNDAY) counted++;
        }
        return day;
    }
}
