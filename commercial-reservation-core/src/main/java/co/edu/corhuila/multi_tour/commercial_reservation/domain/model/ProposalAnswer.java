package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

// HU-COM-018 RN-05, AC-18.6, GAP-008: the answer to a proposal. The customer answers with nothing else than accept or reject; an answer received by an outside channel carries its channel (one of
// four), its evidence (1 to 500 characters after trimming, free text that is never logged nor evented) and the instant it was given. answeredAt is null for the customer's own answer: the
// episode stamps it with the instant of the call. recordedBy is the account of whoever answered or recorded it (DP-D18-4).
public record ProposalAnswer(boolean accepted, Source source, String channel, String evidence, Instant answeredAt, UUID recordedBy) {

    public enum Source { CUSTOMER, OUTSIDE_CHANNEL }

    private static final Set<String> CHANNELS = Set.of("EMAIL", "PHONE", "WHATSAPP", "IN_PERSON");

    public ProposalAnswer {
        if (source == null || recordedBy == null) throw new DomainException("answer", "the source and the author of the answer are required");
        if (source == Source.OUTSIDE_CHANNEL) {
            if (channel == null || !CHANNELS.contains(channel)) throw new DomainException("channel", "the channel must be EMAIL, PHONE, WHATSAPP or IN_PERSON");
            if (evidence == null || evidence.isBlank() || evidence.length() > 500) throw new DomainException("evidence", "the evidence is required and has at most 500 characters");
            if (answeredAt == null) throw new DomainException("answeredAt", "the instant of the answer is required");
        } else if (channel != null || evidence != null) {
            throw new DomainException("answer", "the answer of the customer carries no channel and no evidence");
        }
    }

    public static ProposalAnswer byCustomer(boolean accepted, UUID customerAccount) {
        return new ProposalAnswer(accepted, Source.CUSTOMER, null, null, null, customerAccount);
    }

    public static ProposalAnswer outside(boolean accepted, UUID recordedBy, String channel, String evidence, Instant answeredAt) {
        return new ProposalAnswer(accepted, Source.OUTSIDE_CHANNEL, channel, evidence == null ? null : evidence.trim(), answeredAt, recordedBy);
    }

    ProposalAnswer stampedAt(Instant now) {
        return answeredAt != null ? this : new ProposalAnswer(accepted, source, channel, evidence, now, recordedBy);
    }
}
