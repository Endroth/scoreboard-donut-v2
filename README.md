# DonutStyleScoreboard

Plugin de scoreboard para Paper 1.21.11, com preferências individuais por jogador e linhas editáveis em `config.yml`.

## Requisitos
- Paper 1.21.11
- Java 21 ou superior
- Maven 3.9+ para compilar localmente
- Opcional: Vault + plugin de economia para exibir saldo real

## Compilar
Na pasta do projeto:
```bash
mvn clean package
```
O arquivo será `target/DonutStyleScoreboard.jar`. Copie para `plugins/` e reinicie o servidor.

## GitHub Actions
Envie todos os arquivos para um repositório GitHub. A workflow `.github/workflows/build.yml` compila em cada `push` e também pode ser iniciada em **Actions → Build → Run workflow**. Baixe o artefato `DonutStyleScoreboard`.

## Comandos
- `/dsb` — lista comandos e IDs de linha
- `/dsb toggle` — liga/desliga sua scoreboard (salvo individualmente)
- `/dsb line <id> on|off` — mostra/oculta uma linha para você
- `/dsb label <id> on|off` — mostra/oculta o texto da linha, mantendo o ícone e o valor
- `/dsb reload` — recarrega `config.yml` (permissão `dsb.admin`)

IDs: `kills`, `deaths`, `playtime`, `balance`, `online`.

Exemplos:
```text
/dsb line deaths off
/dsb label kills off
/dsb line balance off
/dsb toggle
```

## Personalizar
Edite `plugins/DonutStyleScoreboard/config.yml`. Cada linha possui `enabled`, `icon`, `label` e `format`. Códigos de cor usam `&`, por exemplo `&a` para verde. Em `format`, `%value%` é substituído pela estatística. `%icon%` e `%label%` também estão disponíveis para formatos personalizados.

As preferências individuais ficam em `plugins/DonutStyleScoreboard/players.yml`.

## Observações
- Não há linha KeyAll.
- Kills e mortes usam estatísticas vanilla do jogador; tempo é o tempo total jogado, exibido em horas/minutos; online mostra jogadores conectados/máximo.
- Saldo requer Vault e um provedor de economia compatível. Sem isso, aparece `N/D`.
- O plugin usa uma scoreboard própria por jogador; plugins que também controlam a sidebar podem disputar a mesma posição.
