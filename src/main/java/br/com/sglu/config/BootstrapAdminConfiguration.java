package br.com.sglu.config;

import br.com.sglu.service.AdministradorInicialService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "sglu.bootstrap.admin", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class BootstrapAdminConfiguration {

    @Bean
    ApplicationRunner initializeAdministrator(AdministradorInicialService service, BootstrapAdminProperties properties) {
        return args -> service.inicializar(properties.nome(), properties.email(), properties.senha());
    }
}
