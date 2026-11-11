package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingRecord;

// HU-COM-018 plan D-04: appends the record of an accepted rescheduling; never updated, never deleted.
public interface ReschedulingRecords {

    void save(ReschedulingRecord record);
}
