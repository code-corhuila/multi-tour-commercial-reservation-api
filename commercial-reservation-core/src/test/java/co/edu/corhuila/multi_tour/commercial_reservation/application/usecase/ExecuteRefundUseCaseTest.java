package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ExecutionState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.OutflowMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-05 of HU-COM-016 (-api): AC-16.4 to AC-16.6, AC-16.19; plan D-06. The steps of the saga, idempotent by state: start (step 1), undo (the compensation)
// and completion (step 3). The service never calls Cash; it records the person, the method and the movement it is given. A repeat changes nothing and
// writes nothing; another tenant looks missing.
class ExecuteRefundUseCaseTest {

    private final RefundFakes fakes = new RefundFakes();
    private final ExecuteRefundUseCase execute = new ExecuteRefundUseCase(fakes.refundRepository, fakes.transaction, RefundFakes.CLOCK);
    private final UUID collaborator = UUID.randomUUID();
    private final UUID movement = UUID.randomUUID();

    private RefundAuthorization authorized() {
        RefundAuthorization refund = RefundAuthorization.determine(fakes.ids.newId(), RefundFakes.TENANT, UUID.randomUUID(),
                RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, new Cents(100_000), RefundFakes.NOW, RefundFakes.NOW.plusSeconds(3_600));
        fakes.refunds.put(refund.id(), refund);
        return refund;
    }

    @Test
    void startingRecordsThePersonTheMethodAndTheInstantOfTheClock() {
        RefundAuthorization refund = authorized();

        RefundAuthorization started = execute.start(RefundFakes.TENANT, refund.id(), collaborator, OutflowMethod.TRANSFER);

        assertThat(started.state()).isEqualTo(ExecutionState.EXECUTING);
        assertThat(started.executedBy()).isEqualTo(collaborator);
        assertThat(started.executingAt()).isEqualTo(RefundFakes.NOW);
        assertThat(fakes.refunds.get(refund.id())).isEqualTo(started);
    }

    @Test
    void aRepeatedStepChangesNothingAndWritesNothing() {
        RefundAuthorization refund = authorized();
        execute.start(RefundFakes.TENANT, refund.id(), collaborator, OutflowMethod.CASH);
        int writes = fakes.transactions;

        execute.start(RefundFakes.TENANT, refund.id(), collaborator, OutflowMethod.CASH);
        execute.undo(RefundFakes.TENANT, authorized().id());

        assertThat(fakes.transactions).isEqualTo(writes);
        assertThatThrownBy(() -> execute.start(RefundFakes.TENANT, refund.id(), UUID.randomUUID(), OutflowMethod.CASH))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void undoingTheStartLeavesTheRefundAuthorizedAndAnExecutedOneCannotBeUndone() {
        RefundAuthorization refund = authorized();
        execute.start(RefundFakes.TENANT, refund.id(), collaborator, OutflowMethod.CASH);

        assertThat(execute.undo(RefundFakes.TENANT, refund.id())).isEqualTo(refund);

        execute.start(RefundFakes.TENANT, refund.id(), collaborator, OutflowMethod.CASH);
        execute.complete(RefundFakes.TENANT, refund.id(), movement);
        assertThatThrownBy(() -> execute.undo(RefundFakes.TENANT, refund.id())).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void completingNeedsTheMovementAndIsTerminalWithTheSameOneRepeated() {
        RefundAuthorization refund = authorized();
        assertThatThrownBy(() -> execute.complete(RefundFakes.TENANT, refund.id(), movement)).isInstanceOf(InvalidStatusTransitionException.class);
        execute.start(RefundFakes.TENANT, refund.id(), collaborator, OutflowMethod.TRANSFER);

        RefundAuthorization done = execute.complete(RefundFakes.TENANT, refund.id(), movement);

        assertThat(done.state()).isEqualTo(ExecutionState.EXECUTED);
        assertThat(done.cashMovementId()).isEqualTo(movement);
        assertThat(execute.complete(RefundFakes.TENANT, refund.id(), movement)).isEqualTo(done);
        assertThatThrownBy(() -> execute.complete(RefundFakes.TENANT, refund.id(), UUID.randomUUID())).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void aRefundOfAnotherTenantLooksMissing() {
        RefundAuthorization refund = authorized();

        assertThatThrownBy(() -> execute.start(UUID.randomUUID(), refund.id(), collaborator, OutflowMethod.CASH)).isInstanceOf(RefundNotFoundException.class);
        assertThatThrownBy(() -> execute.find(UUID.randomUUID(), refund.id())).isInstanceOf(RefundNotFoundException.class);
        assertThat(execute.find(RefundFakes.TENANT, refund.id())).isEqualTo(refund);
    }
}
