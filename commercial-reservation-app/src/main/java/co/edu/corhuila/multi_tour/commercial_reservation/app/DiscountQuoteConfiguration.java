package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.DiscountQuoteController;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.DiscountQuoteHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.EvaluateApplicableDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteDiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.QuoteTourDiscounts;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.EvaluateApplicableDiscountRulesUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.QuoteDiscountBreakdownUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.QuoteTourDiscountsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// HU-COM-004: the quote by tour and date over the rules of HU-COM-003 (stateless, no reservation).
@Configuration
class DiscountQuoteConfiguration {
    @Bean EvaluateApplicableDiscountRules evaluateApplicableDiscountRules(DiscountRuleRepository rules) {
        return new EvaluateApplicableDiscountRulesUseCase(rules);
    }

    @Bean QuoteDiscountBreakdown quoteDiscountBreakdown(EvaluateApplicableDiscountRules evaluation) {
        return new QuoteDiscountBreakdownUseCase(evaluation);
    }

    @Bean QuoteTourDiscounts quoteTourDiscounts(CatalogServiceRepository catalog, QuoteDiscountBreakdown breakdown) {
        return new QuoteTourDiscountsUseCase(catalog, breakdown);
    }

    @Bean DiscountQuoteController discountQuoteController(QuoteTourDiscounts quotes) {
        return new DiscountQuoteController(quotes);
    }

    @Bean WebMvcConfigurer discountQuoteAccess() {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new DiscountQuoteHttpAccess())
                        .addPathPatterns("/api/v1/commercial-reservation/discount-quotes");
            }
        };
    }
}
