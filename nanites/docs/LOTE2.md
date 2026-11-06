# Lote 2 — resultado real Nanites 0.2.0

## Compilação e artefatos

Dois builds contra Minecraft real passaram; **sem javac de stubs**. Core/bridge Java17 embutidos em ambos, sem classes `com/google/gson` no mod. Não há cópia de código/artwork de jevmod. Wrapper JAR reutilizado apenas como infraestrutura; Gradle8.8 checksum SHA256 oficial `a4b4158601f8636cdeeab09bd76afb640030bb5b144aafe261a5e8af027dc612`. Endpoints de dependências HTTPS oficiais/Maven, sem curl, instalações globais ou lançamento de cliente/jogo.

| Resultado | Caminho/versão |
|---|---|
| Forge | `../dist/nanites-forge-1.20.1-0.2.0.jar`, 187624 bytes, 123 entries, bytecode61, SRG/reobf verificado |
| Fabric | `../dist/nanites-fabric-1.21.1-0.2.0.jar`, 190846 bytes, 126 entries, bytecode65, intermediary/remap + Player mixin verificados |
| Forge SHA256 | `938F2B3F67FA72128B7E5019A239A989F61724B63634074B83E4C138BD8B70AC` |
| Fabric SHA256 | `F1A76F3289FBA8D783E7E8EF3A8D6F1F0850E4BA546054316A46B14463F9B87D` |
| Toolchain | Forge1.20.1-47.4.28, ForgeGradle6.0.54, Java17; Fabric1.21.1, Loader0.16.14, API0.116.17+1.21.1, Loom1.7.4, Java21 |

Versões confirmadas em metadados oficiais (não só resultado de busca):
https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml ; https://maven.minecraftforge.net/net/minecraftforge/gradle/ForgeGradle/maven-metadata.xml ; https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml ; https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml ; https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml .

Comando autorizado executado: `powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1`. O script executa sequencialmente:

```
JAVA_HOME=<JDK17>; gradlew.bat -Ploader=forge :core:check :shared-game:check :forge:build --console=plain
JAVA_HOME=<JDK21>; gradlew.bat -Ploader=fabric :core:check :shared-game:check :fabric:build --console=plain
```

Logs completos: `../validation-logs/20261106-172909-forge.log`, `../validation-logs/20261106-172909-fabric.log`; stdout/auditoria `../build-final-2.log`, `../dist/VALIDATION.txt`. ReobfJar/remapJar foram tarefas reais. Auditoria lê zip, checa duplicates, descriptors, recipes no plural1.20/singular1.21, modelos/texturas/lang, classes core/game, ausência de Gson, major versions e namespaces de produção. Build passou antes da cópia para dist. JARs também não contêm testes. Auditoria adicional `../validation-logs/mixin-audit.log` conferiu `fabric-refmap.json`, configuração ligada ao refmap e mapeamento `Player.actuallyHurt -> class_1657.method_6074(class_1282,float)`; essa verificação foi incorporada a audit.ps1 e o resultado final está em `../validation-logs/final-audit.log`. Isso verifica empacotamento/mappings, não execução de mixin em jogo.

## Testes e iterações

- CoreTests33 + ExpansionTests54 = **87 testes** (inclui10.000 transfers randomizadas). Passaram nas duas builds reais.
- GameTests **22 checks** em17/21: saldo vazio não protege/não emite VFX; consumo de caixa uma vez; proteção financiada/débitos exatos; impacto limitado; preview; hot-swap/rollback sem reembolso; spent não reciclável; capacidades negadas; path traversal negado; modelo REQUIRED bloqueia rede; isolante exige material, consumo explícito não mapeado negado, fogo real reduzido pela lógica; decompiler opt-in negado por padrão; restart mantém script do dono.
- `test.ps1` original executado novamente com os dois JDKs, `--release17 -Xlint:all -Werror`, demos offline passaram. Logs `../core-jdk17-validation.log` e `../core-jdk21-validation.log`.
- Durante a iteração corrigidos avaliação sourceSets de subprojects, conversão URI, versão do schema como identificador `v1`, compartilhamento de outputs (builds agora sequenciais), preservação de rollback fora de session-clone, target correto do mixin **Player.actuallyHurt** conferido no JAR Minecraft com javap e nome do remapJar. Erros e tentativas ficam nos logs de build antigos; não houve bloqueio irreparável de rede.
- Forge emite warning oficial de depreciação `FMLJavaModLoadingContext.get()`; builds usam Gradle8.8, não são declaradas compatíveis com Gradle9. Gradle relata deprecações de plugin. Não impedem esses builds.

**Não foi testado**: iniciar Minecraft/cliente/dedicated server, resolução/rendering/crafting em runtime, aplicação efetiva do mixin pelo loader, um modpack, LLM real/TLS no endpoint, ou decompiler.jar confiável real configurado pelo operador. Não transformamos sucesso de compilação em evidência visual. A execução simulada de `GameService.Host` nos testes valida lógica/contabilidade, **não é Minecraft stub usado para compilar loaders**.

## Implementado e contrato de mundo

`shared-game/GameService` é independente das APIs de loaders; ambos adapters chamam só na thread do servidor. Core `NaniteSession` produz intents, não manipula mundo. Adapter roda cópia, confirma resultado/posse/budgets/conservação/requisitos de material/custo, debita no estado autoritativo e aplica efeitos limitados. Saldo usa `storage` e `spent` genéricos, capacity10trilhões cada. Intake adiciona apenas caixa registrada consumida do stack; gasto move para spent e é irreversível. Script não pode fazer net refund de spent. Sem mint pela IA; itens Creative/admin são fronteira vanilla confiável, não proteção contra cheats de OP.

Allowlist world: `protect self 0..20` (redução HP no evento fall/impact; custo1milhão por unidade), `protect.insulating self 0..20` (somente evento fire e registro material explícito; mesmo custo), `shape.ring self 1..4` e `shape.column self 1..4` (VFX paramétricos, custo100mil por unidade). Não há comandos de blocos/claims/inventário arbitrário/energia. `protect` em tick não cria escudo persistente — deve ser usado só nos handlers de dano; não há armadura custom escondida.

Eventos autenticados: group=`owner`; fall/impact: `damage` centésimosHP, `distance` centésimosbloco clamp0..4096, `fire`0/1 por tag real do DamageSource; tick a cada20ticks: `age`=gameTime. Limitscore4events/32actions por execução, fuel16.384 e até8world actions por evento. VFX enviados ao cliente renderer vanilla:24partículas por dono/20ticks,256por tickglobal, dimensões limitadas. Outros mods cancelando dano/rendering podem alterar o resultado; sem promessas de cooperação não testada.

Commands por próprio UUID server-authenticated: status/help, example/export (create-new), edit/apply, preview/load, rollback, save, mods, absorb/material, reactor (UNSUPPORTED), ai/proposal, source/decompile. Sem parâmetro target/dono e sem acesso alheio por OP/IA padrão. Nomes de arquivos confinados a scripts/<UUID>, symlinks/traversal/arquivos grandes negados. Estado versionado em mundo/nanites, save individual atômico com revisão/conservação/ownership. Adapters não permitem world actions externas ao allowlist.

IA usa snapshot destacado + LocalChatClient/MultiTurnAgent/ControlledTools em worker; nove ferramentas não ganham ativação/processos/caminhos livres. Endpointexato HTTPSloopback/no redirect/default TLS, modelo REQUIRED, token opcional **explicitamente configurado**. Resultado volta pela threadmain; logout cancela run e lifecycle generation descarta resposta antiga. Só comando de dono aplica proposta revalidada no estado atual. Mods listados pelos loaders; fontes só aliases/index do próprio dono. Decompiler worker processa1classe de1JAR de mod PATH real permitido, com configjar/version confiáveis, SHA256/versões cache, timeout/quota. Mods nested/dev sem JAR normal não são extraídos automaticamente. Dados de fonte não certificam integração.

Material: `/nanites absorb` consome item da mão somente se registro exato é configurado para isolante, mantém dono/count/revision/evidence em JSON atômico separado. Capacidade é uma regra explícita deste mod, não física ou compatibilidade inventada. Reactor/energia amaldiçoada/AOT sempre **não suportados**, SPI futura não instancia energia fictícia.

## Pendências honestas

1. Testar roteiro [../TESTE_NO_JOGO.md](../TESTE_NO_JOGO.md) nos dois runtimes/servidor e verificar conflitos de modpack. Nenhum profile de usuário foi alterado.
2. Não há blocos sólidos próprios/dismantle, armadura/OBJ custom, editor gráfico, número ilimitado de reservatórios/item contents portáteis, retirada/refund ou geometrias arbitrárias. São escudo e duas formasVFX numéricas composáveis limitadas.
3. Não há journal/outbox durável cross-Minecraft: snapshot/inventário/playerdata/crafting/material não compõem uma transação distribuída. Crash/disco cheio/restore parcial pode perder/desalinhar recursos. Não prometer proteção universal. Saves normais/rollback de código testados, não crashfuzz Minecraft.
4. Não há integração energética com mod externo. Código JVM não é carregado/recarregado por IA/jogador. Material mapeado não equivale a absorb generalizado de reactor.
5. LLM/TLS/decompiler reais precisam configuração/teste manual explícitos; não procuramos tokens nem desligamos TLS. Cache compila e a lógica core é testada, runner real precisa ferramenta configurada.

Infra nova: `settings.gradle`, `build.gradle`, `gradle.properties`, builds core/shared/forge/fabric, wrapper8.8, `build.ps1`, `tools/bootstrap.ps1`, `tools/probe.ps1`, `tools/textures.ps1`, `tools/audit.ps1`. Core existente alterado apenas via editFile com duas extensões compatíveis: token opt-in JvmTransport e mods reais fornecidos no catálogo de ControlledTools. Testes core existentes e jevmod/postman/.postman preservados.
