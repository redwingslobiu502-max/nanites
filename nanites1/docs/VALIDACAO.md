# Validação executada — 06/11/2026

## Lote 1 — validação atual do núcleo ampliado

Executados `nanites/test.ps1 -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'` e o mesmo comando com `jdk-21.0.11.10-hotspot`. Rodadas paralelas em diretórios de build distintos; `--release 17 -encoding UTF-8 -Xlint:all -Werror`, compilação main/test separada, CoreTests, ExpansionTests, Demo, ProgrammingDemo e empacotamento. **Ambas chegaram a VALIDATION=PASS.** Gson 2.10.1 já existente em `C:\Users\Andrey\.gradle\caches\minecraft\libraries\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar`, configurável por `-GsonJar`; não foi baixado/embutido no JAR.

| Ambiente | CoreTests | ExpansionTests | Demos / JAR |
|---|---:|---:|---|
| JDK 17.0.17 | 33 PASS | 54 PASS | PASS |
| JDK 21.0.11, release17 | 33 PASS | 54 PASS | PASS |

**87 testes por JDK**, incluindo 10.000 transferências aleatórias do harness original. Testes novos: malformed/token/AST/depth, funções/condicionais/curto-circuito, loops/fuel/action budget, overflow, world intents/ranges/outbox, preview/ownership/revision, hot-swap falho/migração/rollback de código, snapshots restart/fila/conservação/stale saves/atomic quota, JSON strict/depth/duplicate, cache namespace/traversal/symlink/corrupção/invalidação, tools allowlist/source injection/proposta sem ativação, chat fake indisponível/3xx/429/503/malformed/timeout/cancel/in-flight/output quota, multi-turn incremental/call IDs/limites/pre-reserva. Symlink foi testado quando permitido pelo SO (o harness imprime SKIP se faltar privilégio).

```text
EXPANSION_TESTS=54 PASS; FAKE_TRANSPORT_ONLY; NO_LLM_CONNECTION
PROGRAMMING_DEMO=PASS; conserved=500000000000; script rollback kept committed transfers; world effects NOT executed
VALIDATION=PASS (Java 17 bytecode; core only, NOT a Minecraft mod)
```

Artefatos desta rodada, nomes confirmados por listagem (não instalar como mods):
- JDK17: `nanites/build/run-e360cbdd75a2433c8159eeabc83c32cf/nanites-core.jar`
- JDK21: `nanites/build/run-7e91dbbfe261454ca98ac6eececdbe45/nanites-core.jar`

Logs da rodada final:
- `C:\Users\Andrey\AppData\Local\Temp\postman-shell-term_1793994983133_7jq19s6wk.log`
- `C:\Users\Andrey\AppData\Local\Temp\postman-shell-term_1793994984309_n6jonqqrp.log`

Verificação suplementar: `javap -verbose` de `ScriptLanguage` no JAR JDK21 confirmou **major version: 61**; ProgrammingDemo foi executada diretamente do JAR JDK17 com Gson no classpath e passou. SHA-256 do Gson local: `4241C14A7727C34FEEA6507EC801318A3D4A90F070E4525681079FB94EE4C593`. A alteração posterior no comentário de IntegrationContracts apenas aponta aos novos implementadores, sem mudar comportamento.

Durante implementação houve correções antes dessa rodada: warnings -Werror de close explícito no harness e espera de cancelamento assíncrono no teste de timeout. Não foram ocultados com flags permissivas. Pastas incompletas de build preservadas. Algumas inspeções de paths quebrados pelo terminal exigiram confirmar nomes por listagem; não eram falhas de runtime.

**Não testado:** TLS/modelo real, chamadas ao LLM, execução real do Vineflower, adapters/Minecraft/inventários, absorção de reator, efeitos/VFX ou benchmark de tick. Runner Vineflower seletivo é código real opcional, mas precisa JAR explicitamente configurado/confiado e smoke test. Source injection usa fake e comprova limite de autoridade, não resistência cognitiva de um modelo real. Persistência validada é do núcleo, não transação universal com mundo/inventário. Sem downloads, mudanças globais, cópia/execução de jevmod ou escrita em postman/.postman. Preservação pelo escopo das operações, não hash pré/pós de toda a referência.

Contrato completo e limites honestos: [LOTE1.md](LOTE1.md).

---

## Histórico — validação M0 da fundação

As seções abaixo registram a rodada anterior de 33 testes e artefatos antigos, não o estado atual do lote 1.

## Ambiente observado (leitura apenas)

- Raiz inicial: `.postman`, `jevmod`, `postman`; `nanites` livre, criado como projeto independente.
- PATH: `java/javac 1.8.0_502` em `D:\jdk-8.0.502.7-hotspot`; inadequado ao target. Gradle encontrado, mas não executado.
- JDKs existentes selecionados explicitamente: Temurin **17.0.17+10** e **21.0.11+10-LTS**, em `C:\Program Files\Eclipse Adoptium`.
- Nenhuma instalação global, download, chamada ao LLM, execução de jevmod/jogo/mods, decompilação ou cópia de wrapper. Toda fonte e artefato desta entrega vive em `nanites`; nenhuma escrita solicitada em `jevmod`, `postman` ou `.postman`. Não fizemos comparação criptográfica pré/pós de toda a referência; preservação é pelo escopo das operações, não um hash de integridade inventado.

## Comandos reprodutíveis utilizados

Na raiz `D:\Downloads\nanobots mod`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File nanites\test.ps1 -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'
powershell -NoProfile -ExecutionPolicy Bypass -File nanites\test.ps1 -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
```

A rodada final executou ambos sequencialmente com `$ErrorActionPreference='Stop'`. Em cada execução: `javac --release 17 -encoding UTF-8 -Xlint:all -Werror`, compilação de main/test separada, `CoreTests`, `Demo`, `jar --create --main-class dev.nanites.core.Demo`. Script interrompe em código não zero; ambos chegaram ao marcador final de sucesso.

## Resultados da fonte final

| Ambiente | Harness | Transferências aleatórias | Demo e empacotamento |
|---|---:|---:|---|
| JDK 17.0.17 | **33/33 PASS** | 10.000, seed 1701 | PASS |
| JDK 21.0.11 com `--release 17` | **33/33 PASS** | 10.000, seed 1701 | PASS |

Saída (normalizada somente a quebra de linha do terminal):

```text
RESULT: 33 tests passed; randomized transfers=10000; no network or game execution
Tick: Result[committed=true, swapped=true, program=build.bridge, error=]
VALIDATION=PASS (Java 17 bytecode; core only, NOT a Minecraft mod)
```

A demo passou de `supply=500000000000, bridge=0, activations=0, program=idle` para `supply=0, bridge=500000000000, activations=1, program=build.bridge`. Total permaneceu **500000000000**; foi criado apenas o mapa agregado, não entidades Minecraft.

Artefatos da rodada final (não instalar como mod):

- JDK 17: `D:\Downloads\nanobots mod\nanites\build\run-11018e54780c4e41829830627d5a3447\nanites-core.jar`
- JDK 21: `D:\Downloads\nanobots mod\nanites\build\run-56d48cf76c734e52a5ce937eb21e68c5\nanites-core.jar`

Os dois JARs finais também foram executados diretamente com `java -jar`, respectivamente nos JDKs 17 e 21: ambos produziram a demo correta. `javap -verbose` no JAR gerado pelo JDK 21 confirmou `major version: 61` (Java 17). Uma tentativa suplementar inicial de executar o JAR usou caminho transcrito incorretamente da quebra de linha do terminal e falhou com `Unable to access jarfile`; caminhos foram confirmados pela listagem, corrigidos aqui e a execução direta foi repetida com sucesso. Não foi uma falha do core.

```powershell
& 'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot\bin\java.exe' -jar 'nanites\build\run-11018e54780c4e41829830627d5a3447\nanites-core.jar'
& 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe' -jar 'nanites\build\run-56d48cf76c734e52a5ce937eb21e68c5\nanites-core.jar'
& 'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot\bin\javap.exe' -verbose -classpath 'nanites\build\run-56d48cf76c734e52a5ce937eb21e68c5\nanites-core.jar' dev.nanites.core.Ledger | Select-String 'major version'
```

As pastas anteriores de build foram preservadas, incluindo uma compilação inicialmente incompleta. O script usa UUID por execução para impedir classes antigas de mascarar falhas; esses nomes de diretório são IDs locais de build, não entidades Postman.

### Cobertura relevante

- Exatamente 500 bilhões por caixa; multiplicação, total agregado e limites de long; capacidade, negativos, contas desconhecidas, auto-transferência e zero.
- Lote com erro tardio não altera saldo anterior; snapshots imutáveis; preview sem commit; 10.000 transferências com conservação ou rejeição atômica.
- Regras filtradas por grupos/eventos; candidato só ativa na fronteira tick; candidato inválido nem agenda; não sobrescrever pendente; preservar estado compatível.
- Migração explícita de versões/renomes/drops/defaults; rejeição de perda/colisão/versão errada; rollback da migração e candidato em erro.
- Overflow/estado negativo, limite de ações/eventos e evento desconhecido: sem commit parcial de ledger/estado.
- Definições customizadas de ponte/armadura sem cases por nome; isolante não condutor; conflitos/valores desconhecidos; origens declaradas/adapter desconhecido não viram capacidade verificada.
- Componente mantém combustível/estado/origem/requisitos ao realocar; destino inválido rejeitado; débito contábil checked sem energia fictícia. Fixtures não são adapters reais.
- URL literal exata com HTTPS e sem userinfo/query/fragmento; rejeita também null e esquema em maiúsculas; nenhuma requisição foi enviada.
- Identidade de cache muda por hash/loader/Minecraft/versão loader/decompiler/schema; paths/leituras incrementais limitados; histórico/turnos/ferramentas do contrato de IA.

## Problemas encontrados e correções

A primeira compilação falhou em `Ledger.Reservoir`: o accessor `id()` do record ocultava a resolução de `id(String)`. Corrigido para `Ledger.id(...)`; nenhuma falha foi ocultada. Depois, revisão endureceu o validador do endpoint de equivalência URI para **igualdade literal** e acrescentou casos null/HTTPS maiúsculo. O código final foi recompilado e testado nos dois JDKs, não apenas o código anterior.

## Limites deste resultado

PASS significa **núcleo Java executável e invariantes testadas**, não mod jogável nem segurança de Java arbitrário. Não validamos APIs/build de Forge/Fabric, saves, rede/ACL, HTTP/TLS real, cache/decompiler real, reator de outros mods, benchmark Minecraft ou renderer. Sem blocker de tooling para o core; essas integrações permanecem planejadas e exigem dependências/autorização/testes concretos. Consulte ARQUITETURA.md, INTEGRACOES.md e MARCOS.md.
