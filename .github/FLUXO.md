# Fluxo de branches e pull requests

1. Trabalhe em uma branch diferente de main e envie os commits.
2. O workflow Abrir PR automaticamente cria um PR para main, se houver commits novos e ainda não existir PR aberto para a mesma branch.
3. Novos pushes atualizam o PR existente. O CI roda nos pushes de branches, nos PRs para main e após o merge na main.
4. Revise o PR e os checks antes de realizar o merge. A automação não aprova nem faz merge.

## Configuração administrativa obrigatória no GitHub

Estes ajustes são configurações do repositório; o arquivo YAML não protege a main sozinho.

- Settings > Actions > General > Workflow permissions: habilitar Allow GitHub Actions to create and approve pull requests. O workflow só cria PRs, não aprova.
- Settings > Branches > Branch protection rules: proteger main com Require a pull request before merging e Require status checks to pass before merging.
- Exigir o check build no frontend e verify no backend, com a branch atualizada antes do merge.
- Habilitar Do not allow bypassing the above settings, incluindo administradores, e manter force pushes e exclusão de main desabilitados. Não conceder exceções de push direto.
- A quantidade de revisões obrigatórias pode seguir o acordo da equipe; exigir PR é independente dessa quantidade.

A conta utilizada para publicar estes workflows tem acesso de escrita, sem administração. A proteção da main e a permissão de criação de PRs precisam ser confirmadas por um administrador.

PRs criados pelo GITHUB_TOKEN podem exigir autorização para executar workflows do evento pull_request. Por isso o CI também roda no push, com token somente de leitura. Não é necessário armazenar token pessoal para a criação automática.

Após integrar esta configuração à main, atualize as branches antigas a partir dela para que também recebam o workflow de push. Novas branches criadas a partir da main atualizada já terão a automação.
