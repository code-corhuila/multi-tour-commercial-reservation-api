package co.edu.corhuila.multi_tour.commercial_reservation.app;

import java.util.Comparator;
import org.junit.jupiter.api.ClassDescriptor;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.ClassOrdererContext;

/** Puts ContractCoverageTest after every other class and keeps the order of the rest as discovered. */
public class ContractCoverageLastOrderer implements ClassOrderer {
    @Override
    public void orderClasses(ClassOrdererContext context) {
        context.getClassDescriptors().sort(Comparator.comparingInt(ContractCoverageLastOrderer::rank));
    }

    private static int rank(ClassDescriptor descriptor) {
        return descriptor.getTestClass() == ContractCoverageTest.class ? 1 : 0;
    }
}
