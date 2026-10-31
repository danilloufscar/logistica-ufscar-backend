# SGLU — Backend

API Spring Boot 4.1.1 / Java 21. Implementa login, cadastro/listagem de usuários, perfis ADMINISTRADOR/OPERADOR e criação controlada do primeiro Administrador.

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

As famílias `/api/usuarios`, `/api/estoque/ajustes` e `/api/relatorios` exigem ADMINISTRADOR. Demais rotas exigem ADMINISTRADOR ou OPERADOR, exceto o POST de login. Cadastro/listagem de usuários estão implementados; estoque e relatórios têm somente regras de autorização preparadas. O portal público será liberado junto à sua implementação. Swagger também está protegido nesta etapa.

O frontend existente pode usar seu proxy `/api` para o backend em `localhost:8080`; chamadas diretas de outra origem não têm CORS liberado. Tela de login, redirecionamento e logout no front são outra etapa. Logout local remove o token; não há refresh/revogação imediata neste escopo.

## Cadastro e listagem de usuários — RF02

Fluxo MVC: `UsuarioController → UsuarioService → UsuarioRepository`. Validação de entrada nos DTOs; normalização de e-mail, unicidade, codificação da senha e transações no serviço; restrição UNIQUE também no banco. A autorização é aplicada pela SecurityFilterChain existente.

Ambos os endpoints exigem `Authorization: Bearer <token-de-administrador>`.

`POST /api/usuarios`, corpo JSON:

```json
{
  "nome": "Operador de teste",
  "email": "operador.teste@example.com",
  "senha": "SenhaLocal@123",
  "perfil": "OPERADOR"
}
```

Todos os campos são obrigatórios. Nome: até 120 caracteres; e-mail: válido, até 254 caracteres, único após normalização de espaços externos/caixa; perfil: exatamente `ADMINISTRADOR` ou `OPERADOR`, sem valores numéricos. Senha não vazia e até 72 bytes UTF-8 (limite BCrypt); seu conteúdo é preservado, inclusive espaços. Não foi acrescentado um tamanho mínimo de senha ao RF02, pois ele não é definido no enunciado. A política do Administrador de bootstrap permanece separada.

Sucesso: **201 Created**, somente com os campos públicos:

```json
{"id":2,"nome":"Operador de teste","email":"operador.teste@example.com","perfil":"OPERADOR"}
```

O identificador acima é ilustrativo. O usuário recém-criado pode entrar pelo `POST /api/auth/login` com a senha inicial.

`GET /api/usuarios`: **200 OK**, array JSON de usuários com os mesmos quatro campos, ordenado por nome e depois id. A listagem não expõe senha nem hash. Não há edição/exclusão de usuários nesta etapa.

| Situação | HTTP | Código |
|---|---|---|
| Campos ausentes/inválidos ou senha acima de 72 bytes | 400 | DADOS_INVALIDOS |
| JSON/perfil não interpretável, inclusive perfil numérico | 400 | JSON_INVALIDO |
| E-mail já cadastrado | 409 | EMAIL_JA_CADASTRADO |
| Conflito na restrição do banco, inclusive corrida após checagem prévia | 409 | CONFLITO_DADOS |
| Sem token válido | 401 | NAO_AUTENTICADO |
| Token de Operador | 403 | ACESSO_NEGADO |

No Insomnia: autentique como Administrador, use seu Bearer token para cadastrar e listar, depois entre com o novo Operador. Com o token do Operador, GET e POST `/api/usuarios` devem retornar 403. Repetir o e-mail (mesmo mudando caixa/espaços externos) deve retornar 409. Campos inválidos devem retornar 400 sem criar usuário.

## Testes e cobertura

```powershell
.\mvnw.cmd verify
```

Os testes usam H2 em modo PostgreSQL, Flyway e credenciais fictícias exclusivas do profile `test`; não precisam do banco de desenvolvimento. Cobrem login, JWT, autorização, bootstrap, cadastro dos dois perfis, login do usuário cadastrado, listagem sem segredos, validações, limite BCrypt em bytes e unicidade protegida também pelo banco. O cenário de corrida força a checagem prévia a não detectar a duplicidade e verifica a restrição real, sem depender de timing entre threads. Testes de serviços usam Mockito. JaCoCo gera `target/site/jacoco/index.html` e exige ao menos 70% de cobertura de linhas no pacote `service`.

Para validar os testes de integração com PostgreSQL, use **uma base temporária exclusiva** e configure `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` e `SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver`. Execute `./mvnw -Dtest=AuthIntegrationTest,UsuarioIntegrationTest verify` (no PowerShell, use `.\mvnw.cmd` e coloque o argumento `-Dtest=...` entre aspas). Esses testes limpam a tabela de usuários da base configurada; nunca aponte para `sglu` ou para uma base com dados que precisam ser preservados. Remova essas variáveis após a validação para voltar ao H2 padrão.

O workflow de CI executa `verify` em push/PR quando estas alterações forem publicadas. Proteção de branch deve ser configurada no GitHub pela equipe.

**Pendência de aderência ao enunciado:** ele menciona JUnit 5. A stack original Spring Boot 4.1.1 utiliza JUnit 6 e Spring Framework 7, que [exige JUnit 6](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes#baseline-upgrades). Mantivemos essa stack; alinhar a versão com o professor ou planejar a migração conjunta da stack, sem forçar JUnit 5 incompatível.
