package br.com.sglu;

import br.com.sglu.model.Perfil;
import br.com.sglu.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sglu-bootstrap;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "sglu.bootstrap.admin.enabled=true",
        "sglu.bootstrap.admin.nome=Administrador inicial",
        "sglu.bootstrap.admin.email=inicial@example.com",
        "sglu.bootstrap.admin.senha=senha-ficticia-teste"
})
@ActiveProfiles("test")
class SgluApplicationTests {

	@Autowired UsuarioRepository users;
	@Autowired PasswordEncoder passwords;

	@Test
	void inicializacaoCriaAdministradorComBCrypt() {
		var admin = users.findByEmail("inicial@example.com").orElseThrow();
		assertThat(admin.getPerfil()).isEqualTo(Perfil.ADMINISTRADOR);
		assertThat(admin.getNome()).isEqualTo("Administrador inicial");
		assertThat(passwords.matches("senha-ficticia-teste", admin.getSenhaHash())).isTrue();
	}

}
