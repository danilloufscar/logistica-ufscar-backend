# SGLU — Backend

API Spring Boot 4.1.1 / Java 21. Este incremento implementa login, base de usuários, perfis ADMINISTRADOR/OPERADOR e criação controlada do primeiro Administrador.

## Arquitetura

`AuthController → AutenticacaoService → AuthenticationManager → UsuarioDetailsService → UsuarioRepository → PostgreSQL`.

Depois da autenticação, `TokenService` emite o JWT. MVC utiliza controllers HTTP, modelos de domínio e DTOs JSON como representação de saída. Regras ficam nos serviços; persistência nos repositories. Dependências são recebidas pelo construtor. Spring Security cuida de BCrypt, validação do JWT e autorização.

## Executar localmente (PowerShell)

Pré-requisitos: JDK 21 em `JAVA_HOME` e PostgreSQL disponível (ou Docker Compose para iniciar somente o banco). O Maven Wrapper está no repositório.

1. Configure as variáveis no terminal em que iniciará a aplicação. `.env.example` lista todas elas, mas o Spring **não carrega `.env` automaticamente**.

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/sglu'
$env:DB_USERNAME = 'sglu'
$env:DB_PASSWORD = Read-Host 'Senha do banco' -MaskInput
$bytes = [byte[]]::new(32)
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
```

`-MaskInput` requer PowerShell 7; em versões anteriores configure os segredos pelas variáveis de ambiente do sistema. Guarde a chave JWT em local seguro e reutilize-a nos reinícios; substituí-la invalida os tokens anteriores. Não registre segredos no Git.

2. Se usar Docker, execute `docker compose up -d db`. O compose usa `DB_PASSWORD` do ambiente e mantém o banco em volume. Se já existe PostgreSQL, ajuste a conexão para uma base vazia `sglu`.

3. Na primeira execução, configure um Administrador com dados fictícios de desenvolvimento:

```powershell
$env:BOOTSTRAP_ADMIN_ENABLED = 'true'
$env:BOOTSTRAP_ADMIN_NAME = 'Administrador de desenvolvimento'
$env:BOOTSTRAP_ADMIN_EMAIL = 'admin@example.com'
$env:BOOTSTRAP_ADMIN_PASSWORD = Read-Host 'Senha inicial (12+ caracteres)' -MaskInput
.\mvnw.cmd spring-boot:run
```

Flyway cria a tabela; Hibernate valida o esquema. O bootstrap salva somente o hash BCrypt. Se já houver Administrador, preserva seus dados; se o e-mail pertencer a um Operador, falha sem promovê-lo. Execute a primeira inicialização com uma única instância da aplicação. Nos próximos inícios, defina `BOOTSTRAP_ADMIN_ENABLED=false` e remova as variáveis de credenciais iniciais.

## Contrato com o frontend

`POST http://localhost:8080/api/auth/login`, `Content-Type: application/json`:

```json
{"email":"admin@example.com","senha":"sua-senha"}
```

Sucesso: HTTP 200 com `{"token":"<jwt>"}`. JWT HS256 com `sub` (e-mail), `id`, `nome`, `perfil`, `iss`, `iat`, `exp`; expira em uma hora por padrão (`JWT_EXPIRATION=PT1H`). E-mail é normalizado; senha é preservada. BCrypt aceita até 72 bytes UTF-8.

Credenciais incorretas: HTTP 401 com:

```json
{"codigo":"CREDENCIAIS_INVALIDAS","mensagem":"E-mail ou senha incorretos","detalhes":{}}
```

Campos inválidos/JSON inválido retornam 400. Rotas internas recebem `Authorization: Bearer <token>`; token ausente, inválido ou expirado retorna 401, e perfil sem permissão retorna 403. Não são criadas sessões HTTP.

As famílias `/api/usuarios`, `/api/estoque/ajustes` e `/api/relatorios` exigem ADMINISTRADOR. Demais rotas exigem ADMINISTRADOR ou OPERADOR, exceto o POST de login. **Esses cadastros e funcionalidades ainda não estão implementados**: as regras apenas preparam a autorização. O portal público será liberado junto à sua implementação. Swagger também está protegido nesta etapa.

O frontend existente pode usar seu proxy `/api` para o backend em `localhost:8080`; chamadas diretas de outra origem não têm CORS liberado. Tela de login, redirecionamento e logout no front são outra etapa. Logout local remove o token; não há refresh/revogação imediata neste escopo.

## Testes e cobertura

```powershell
.\mvnw.cmd verify
```

Os testes usam H2 em modo PostgreSQL, Flyway e credenciais fictícias exclusivas do profile `test`; não precisam do banco de desenvolvimento. Testam login real com BCrypt, contrato JWT, assinatura, expiração, autorização, validação de entrada e bootstrap idempotente. Testes de serviços usam Mockito. JaCoCo gera `target/site/jacoco/index.html` e exige ao menos 70% de cobertura de linhas no pacote `service`. H2 não substitui a validação de execução com PostgreSQL.

O workflow de CI executa `verify` em push/PR quando estas alterações forem publicadas. Proteção de branch deve ser configurada no GitHub pela equipe.

**Pendência de aderência ao enunciado:** ele menciona JUnit 5. A stack original Spring Boot 4.1.1 utiliza JUnit 6 e Spring Framework 7, que [exige JUnit 6](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes#baseline-upgrades). Mantivemos essa stack; alinhar a versão com o professor ou planejar a migração conjunta da stack, sem forçar JUnit 5 incompatível.
