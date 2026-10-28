package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.LocalDate;

// RN-10: a birth date is never in the future; "today" comes from the clock port in the tenant's zone.
// TODO(DP-07): age bounds and minors are NO DEFINIDO; any positive age is accepted.
public record BirthDateOrAge(LocalDate birthDate, Integer age) {
    public static BirthDateOrAge ofBirthDate(LocalDate birthDate, LocalDate today) {
        if (birthDate == null) throw new DomainException("birthDateOrAge", "birthDateOrAge is required");
        if (today == null) throw new DomainException("today", "today is required");
        if (birthDate.isAfter(today)) throw new DomainException("birthDate", "birthDate must not be in the future");
        return new BirthDateOrAge(birthDate, null);
    }

    public static BirthDateOrAge ofAge(int age) {
        if (age <= 0) throw new DomainException("age", "age must be positive");
        return new BirthDateOrAge(null, age);
    }
}
