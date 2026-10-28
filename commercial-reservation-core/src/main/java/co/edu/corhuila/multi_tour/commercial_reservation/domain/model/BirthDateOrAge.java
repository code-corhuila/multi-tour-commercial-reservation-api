package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.LocalDate;

public record BirthDateOrAge(LocalDate birthDate, Integer age) {
    public static BirthDateOrAge ofBirthDate(LocalDate birthDate, LocalDate today) {
        return new BirthDateOrAge(birthDate, null);
    }

    public static BirthDateOrAge ofAge(int age) {
        return new BirthDateOrAge(null, age);
    }
}
