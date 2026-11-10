package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.util.List;

// One page of the name search; next is the opaque cursor of the following page, null on the last.
public record CustomerSearchPage(List<Customer> customers, String next) {}
