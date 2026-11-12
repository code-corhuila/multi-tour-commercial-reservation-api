package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ReschedulingInternalController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentDeadlineFreezes;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReschedulingQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ConcludeRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.DetermineRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.OpenRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.VoidReschedulingProposals;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EconomicDeadlineFreezer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ConcludeReschedulingUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.OpenReschedulingUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.VoidReschedulingProposalsUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-018: the internal routes of the saga and of job 5. Only this class knows which adapter backs each port; the ports of the reservation stack are injected lazily, as in
// CancellationConfiguration. The freeze of the economic deadlines is the adapter of HU-COM-011, shared with HU-COM-020.
@Configuration
class ReschedulingConfiguration {

    @Bean
    ReschedulingRepository reschedulingRepository(JdbcTemplate jdbc) {
        return new JdbcReschedulingRepository(jdbc);
    }

    @Bean
    ReschedulingQueries reschedulingQueries(JdbcTemplate jdbc) {
        return new JdbcReschedulingQueries(jdbc);
    }

    @Bean
    EconomicDeadlineFreezer economicDeadlineFreezer(JdbcTemplate jdbc) {
        return new JdbcPaymentDeadlineFreezes(jdbc);
    }

    @Bean
    OpenRescheduling openRescheduling(ReschedulingRepository episodes, @Lazy ReservationRepository reservations, @Lazy SalidaCapacityHolder holder, EconomicDeadlineFreezer freezer,
            @Lazy PaymentEventOutbox outbox, @Lazy PaymentTransaction transaction, IdGenerator ids, Clock clock) {
        return new OpenReschedulingUseCase(episodes, reservations, holder, freezer, outbox, transaction, ids, clock);
    }

    @Bean
    VoidReschedulingProposals voidReschedulingProposals(ReschedulingRepository episodes, @Lazy ReservationRepository reservations, @Lazy SalidaCapacityHolder holder, @Lazy DetermineRefund refunds,
            @Lazy PaymentEventOutbox outbox, TenantStatusReader tenants, @Lazy PaymentTransaction transaction, Clock clock) {
        return new VoidReschedulingProposalsUseCase(episodes, reservations, holder, refunds, outbox, tenants, transaction, clock);
    }

    @Bean
    ConcludeRescheduling concludeRescheduling(ReschedulingRepository episodes, @Lazy ReservationRepository reservations, @Lazy SalidaCapacityHolder holder, @Lazy DetermineRefund refunds,
            @Lazy PaymentEventOutbox outbox, @Lazy PaymentTransaction transaction, Clock clock) {
        return new ConcludeReschedulingUseCase(episodes, reservations, holder, refunds, outbox, transaction, clock);
    }

    @Bean
    ReschedulingInternalController reschedulingInternalController(OpenRescheduling opening, VoidReschedulingProposals voiding, ConcludeRescheduling conclusion,
            ReschedulingRepository episodes, ReschedulingQueries queries, Clock clock) {
        return new ReschedulingInternalController(opening, voiding, conclusion, episodes, queries, clock);
    }
}
