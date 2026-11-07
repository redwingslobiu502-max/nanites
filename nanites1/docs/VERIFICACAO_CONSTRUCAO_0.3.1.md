# Verificação factual — construção Forge 0.3.1

Projeto: `D:\Downloads\nanobots\nanites`. Target implementado e compilado: **Forge Minecraft 1.20.1 / Java 17**, Forge 47.4.28. Não foi lido/modificado/integrado fonte de `jevmod` nesta execução. Não houve execução do Minecraft ou conexão a modelo de IA real. Alterações anteriores já existentes foram preservadas; `git status --short` e `git diff --stat` foram consultados antes de editar. O primeiro comando de inspeção usou sintaxe PowerShell em CMD e falhou; a inspeção foi corrigida usando `&` e `git --no-pager` para evitar pager. Não houve reset/checkout/commit nem limpeza de alterações do usuário.

## O que efetivamente foi implementado

- Blueprint JSON com origem absoluta/dimensão explícitas, lista livre de voxels relativos com materiais mistos e blockstates de registry, mais cuboids sólidos inclusivos, spheres sólidas inteiras e lines DDA. JSON estrito, campos exatos, limites de expansão, overflow e diagnósticos.
- `ConstructionQueue`: valida todos os blocos/posições antes de substituir a fila válida; fila global incremental por tick, round-robin, orçamento de passos/tempo configurável; consulta de progresso e estado observado após colocação; cancelamento sem rollback; chunks ausentes aguardam sem tickets/carregamento forçado da fila.
- `ForgeConstruction`: resolve IDs e propriedades dos blocos instalados por Forge registry, sem lista fixa de materiais; observa mundo e jogador, pesquisa catálogo paginado; executa `ServerLevel.setBlock` com flags 18 exclusivamente na thread servidor.
- `ControlledTools`/`MultiTurnAgent`/`GameService`: cinco ferramentas de construção com schemas/descrições reais no protocolo existente, resultados acrescentados às rodadas seguintes; chamadas da IA efetivamente agendam construção. Provider assíncrono retorna fork da sessão **atual** para `state`, `read_script` e preview/propostas, sem acesso a mundo no worker. Trace `tool_result` deixou de ser descartado.
- Geração/cancelamento de rodada e revisão da fila rejeitam callbacks antigos; nova pergunta substitui conversa antiga; comandos manuais e reloads invalidam rodada anterior. Scripts `.nano` existentes mantêm preview/stage/apply/migration/rollback e last-known-good. Blueprint reload é explícito por comando, não watcher.
- Construção direta não exige grant/saldo/aprovação por bloco; regras históricas de grants/custos de intervenções e proteção permanecem separadas. Não foram prometidas correção integral da auditoria, instalação dinâmica de JARs ou compatibilidade universal.

## Arquivos realmente editados nesta execução

Todos os caminhos abaixo são relativos a `nanites/`:

```text
README.md
core/src/main/java/dev/nanites/core/ControlledTools.java
core/src/main/java/dev/nanites/core/MultiTurnAgent.java
core/src/main/java/dev/nanites/core/SafeData.java
shared-game/build.gradle
shared-game/src/main/java/dev/nanites/game/GameService.java
shared-game/src/test/java/dev/nanites/game/GameTests.java
forge/build.gradle
forge/src/main/java/dev/nanites/forge/NanitesForge.java
forge/src/main/resources/META-INF/mods.toml
```

## Arquivos novos desta execução

```text
shared-game/src/main/java/dev/nanites/game/Blueprint.java
shared-game/src/main/java/dev/nanites/game/ConstructionQueue.java
shared-game/src/test/java/dev/nanites/game/ConstructionTests.java
forge/src/main/java/dev/nanites/forge/ForgeConstruction.java
docs/CONSTRUCAO_FORGE_0.3.1.md
docs/VERIFICACAO_CONSTRUCAO_0.3.1.md
dist/VALIDATION-FORGE-0.3.1.txt
```

Novos logs gerados:

```text
validation-logs/construction-first-check.log
validation-logs/construction-forge-check.log
validation-logs/construction-forge-final.log
validation-logs/construction-release-0.3.1.log
```

As classes `ForgeRuntime`, `ModularArmor`, `ArmorModules`, `ProtectionBudget`, `RuntimeInterventions`, os testes runtime/atributos, receitas, idiomas e outros arquivos que já apareciam alterados/untracked antes da execução **não são criações desta execução**. Eles foram reaproveitados/preservados, não atribuídos a este trabalho. `git diff --stat` inclui alterações preexistentes; não é um delta exclusivo desta sessão.

## Comandos executados e resultados

Ambiente de build:

```bat
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
```

1. `gradlew.bat -Ploader=forge :core:check :shared-game:check :forge:compileJava --offline --console=plain` → falhou no teste novo que inicialmente esperava reset do default de estado no hot-swap. A migração preserva estado existente; a expectativa foi corrigida para verificar o efeito live real, não mudar o comportamento de migração. Log `construction-first-check.log`.
2. `gradlew.bat -Ploader=forge :core:check :shared-game:check :forge:build --offline --console=plain` → o teste novo passou; regressão `GameTests` revelou perda do diagnóstico de proposta obsoleta devido à limpeza prematura da lista. O código foi corrigido para manter propostas e sua checagem de revisão. O teste de geração foi atualizado para a geração atual após reload manual, e ganhou verificação de rejeição da geração antiga (sem remover as verificações de obsolescência). Log `construction-forge-check.log`.
3. `gradlew.bat -Ploader=forge :forge:compileJava :core:check :shared-game:check :forge:build --offline --console=plain` → **BUILD SUCCESSFUL**, 36 s; Forge compilou, reobf e testes passaram. Log `construction-forge-final.log`.
4. Depois das últimas melhorias de mensagens/diagnósticos e dos testes adicionais de troca de script na mesma rodada e reload de arquivo: `gradlew.bat -Ploader=forge :core:check :shared-game:check :forge:build --offline --console=plain` → **BUILD SUCCESSFUL in 32s**, 23 tarefas (15 executadas, 8 up-to-date). Este é o **build final que foi publicado**. Log `construction-release-0.3.1.log`.

Resultados do último build:

| Tarefa | Resultado observado |
|---|---|
| `:core:coreTests` | 33 testes, mais 10.000 transferências randomizadas |
| `:core:expansionTests` | 57 checks, transport fake |
| `:shared-game:gameTests` | 51 checks |
| `:shared-game:runtimeTests` | 91 checks, backend/transport fake |
| `:shared-game:constructionTests` | 252 asserções (incluem guardas de thread, não 252 cenários independentes), mundo/IA fake |
| `:forge:forgeAttributeTests` | 15 checks com objetos reais de atributos, sem abrir jogo |
| `:forge:compileJava`, `:forge:reobfJar`, `:forge:build` | Sucesso |

Warnings remanescentes: API antiga `FMLJavaModLoadingContext.get()` preexistente, uso de APIs deprecated no runtime existente/Gradle 9 futuro, terminal sem recursos avançados nos testes Forge. A linha `Nanites VFX failed ... VFX failure` vem do cenário de regressão que injeta falha de VFX intencionalmente; a tarefa passa. Nenhum desses logs representa teste visual in-game.

Também foram executados `git --no-pager diff --check -- .`, `git --no-pager diff --stat -- .`, `git status --short -- .`, listagens de diretórios, `tasklist` para verificar andamento do build e conferência de empacotamento/hash via PowerShell (`System.IO.Compression.ZipFile`, `Get-FileHash`). `diff --check` não reportou erro de whitespace; avisos LF/CRLF são do Git/Windows. Nenhuma dependência foi instalada e os builds foram solicitados em modo offline. Não foram usados curl, runServer/runClient ou scripts dual/Fabric.

## JAR final publicado

```text
D:\Downloads\nanobots\nanites\dist\nanites-forge-1.20.1-0.3.1.jar
Tamanho: 262625 bytes
SHA256: 82EB0A023F79B6B8BC9FB05181233C43AEFF0CD1C5FF6DAA6E8205920E97CC7F
```

A cópia foi feita **após** o build final aprovado, recusando sobrescrever destino já existente. Conferência: 162 entradas no JAR; classes `Blueprint`, `ConstructionQueue`, `ForgeConstruction`, `NanitesForge`, `ControlledTools` e `GameService` presentes; metadata 0.3.1 / Minecraft 1.20.1; nenhuma classe Gson duplicada. Hash do JAR de `forge/build/libs` e da cópia em `dist` idênticos. Artefatos antigos 0.2.0 em `dist` foram preservados, sem afirmar publicação histórica 0.3.0 que não existia ali.

## Roteiro do seu teste e pendências

Siga [CONSTRUCAO_FORGE_0.3.1.md](CONSTRUCAO_FORGE_0.3.1.md): instala com jogo fechado, teste de um bloco sem IA, blueprint misto em arquivo, erro de registry/blockstate, progresso/cancelamento, consulta live, pedido IA, substituição/reload e scripts vivos. Comandos centrais implementados:

```text
/nanites build submit JSON
/nanites build load nome
/nanites build status
/nanites build cancel
/nanites build limits
/nanites blocks query offset limit
/nanites observe {"dimension":"minecraft:overworld","x":0,"y":80,"z":0,"radius":0}
/nanites ai pedido
/nanites ai_cancel
/nanites edit texto.nano
/nanites apply
/nanites preview nome
/nanites load nome
/nanites rollback
```

`JSON`, `nome`, `pedido` e `texto.nano` são placeholders de sintaxe: o guia contém comandos literais prontos e conteúdo de arquivo. A IA precisa de modelo local compatível com tools e TLS configurados pelo usuário; aqui só se validou o protocolo com transport fake.

Pendências/limitações verdadeiras: validação in-game/modpack e IA real pelo usuário; hooks de mods e física não podem ser certificados por compile/fake host; flags 18 não reproduzem todas as atualizações de vizinhos; não há NBT de block entities, fila persistida, rollback de mundo, crafting/consumo de materiais, watcher automático ou agente infinito de manutenção. Parsing/validação do plano inteiro ainda são síncronos no servidor, e um único hook/setBlock não é preemptível. Configuração inicial de IA/limites exige reinício; **scripts/blueprints/replanejamento** não exigem. A lista arbitrária de voxels é implementada e executada, mas tamanho/tempo/conversa são finitos. Não há bloqueio de build restante nesta entrega.
