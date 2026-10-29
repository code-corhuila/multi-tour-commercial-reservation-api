package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

// The contract stores the e-mail trimmed and lower-cased (RegisterCustomerRequest), so two spellings are one.
class ContactInfoTest {
    @Test
    void theEmailIsTrimmedAndLowerCased() {
        assertEquals("persona.uno@example.invalid", new ContactInfo("  Persona.UNO@Example.invalid ", "3000000000").email());
    }

    @Test
    void anAbsentEmailStaysAbsent() {
        assertNull(new ContactInfo(null, "3000000000").email());
    }
}
