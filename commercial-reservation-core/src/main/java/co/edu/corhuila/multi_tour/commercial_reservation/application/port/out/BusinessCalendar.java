package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.time.Instant;

// HU-COM-016 RN-07, DP-03: the five business days of the execution due instant. The holidays come from the country of the tenant (domain 01) and have no
// source yet, so the only adapter counts Monday to Friday (declared debt DD-6). The caller never sends an instant.
public interface BusinessCalendar {

    Instant addBusinessDays(Instant from, int days);
}
