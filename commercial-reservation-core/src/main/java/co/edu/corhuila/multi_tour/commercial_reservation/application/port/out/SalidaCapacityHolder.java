package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// One conditional update of the capacity row, held + requested within the effective capacity (ADR-004 D4, plan D-05):
// never a read followed by a write. False means no availability.
public interface SalidaCapacityHolder {

    boolean hold(UUID tenantId, UUID salidaId, int requested);
}
