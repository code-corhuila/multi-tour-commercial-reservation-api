package co.edu.corhuila.multi_tour.commercial_reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociatedEstablishment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class EstablishmentAbsenceTest {
    // RN-02 and RN-07 depend on absences, and an absence rots silently unless a test watches it.
    @Test void noDeleteOperationExistsInTheEntityOrThePort() {
        for (Class<?> surface : surfaces()) {
            assertThat(Arrays.stream(surface.getMethods()).map(method -> method.getName().toLowerCase(Locale.ROOT)))
                    .noneMatch(name -> name.matches("^(delete|remove|purge|erase|destroy).*"));
        }
    }

    @Test void theOperationHasNoFieldForRoomDishMenuSeatRateOrAvailability() {
        for (Class<?> surface : surfaces()) inspectFields(surface);
    }

    private void inspectFields(Class<?> surface) {
        assertThat(Arrays.stream(surface.getDeclaredFields()).map(field -> field.getName().toLowerCase(Locale.ROOT)))
                .noneMatch(name -> name.matches(".*(room|dish|menu|seat|rate|availability).*"));
        assertThat(Arrays.stream(surface.getDeclaredMethods()).map(method -> method.getName().toLowerCase(Locale.ROOT)))
                .noneMatch(name -> name.matches(".*(room|dish|menu|seat|rate|availability).*"));
        for (Class<?> nested : surface.getDeclaredClasses()) inspectFields(nested);
    }

    private List<Class<?>> surfaces() {
        var types = new ArrayList<Class<?>>(List.of(AssociatedEstablishment.class, AssociatedEstablishmentRepository.class));
        Path directory = Path.of("target/classes/co/edu/corhuila/multi_tour/commercial_reservation/application/port/in");
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith("AssociatedEstablishment.class")
                    || path.getFileName().toString().equals("ListAssociatedEstablishments.class")).toList()) {
                types.add(Class.forName("co.edu.corhuila.multi_tour.commercial_reservation.application.port.in."
                        + file.getFileName().toString().replace(".class", "")));
            }
        } catch (Exception error) { throw new AssertionError("Input ports must be inspectable", error); }
        return types;
    }
}
