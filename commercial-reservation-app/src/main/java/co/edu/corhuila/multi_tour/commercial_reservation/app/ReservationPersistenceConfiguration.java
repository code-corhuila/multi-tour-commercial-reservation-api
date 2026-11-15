package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcEnabledPaymentOptions;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTransportRateReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EnabledPaymentOptions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FieldCipher;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TransportRateReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// HU-COM-007: the JDBC adapters of the reservation. The repository writes the reservation, its lines, the companions and
// the ReservationCreated event in one transaction that joins the one of the idempotent creation. The reader backs both
// ReservationReader (the snapshot of HU-COM-004) and CreatedReservationReader.
@Configuration
class ReservationPersistenceConfiguration {

    @Bean
    ReservationCreatedOutbox reservationCreatedOutbox(JdbcTemplate jdbc) {
        return new JdbcReservationCreatedOutbox(jdbc);
    }

    @Bean
    JdbcReservationRepository jdbcReservationRepository(JdbcTemplate jdbc, FieldCipher cipher,
            PlatformTransactionManager manager, ReservationCreatedOutbox outbox) {
        return new JdbcReservationRepository(jdbc, cipher, new TransactionTemplate(manager), outbox);
    }

    @Bean
    JdbcReservationReader jdbcReservationReader(JdbcTemplate jdbc, FieldCipher cipher) {
        return new JdbcReservationReader(jdbc, cipher);
    }

    // AC-07.16 and AC-07.17: the list and the detail of the reservations of a tenant, built over the reader of the creation.
    @Bean
    JdbcReservationQueries reservationQueries(JdbcTemplate jdbc, JdbcReservationReader reader) {
        return new JdbcReservationQueries(jdbc, reader);
    }

    // RN-14: the enabled types and media come from payment_condition; DP-05 leaves the default (everything enabled) open.
    @Bean
    EnabledPaymentOptions enabledPaymentOptions(JdbcTemplate jdbc) {
        return new JdbcEnabledPaymentOptions(jdbc);
    }

    // DP-03: the per-person transport fare is the transport_rate_per_person_cents column of the tour in the catalog; NULL answers empty (RN-15).
    @Bean
    TransportRateReader transportRateReader(JdbcTemplate jdbc) {
        return new JdbcTransportRateReader(jdbc);
    }
}
