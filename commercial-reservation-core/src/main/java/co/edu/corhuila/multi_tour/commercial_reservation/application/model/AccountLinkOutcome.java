package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

// LINKED: the record now carries the account. NOTHING_TO_LINK: no free record of the tenant has that e-mail, or the account already
// has one; the link then waits for the first reservation (ADR-004 D5). ALREADY_PROCESSED: a repeat of the event id.
public enum AccountLinkOutcome {
    LINKED, NOTHING_TO_LINK, ALREADY_PROCESSED
}
