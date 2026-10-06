package cl.bancoxyz.bff.movil.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * ETag: si los datos no cambiaron desde la última consulta, la app recibe un 304 sin cuerpo
 * (ahorra ancho de banda y batería). Junto con gzip (server.compression) reduce mucho el tráfico.
 */
@Configuration
public class OptimizacionMovilConfig {

    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registro = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registro.addUrlPatterns("/movil/*");
        registro.setName("etagFilter");
        return registro;
    }
}
