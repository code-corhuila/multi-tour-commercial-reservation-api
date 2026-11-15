package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.util.LinkedHashMap;

// HU-COM-018 RN-12, AC-18.19, RNF-03: a request body that carries free text (the evidence and the channel text of an outside answer) is read into a map, and the web framework prints what it
// read when its logger is at DEBUG. This map prints only the names of its fields, so the text never reaches a log line; the values are read as with any other map.
public final class RedactedBody extends LinkedHashMap<String, Object> {
    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
        return "RedactedBody" + keySet();
    }
}
