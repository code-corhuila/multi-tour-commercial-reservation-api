package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ClientNoticeController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcClientNoticeReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadClientNoticeContent;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ClientNoticeReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerProfileReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ReadClientNoticeContentUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-015: the content route of the client notices for job 4.
@Configuration
class ClientNoticeConfiguration {

    @Bean
    ClientNoticeReader clientNoticeReader(JdbcTemplate jdbc) {
        return new JdbcClientNoticeReader(jdbc);
    }

    @Bean
    ReadClientNoticeContent readClientNoticeContent(ClientNoticeReader notices, CustomerProfileReader customers) {
        return new ReadClientNoticeContentUseCase(notices, customers);
    }

    @Bean
    ClientNoticeController clientNoticeController(ReadClientNoticeContent read) {
        return new ClientNoticeController(read);
    }
}
