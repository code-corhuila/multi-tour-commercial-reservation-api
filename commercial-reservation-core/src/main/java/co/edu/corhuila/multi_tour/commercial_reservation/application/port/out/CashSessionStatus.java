package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// Whether the tenant has an open cash session (RN-05, ADR-004 D3). The adapter will call the cash domain; until HU-CSH-001
// exists it is a double (DP-02, declared debt DD-2).
public interface CashSessionStatus {

    boolean isOpen(UUID tenantId);
}
