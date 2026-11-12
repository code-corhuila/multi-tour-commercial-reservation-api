package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CashSessionStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// The income recorder is its own object: if it were also a CashSessionStatus, Spring would see two beans of that type
// and the real adapter of the cash session could not be told apart (found by the HTTP test of the payments).
class UnverifiedCashIncomeTest {

    private static final UUID ID = UUID.fromString("6f1c1c64-0000-4000-8000-000000000001");

    @Test
    void theIncomeStaysRefusedWithAnExplicitMessage() {
        UnverifiedCashIncome income = new UnverifiedCashIncome();

        assertThatThrownBy(() -> income.record(ID, ID, "k", null, ID)).hasMessageContaining("cannot be recorded");
        assertThatThrownBy(() -> income.reverse(ID, ID)).hasMessageContaining("cannot be reversed");
    }

    @Test
    void itIsNotACashSessionStatus() {
        assertThat(new UnverifiedCashIncome()).isNotInstanceOf(CashSessionStatus.class);
    }
}
