package co.edu.corhuila.multi_tour.commercial_reservation.app;

import org.junit.jupiter.api.Test;

class CatalogContractTest {
    @Test void contractExposesNoDeleteOperation() {
        // RN-06 depends on an absence that future contract changes could silently erase.
        CatalogContract.assertNoDeletion();
    }
}
