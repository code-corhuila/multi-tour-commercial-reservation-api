package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

// T-02 of HU-COM-016 (-api): RN-07, AC-16.11; DP-03 (PROPOSED default). Five business days skip Saturday and Sunday and keep the time of day; the
// holidays have no source yet (declared debt DD-6), so this adapter counts Monday to Friday only.
class WeekdayBusinessCalendarTest {

    private final WeekdayBusinessCalendar calendar = new WeekdayBusinessCalendar();

    @Test
    void fiveBusinessDaysFromAMondayAreTheNextMonday() {
        assertThat(calendar.addBusinessDays(Instant.parse("2026-11-09T10:00:00Z"), 5)).isEqualTo(Instant.parse("2026-11-16T10:00:00Z"));
    }

    @Test
    void theWeekendIsSkipped() {
        assertThat(calendar.addBusinessDays(Instant.parse("2026-11-12T15:30:00Z"), 5)).isEqualTo(Instant.parse("2026-11-19T15:30:00Z"));
        assertThat(calendar.addBusinessDays(Instant.parse("2026-11-13T08:00:00Z"), 1)).isEqualTo(Instant.parse("2026-11-16T08:00:00Z"));
    }

    @Test
    void aStartOnTheWeekendCountsFromTheNextWorkingDay() {
        assertThat(calendar.addBusinessDays(Instant.parse("2026-11-14T10:00:00Z"), 1)).isEqualTo(Instant.parse("2026-11-16T10:00:00Z"));
        assertThat(calendar.addBusinessDays(Instant.parse("2026-11-15T10:00:00Z"), 5)).isEqualTo(Instant.parse("2026-11-20T10:00:00Z"));
    }

    @Test
    void zeroDaysChangesNothing() {
        Instant from = Instant.parse("2026-11-14T10:00:00Z");
        assertThat(calendar.addBusinessDays(from, 0)).isEqualTo(from);
    }
}
