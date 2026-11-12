package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.IdempotentReschedulingWrites;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ReschedulingHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ReschedulingWriteController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentDeadlineFreezes;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReschedulingRecords;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.AnswerReschedulingProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ConcludeRescheduling;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RespondToReschedulingProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.SendReschedulingProposal;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.AcceptReschedulingProposalUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RespondToReschedulingProposalUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.SendReschedulingProposalUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// HU-COM-018: the public writes of the rescheduling episodes over the use cases and the JDBC adapters already merged (the episode, the record of the accepted rescheduling, the resumption of the
// frozen deadlines). The ReschedulingRepository and the ConcludeRescheduling of the internal routes are reused. The role gate runs before the body and before any load (AC-18.15).
@Configuration
class ReschedulingWriteConfiguration {

    @Bean
    SendReschedulingProposal sendReschedulingProposal(ReschedulingRepository episodes, @Lazy ReservationRepository reservations, @Lazy DepartureReader departures,
            @Lazy SalidaCapacityHolder holder, @Lazy PaymentTransaction transaction, IdGenerator ids, Clock clock) {
        return new SendReschedulingProposalUseCase(episodes, reservations, departures, holder, transaction, ids, clock);
    }

    @Bean
    AnswerReschedulingProposal answerReschedulingProposal(ReschedulingRepository episodes, @Lazy ReservationRepository reservations, @Lazy DepartureReader departures,
            @Lazy SalidaCapacityHolder holder, JdbcTemplate jdbc, @Lazy PaymentEventOutbox outbox, @Lazy PaymentTransaction transaction, IdGenerator ids, Clock clock) {
        return new AcceptReschedulingProposalUseCase(episodes, reservations, departures, holder, new JdbcPaymentDeadlineFreezes(jdbc), new JdbcReschedulingRecords(jdbc), outbox,
                transaction, ids, clock);
    }

    @Bean
    RespondToReschedulingProposal respondToReschedulingProposal(ReschedulingRepository episodes, @Lazy ReservationRepository reservations, @Lazy CustomerByAccount accounts,
            AnswerReschedulingProposal acceptance, ConcludeRescheduling conclusion) {
        return new RespondToReschedulingProposalUseCase(episodes, reservations, accounts, acceptance, conclusion);
    }

    @Bean
    IdempotentReschedulingWrites idempotentReschedulingWrites(SendReschedulingProposal proposals, RespondToReschedulingProposal responses, IdempotencyKeys keys,
            PlatformTransactionManager manager) {
        return new IdempotentReschedulingWrites(proposals, responses, keys, new TransactionTemplate(manager));
    }

    @Bean
    ReschedulingWriteController reschedulingWriteController(IdempotentReschedulingWrites writes, ConcludeRescheduling conclusion) {
        return new ReschedulingWriteController(writes, conclusion);
    }

    @Bean
    WebMvcConfigurer reschedulingAccess() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new ReschedulingHttpAccess()).addPathPatterns("/api/v1/commercial-reservation/reschedulings/**");
            }
        };
    }
}
