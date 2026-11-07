# Nanites — Forge Minecraft 1.20.1 / 0.3.1

## Entrega local atual — quatro etapas (07/11/2026)

- JAR auditado: `dist/stage4-armor-20261107/nanites-forge-1.20.1-0.3.1-stage4.jar` (versão interna 0.3.1, sem bump; JAR anterior de dist preservado).
- [Auditoria, SHA256 e instalação Forge/Java 17](docs/RELEASE_STAGE4_FORGE.md) · [Teste ponta a ponta: máquinas, assimilação, fábricas e armadura viva](docs/TESTE_QUATRO_ETAPAS_FORGE.md).
- Verificação offline não equivale a teste Minecraft/modelo real. Não instalar duas versões Nanites juntas. Seções abaixo são histórico/base preservada.

**Construção real**: blueprints voxel mistos/arbitrários, cuboids/spheres/lines, registry instalado, fila incremental servidor, consulta live e ferramentas IA que submetem/cancelam planos. Sem integração/modificação de jevmod, sem hot-install de JARs, sem rollback automático de blocos. Validação in-game ainda é manual.

- [Guia português 0.3.1 — comandos exatos, blueprint manual, IA, hot reload e limites](docs/CONSTRUCAO_FORGE_0.3.1.md).
- Build exclusivamente Forge: Java 17, `gradlew.bat -Ploader=forge :core:check :shared-game:check :forge:build --offline --console=plain`. Não usar o publicador histórico 0.3.0 para esta versão.
- Artefato esperado após build/reobf bem-sucedidos: `forge/build/libs/nanites-forge-1.20.1-0.3.1.jar`; publicação em `dist` somente com build comprovado. Veja `docs/VERIFICACAO_CONSTRUCAO_0.3.1.md` para os resultados reais desta execução.

## Base anterior Forge 0.3.0 (preservada; evidências abaixo são históricas)

A nova base **Forge-only** adiciona armadura modular IRON, Extremis opt-in, perfis runtime self e execução IA limitada por grant explícito. Atende **parcialmente** à interoperabilidade com outros mods; não modifica universalmente entidades/máquinas/atributos externos. Sem teste Minecraft/IA real: auditoria estática e testes determinísticos não certificam gameplay.

- [Guia completo Forge modular/runtime/Extremis](docs/FORGE_MODULAR_EXTREMIS.md): itens, comandos, segundos, custos, grant, exemplo IA e limites.
- [Guia raiz de setup/build](../GUIA_SETUP_E_BUILD.md).
- Entrega: `dist/nanites-forge-1.20.1-0.3.0.jar`; só é publicada após o gate `tools/audit-publish-forge-0.3.0.ps1 -Publish` passar; evidência separada em `dist/VALIDATION-FORGE-0.3.0.txt`.
- Forge 0.2.0 e Fabric 0.2.0 permanecem preservados. **Não existe suporte Fabric 0.3.0**; nenhuma build Fabric adicional nesta revisão. Não use o audit/build dual antigo para publicar 0.3.0.

## Histórico dual — primeira fatia Minecraft, lote 2 (0.2.0)

As evidências e comandos dual abaixo são históricos 0.2.0, não validação da base Forge 0.3.0.

**Dois builds reais passaram**: Forge 1.20.1/Java17 com `reobfJar` e Fabric 1.21.1/Java21 com `remapJar`. JARs auditados em `dist/`, core/bridge embutidos sem Gson duplicado. **Minecraft não foi iniciado; teste visual/runtime é manual**, não prometemos compatibilidade de modpack sem teste.

- [INSTALL.md](INSTALL.md): loaders, Java, configuração segura e build local.
- [TESTE_NO_JOGO.md](TESTE_NO_JOGO.md): roteiro passo a passo, receitas, depósito, escudo, VFX, edição, posse/save e IA opcional.
- [docs/LOTE2.md](docs/LOTE2.md): engenharia, logs, checksums, capacidades e pendências reais.
- `dist/nanites-forge-1.20.1-0.2.0.jar` e `dist/nanites-fabric-1.21.1-0.2.0.jar`; nunca instalar juntos.

Implementado no código Minecraft: caixa craftável500bilhões, item de acesso a reservatório genérico por dono/mundo, intake consumindo caixa uma vez, gasto irreversível conservativo, fall/impact/tick reais, redução de dano condicionada saldo, anel/coluna paramétrica de partículas via renderer vanilla budgeted, snapshots por UUID, preview/load/edit/export/rollback de código, absorção de material explicitamente mapeado com registro/evidência e proteção de fogo orçada, comandos IA com worker/nove ferramentas/propostas sem autoexecução e decompiler/cache seletivo opt-in. Não há energia de outros mods, blocos sólidos nanite, OBJ/armadura custom, estrutura arbitrária ilimitada ou UI gráfica. O reservatório não carrega saldo portátil NBT; retirada não implementada. Snapshots não são uma transação durável entre todos os saves Minecraft.

Validação: **87 testes core** + **22 checks do serviço game** em builds17/21; harness original estrito/demos também passou nos dois JDKs. Logs em `validation-logs`, `core-jdk17-validation.log`, `core-jdk21-validation.log`; auditoria/checksums em `dist/VALIDATION.txt`. Apenas LocalChatClient (token explícito opcional) e ControlledTools (mods reais no catálogo fornecido pelo loader) tiveram extensão compatível no core. `jevmod`, `postman` e `.postman` preservados; somente wrapper JAR Gradle reutilizado como infraestrutura, não código mod.

## Histórico do lote 1 — núcleo independente (contexto anterior)

As afirmações abaixo descrevem especificamente o lote1 e suas demos core, não o estado atual dos adapters Minecraft.

### Nanites — núcleo compartilhado, lote 1

**Ainda não é um mod instalável.** Implementação independente em `nanites`; `jevmod`, `postman` e `.postman` preservados. Sem cópia de código ou Gradle da referência. O lote 1 implementa programação, snapshots, autoridade por dono, agente local multi-turn e cache. JSON usa **Gson 2.10.1 local já instalado**, explicitamente no classpath; nenhum download ou conexão ao LLM foi feito.

Contrato chamável para próximos adapters: [docs/LOTE1.md](docs/LOTE1.md). Exemplo textual executado pela demo: [examples/fall.nano](examples/fall.nano).

## Executar localmente

Requer **JDK 17+**, não apenas JRE. O Java padrão da máquina é Java 8; indique explicitamente o JDK já instalado:

```powershell
cd 'D:\Downloads\nanobots mod'
powershell -NoProfile -ExecutionPolicy Bypass -File nanites\test.ps1 -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'
```

`Bypass` vale apenas para esse processo. O script compila com `--release 17 -Xlint:all -Werror`, executa CoreTests + ExpansionTests + duas demos offline e produz `nanites/build/run-<id>/nanites-core.jar` (sem Gson embutido). Cada execução usa diretório novo; não apaga trabalho existente. Troque JavaHome pelo JDK `jdk-21.0.11.10-hotspot` para a segunda validação. O parâmetro `-GsonJar 'caminho/gson-2.10.1.jar'` permite dependência local explícita; o padrão aponta ao cache já existente do usuário e falha se ausente, sem baixar nada.

`java -jar <jar>` roda a demo original. Para a demo de scripts: `java -cp '<jar>;<GsonJar>' dev.nanites.core.ProgrammingDemo` (no Windows). Ela executa queda -> proteção/reconfiguração como **intents**, preview, hot-swap, rollback de código e restore de snapshot. Não aplica efeitos no Minecraft nem produz energia.

Para Linux/macOS ou compilação manual (na pasta `nanites`, JDK 17+):

```sh
mkdir -p build/manual/main build/manual/test
find core/src/main/java -name '*.java' > build/manual/main-sources.txt
find core/src/test/java -name '*.java' > build/manual/test-sources.txt
# GSON deve apontar a um gson-2.10.1.jar local autorizado.
javac --release 17 -encoding UTF-8 -Xlint:all -Werror -cp "$GSON" -d build/manual/main @build/manual/main-sources.txt
javac --release 17 -encoding UTF-8 -Xlint:all -Werror -cp "build/manual/main:$GSON" -d build/manual/test @build/manual/test-sources.txt
cp -R examples build/manual/main/
java -cp "build/manual/main:build/manual/test:$GSON" dev.nanites.core.CoreTests
java -cp "build/manual/main:build/manual/test:$GSON" dev.nanites.core.ExpansionTests
java -cp build/manual/main dev.nanites.core.Demo
java -cp "build/manual/main:$GSON" dev.nanites.core.ProgrammingDemo
```

O harness não depende de `-ea`: falhas lançam `AssertionError` e encerram com código não zero. Resultado real da execução nesta máquina: [docs/VALIDACAO.md](docs/VALIDACAO.md).

## Implementado nesta fatia

- Caixa com **500_000_000_000L**; multiplicação, somas, subtrações e total protegidos contra overflow/negativos.
- `Ledger`: reservatórios definidos por ID e definição custom, transferências agregadas e lotes transacionais com conservação. Total global limitado a `Long.MAX_VALUE`; criação é a fronteira inicial de abastecimento, sem mint/destroy durante execução.
- `CapabilityCatalog`: propriedades enumeradas, conflitos configuráveis, origem/evidência, exigências de capacidades verificadas por adapter/versão explicitamente confiado. Exemplo isolante = true, condutor = false; nenhum caso especial de nome de armadura.
- `EmbeddedComponent`: estado/requisitos/combustível imutáveis, validação do host e realocação pura preservando tudo. Débito contábil de combustível **não gera energia**. Sem extração de blocos de outros mods.
- `ScriptLanguage` -> AST auditável com arquivo/linha, funções de expressão, precedência, condicionais, eventos numéricos, repeat limitado e orçamento global; sem Java arbitrário, IO, shell ou rede em scripts.
- `RuleEngine`: preserva IR original e interpreta scripts; valida capacidades/alvos/faixas de intents; commit integral de estado/ledger/programa ou rollback antes de efeitos externos.
- `NaniteSession`: ledger privado, proprietário e revisão monotônica, fila limitada de eventos, preview isolado, stage/tick/migração e rollback explícito do script (não dos efeitos já aplicados).
- `SnapshotStore`/`SafeData`: JSON estrito com Gson, versão/schema/ledger/event state, leitura validada/limitada, escrita temporária com force + rename atômico, rejeição de saves antigos.
- `LocalChatClient`/`MultiTurnAgent`/`ControlledTools`: transporte HTTPS padrão e fake injetável, timeout/cancelamento, quotas, loop assíncrono com resultados incrementais e ferramentas allowlist; propostas jamais são ativadas pela IA.
- `SourceCache`: cache em disco por SHA-256 e todos os eixos de versão, manifestos/payloads com hash, slices/search literal/invalidação controlada. `VineflowerRunner`: implementação opcional seletiva com ProcessBuilder e quotas, exige ferramenta explicitamente confiada; não executado nesta validação.
- Testes de invariantes e segurança, conservação aleatória determinística, erros, hot-swap/migração, snapshots/cache/tool injection/cliente fake; duas demos executáveis.

## Ainda não implementado

Adapters Forge 1.20.1 e Fabric 1.21.1, receitas/item caixa, networking Minecraft, UI/editor, integradores de mundo/reator, energia, composição material por lotes, VFX/render e jogo instalável são os próximos lotes. Intents não são efeitos executados. Absorção geral de reator exige adapter real que preserve combustível/estado/inventário; fonte decompilada nunca promove integração a VERIFIED. O runner Vineflower existe, mas requer JAR explicitamente configurado/confiado e ainda não teve teste real de decompilação. Certificado/modelo do LLM não foram validados. Não há benchmark de tick/GPU nem promessa de reversão de efeitos externos.

Consulte [arquitetura e decisões](docs/ARQUITETURA.md), [contratos e limites de confiança](docs/INTEGRACOES.md) e [marcos](docs/MARCOS.md). Código original desta pasta: não contém código extraído do jevmod. Uma licença de distribuição do projeto deve ser escolhida pelo titular antes de publicação; não se presume licença do jevmod por proximidade.
