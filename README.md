# SGLU — Backend

API Spring Boot 4.1.1 / Java 21. Implementa login, cadastro/listagem de usuários, clientes e produtos, perfis ADMINISTRADOR/OPERADOR e criação controlada do primeiro Administrador.

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

Flyway cria e atualiza as tabelas; Hibernate apenas valida o esquema. O bootstrap salva somente o hash BCrypt. Se já houver Administrador, preserva seus dados; se o e-mail pertencer a um Operador, falha sem promovê-lo. Execute a primeira inicialização com uma única instância da aplicação. Nos próximos inícios, defina `BOOTSTRAP_ADMIN_ENABLED=false` e remova as variáveis de credenciais iniciais.

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

## Cadastro e listagem de clientes e produtos — RF03

Fluxos MVC: `ClienteController → ClienteService → ClienteRepository` e `ProdutoController → ProdutoService → ProdutoRepository`. DTOs validam a entrada e definem o JSON de saída; serviços controlam transações; entidades representam os cadastros persistidos.

As quatro operações permitem **ADMINISTRADOR e OPERADOR**, sempre com `Authorization: Bearer <token>` e, nos POSTs, `Content-Type: application/json`.

| Método | Rota | Resultado |
|---|---|---|
| POST | `/api/clientes` | 201, cliente cadastrado |
| GET | `/api/clientes` | 200, array de clientes |
| POST | `/api/produtos` | 201, produto cadastrado |
| GET | `/api/produtos` | 200, array de produtos |

Corpo de `POST /api/clientes`:

```json
{"nome":"Cliente fictício","telefone":"(16) 99999-0000","endereco":"Rua Fictícia, 10"}
```

Resposta ilustrativa:

```json
{"id":1,"nome":"Cliente fictício","telefone":"(16) 99999-0000","endereco":"Rua Fictícia, 10"}
```

Corpo de `POST /api/produtos`:

```json
{"codigo":"PROD-001","nome":"Produto de teste","estoqueMinimo":5}
```

Resposta ilustrativa:

```json
{"id":1,"codigo":"PROD-001","nome":"Produto de teste","estoqueMinimo":5}
```

Todos os campos são obrigatórios. Campos textuais não aceitam somente espaços; espaços externos são removidos. Limites técnicos adotados: nome de cliente/produto com até 120 caracteres; telefone até 30; endereço textual até 500; código até 64. Esses limites e a normalização são decisões de implementação. O telefone preserva a formatação informada. Clientes e nomes de produtos podem se repetir; o **código do produto é único**, normalizado para maiúsculas (`prod-001` e `PROD-001` identificam o mesmo código).

`estoqueMinimo` aceita inteiro de 0 a 2147483647. Valores negativos, ausentes, fracionários ou fora desse intervalo são rejeitados. Envie `2`, não `2.0`: a configuração JSON impede truncamento de números decimais para inteiros. Este campo é o limite de alerta; saldo e movimentações pertencem à Release 2.

As listagens retornam os mesmos campos das respostas de cadastro, ordenadas por nome e depois id; sem registros retornam `[]`. Edição e exclusão não fazem parte desta etapa.

| Situação | HTTP | Código |
|---|---|---|
| Campo obrigatório ausente/vazio, limite textual ou estoque negativo | 400 | DADOS_INVALIDOS |
| JSON inválido ou estoque não interpretável como inteiro | 400 | JSON_INVALIDO |
| Código já cadastrado | 409 | CODIGO_PRODUTO_JA_CADASTRADO |
| Conflito detectado pela restrição do banco após a checagem prévia | 409 | CONFLITO_DADOS |
| Token ausente, inválido ou expirado | 401 | NAO_AUTENTICADO |

**Migração:** ao reiniciar o backend, Flyway aplica `V2__create_clientes_produtos.sql`, criando as duas tabelas, a unicidade do código e a restrição de estoque mínimo não negativo. A V1 e os usuários existentes são preservados. Não é necessário recriar o container ou apagar o volume.

**Validação no Insomnia:** entre como Administrador, cadastre e liste um cliente e um produto com os exemplos acima. Repita com o token do Operador: ambas as operações devem funcionar. Repita `PROD-001` usando `prod-001`: espere 409. Envie estoque `-1` ou `1.5`, ou omita um campo obrigatório: espere 400 sem criação. Retire o token: espere 401. Reinicie a aplicação e liste novamente para conferir a persistência.

## Testes e cobertura

### Correções da Release 1 e atualização de bases existentes

As migrações V3/V4 criam pedidos em bases novas e regularizam tabelas que foram criadas pelo antigo `ddl-auto=update`. A V3 usa o nome reconhecido pelo Flyway; a V4 instala as restrições ausentes e ajusta a exclusão dos filhos de pedidos. Nenhuma migração apaga usuários ou pedidos. Hibernate agora usa `validate`, e nomes inválidos de migração interrompem a inicialização.

Antes de atualizar uma base com dados, faça um backup. Se houver dados antigos inválidos (por exemplo, quantidade zero ou código duplicado), a migração recusa a atualização; corrija esses registros de forma explícita antes de tentar novamente. Não apague volumes nem execute a limpeza de testes no banco de desenvolvimento. Em um build existente, use `./mvnw clean verify` para remover a cópia antiga de `V3_create_pedidos.sql` que possa ter ficado em `target/classes`.

Pedidos inexistentes retornam 404 (`PEDIDO_NAO_ENCONTRADO`); referências/campos inválidos, 400 (`DADOS_INVALIDOS`); filtros inválidos, 400 (`PARAMETRO_INVALIDO`); ações proibidas/status desatualizado, 409; conflito de versão, 409 (`PEDIDO_DESATUALIZADO`); sessão sem usuário válido, 401. Todos usam `{codigo, mensagem, detalhes}`. Colisões concorrentes do código de consulta são repetidas em novas transações, com limite de cinco tentativas e sem repetir outros erros de integridade.

### Sistema completo com Docker Compose

Mantenha os repositórios em pastas irmãs `logistica-ufscar-backend` e `logistica-ufscar-frontend`. Na pasta do backend, copie `.env.example` para `.env`, preencha `DB_PASSWORD` e `JWT_SECRET` (Base64 de pelo menos 32 bytes aleatórios). O Compose carrega `.env`; a execução direta pelo IntelliJ/Maven exige as variáveis na própria configuração de execução.

Em uma base vazia, configure `BOOTSTRAP_ADMIN_ENABLED=true`, nome, e-mail fictício e senha inicial de pelo menos 12 caracteres. Após criar o Administrador, volte para `false`. Em bases já utilizadas, preserve a senha do banco e a chave JWT existentes.

```powershell
docker compose up -d --build
docker compose ps
docker compose logs backend
```

Frontend: `http://localhost:3000`; API: `http://localhost:8080`; PostgreSQL local: `localhost:5432`. `FRONTEND_PORT`, `BACKEND_PORT` e `DB_PORT` permitem outras portas. Dentro do Docker a API conecta sempre a `db:5432`. `FRONTEND_CONTEXT` permite informar outra pasta do frontend.

Para executar o backend no IntelliJ, suba somente `docker compose up -d db`, use `DB_URL=jdbc:postgresql://localhost:5432/sglu` (ou a porta escolhida em `DB_PORT`) e inicie o frontend com `npm run dev`. Não execute duas instâncias na mesma porta. Se já houver um container antigo, confira `docker compose ps`; pare o serviço antes de mudar seu mapeamento. `docker compose down` preserva o volume; não use `down -v` para atualizar o sistema.

### Isolamento da suíte

Os testes integrados usam `BancoDeTeste`, que remove histórico/itens antes de pedidos e cadastros. O teste de migração verifica instalação limpa, atualização com registros existentes, preservação do usuário e recusa de registros legados inválidos. Para executar esses cenários também em PostgreSQL temporário, inclua `PedidosMigrationTest` em `-Dtest` e defina `SGLU_MIGRATION_TEST_URL` para a mesma base exclusiva; cada cenário usa um schema temporário próprio.

```powershell
.\mvnw.cmd verify
```

Os testes usam H2 em modo PostgreSQL, Flyway e credenciais fictícias exclusivas do profile `test`; não precisam do banco de desenvolvimento. Cobrem login, JWT, autorização, bootstrap, cadastro dos dois perfis, login do usuário cadastrado, listagem sem segredos, validações, limite BCrypt em bytes e unicidade protegida também pelo banco. O cenário de corrida força a checagem prévia a não detectar a duplicidade e verifica a restrição real, sem depender de timing entre threads. Testes de serviços usam Mockito. JaCoCo gera `target/site/jacoco/index.html` e exige ao menos 70% de cobertura de linhas no pacote `service`.

Os testes de clientes/produtos cobrem ambos os perfis, validações, limites, ordenação, persistência, duplicidade e restrições reais do banco. A migração também é verificada sobre uma base H2 na versão V1 com usuário existente, comprovando sua preservação após a V2.

Para validar os testes de integração com PostgreSQL, use **uma base temporária exclusiva** e configure `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` e `SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver`. Execute `./mvnw -Dtest=AuthIntegrationTest,UsuarioIntegrationTest,CadastrosIntegrationTest,PedidosIntegrationTest verify` (no PowerShell, use `.\mvnw.cmd` e coloque o argumento `-Dtest=...` entre aspas). Esses testes limpam as tabelas de usuários, clientes e produtos da base configurada; nunca aponte para `sglu` ou para uma base com dados que precisam ser preservados. Remova essas variáveis após a validação para voltar ao H2 padrão.

O workflow de CI executa `verify` em push/PR quando estas alterações forem publicadas. Proteção de branch deve ser configurada no GitHub pela equipe.

**Pendência de aderência ao enunciado:** ele menciona JUnit 5. A stack original Spring Boot 4.1.1 utiliza JUnit 6 e Spring Framework 7, que [exige JUnit 6](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes#baseline-upgrades). Mantivemos essa stack; alinhar a versão com o professor ou planejar a migração conjunta da stack, sem forçar JUnit 5 incompatível.

## Entradas de estoque — RF08

Administradores e Operadores autenticados podem registrar entradas em `POST /api/estoque/entradas`:

```json
{"produtoId": 1, "quantidade": 10, "data": "2026-11-09"}
```

A resposta `201` informa a movimentação, produto, quantidade, data, instante de registro, usuário responsável e `saldoAtual` após a operação. A data informada é obrigatória; não há restrição de passado/futuro no RF08. Quantidade deve ser inteira positiva. Por compatibilidade com o tipo INTEGER, quantidade e saldo têm limite técnico de 2.147.483.647; exceder o saldo retorna `409 SALDO_LIMITE_EXCEDIDO`. Dados inválidos retornam 400 e autenticação inválida retorna 401.

A migração V5 cria o saldo inicial zero dos produtos existentes e a tabela `movimentacoes_estoque`. Saldo e movimentação ENTRADA são gravados na mesma transação; um bloqueio por produto protege entradas concorrentes. Uma falha desfaz ambos. A autoria vem do JWT, nunca do formulário.

Validação manual: registre 10 unidades e depois 5 do mesmo produto; as respostas devem apresentar saldos 10 e 15. Quantidade zero, negativa, fracionada, produto inexistente ou data inválida não podem alterar o saldo. Execute `./mvnw verify` para incluir os testes de concorrência, rollback, permissões e migração V4→V5. Para PostgreSQL temporário, inclua `EstoqueIntegrationTest` e `PedidosMigrationTest` na seleção de testes descrita acima.

Este incremento cobre RF08. Consulta geral de saldos/alertas (RF10), baixa no despacho (RF09), inventário (RF11) e consulta de movimentações (RF12) são etapas seguintes. O retorno de saldo é uma fotografia após aquela entrada. A API não oferece reenvio idempotente; em falha de conexão após envio, confira o registro antes de repetir.
