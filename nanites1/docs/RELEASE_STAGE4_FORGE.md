# Auditoria da entrega local — quatro etapas Nanites Forge

Auditado em 07/11/2026, sem Minecraft, servidor, modelo ou rede. Foram lidos `ETAPA4_ARMADURA_VIVA.md`, `ARMADURA_VIVA.md`, os guias anteriores, fontes de integração/comandos e logs finais; o JAR foi aberto e inspecionado, não apenas renomeado.

## Artefato instalável

```text
D:\Downloads\nanismo\nanobots\nanites1\dist\stage4-armor-20261107\nanites-forge-1.20.1-0.3.1-stage4.jar
395018 bytes
SHA256 717388E83D3C735D0D4B94B8658CD40C9C5B10D988FBDDF80DCDAF0E19900B94
```

O conteúdo é cópia binária exata de `forge/build/libs/nanites-forge-1.20.1-0.3.1.jar` **e** `forge/build/reobfJar/output.jar`: SHA256 e tamanho iguais. A versão interna permanece **0.3.1**; `stage4` é diferenciação de distribuição, não bump fictício. Nenhuma fonte de produção foi modificada/recompilada nesta auditoria; não foi encontrado defeito de produção que exigisse rebuild. Novos arquivos de auditoria/documentação e apontadores foram acrescentados sem reset de alterações do usuário.

Os **cinco arquivos preexistentes em dist** foram comparados antes/depois por tamanho/SHA256 e preservados, inclusive `dist/nanites-forge-1.20.1-0.3.1.jar` (262625 bytes). Não substitua esse arquivo pelo novo build nem instale os dois na mesma instância.

## Verificações executadas nesta auditoria

- ZIP sem entradas duplicadas; **167 classes**, todas magic CAFEBABE/major **61** (Java 17). Sem classes de testes, Gson duplicado, Minecraft/Forge embutidos ou loader Fabric.
- `META-INF/mods.toml` real: `modId=nanites`, `version=0.3.1`, `javafml`, loader `[47,)`, Forge obrigatório `[47.4.28,)`, Minecraft obrigatório `[1.20.1,1.20.2)`, ambos lados. Manifest mínimo `Manifest-Version: 1.0`; ele não declara bump de versão.
- Reobf comprovado por execução `:forge:reobfJar` no log final, igualdade binária com seu `output.jar` e referências Minecraft `m_<n>_` nos classfiles Forge (56 em ForgeMachines, 33 em ModularArmor, 14 em NanitesForge). ForgeGradle 6.0.54 / mappings official 1.20.1 / `jar.finalizedBy reobfJar` conferidos.
- Classes de produção incluídas: `ForgeMachines`/`CapabilityTransfer`/`MachineAdapters`, `ForgeAssimilation`/`AssimilationLedger`, `AutonomousGoals`, `LivingArmor`/`ModularArmor`/`ProtectionBudget`, `GameService`, ferramentas core dos quatro estágios. Ligações de capabilities ITEM_HANDLER/ENERGY/FLUID_HANDLER e métodos reais de extração/inserção/drenagem/enchimento presentes, inclusive classes anônimas; GameService liga os três serviços persistentes e ferramentas de armadura.
- **21 JSON/pack.mcmeta** parseados, nove receitas e nove modelos de itens preservados (caixa/reservatório, quatro peças e três módulos).
- Nenhum arquivo de produção/recursos em `core/src/main`, `shared-game/src/main`, `forge/src/main` ou `game-resources` posterior ao JAR. Hash do build não mudou na cópia.
- Gate/cópia: `tools/audit-stage4-release.ps1`; evidência aprovada: `validation-logs/stage4-release-audit-passed-20261107.log` (PowerShell UTF-16). Tentativas anteriores de auditoria ficaram em logs separados: foram corrigidas suposições do auditor sobre saída do wrapper/classe anônima, **não defeitos do mod**; nenhuma delas publicou o JAR.

## Evidências de build revisadas (não reexecutadas)

| Log final | Resultado |
|---|---|
| `validation-logs/stage1-machines-final.log` | BUILD SUCCESSFUL, 38s, 24 tasks |
| `validation-logs/stage2-assimilation-final.log` | BUILD SUCCESSFUL, 39s, 26 tasks |
| `validation-logs/stage3-autonomy-final.log` | BUILD SUCCESSFUL, 59s, 27 tasks |
| `validation-logs/stage4-armor-final.log` | BUILD SUCCESSFUL, 1m12s, 29 tasks (17 executadas/12 up-to-date), jar/reobfJar/check |

Validação final por `tools\validate-living-armor.cmd`: Java 17, cache offline, `:core:check :shared-game:check :forge:build`, total reportado **1823 checks/casos**, incluindo **59 de armadura** (46 shared-game + 13 Forge). Suites: core 33+57, assimilação 1087, autonomia 83, construção 252, jogo 51, máquinas 84, runtime 91, Forge assimilação 11, atributos 15. O exit code 0 do wrapper consta no relatório da implementação, não no log Gradle redirecionado. Avisos de depreciação/terminal e falha de VFX intencional no teste não são falhas de produção.

## Instalar e conferir sem lançar nada

PowerShell na raiz:

```powershell
Set-Location 'D:\Downloads\nanismo\nanobots\nanites1'
$jar = 'dist\stage4-armor-20261107\nanites-forge-1.20.1-0.3.1-stage4.jar'
Get-Item $jar | Select-Object FullName,Length
Get-FileHash $jar -Algorithm SHA256
Get-FileHash 'forge\build\libs\nanites-forge-1.20.1-0.3.1.jar' -Algorithm SHA256
Get-Content 'validation-logs\stage4-release-audit-passed-20261107.log' -Tail 7
```

Com a instância **fechada**, backup do mundo e Java 17/Forge 47.4.28/Minecraft 1.20.1, copie só o JAR stage4 para `mods` (servidor e clientes). Remova versões Nanites anteriores **da instância**, não de dist. Nenhuma instalação no launcher foi realizada automaticamente. O gate de publicação recusa destino já existente; não o execute esperando sobrescrita. Rebuild futuro muda o artefato: exija novos testes/auditoria/hash e outra subpasta antes de publicar.

## Testes ainda necessários pelo usuário

Siga [TESTE_QUATRO_ETAPAS_FORGE.md](TESTE_QUATRO_ETAPAS_FORGE.md): comandos reais de transferências/assimilar/recarregar adapters, fábrica vanilla persistente e configuração/reparo/adaptação de armadura; [INSTALL.md](../INSTALL.md) tem o apontador atual.

**Não certificado:** gameplay/rendering/EventBus, IA/modelo real e compatibilidade de cada modpack. Testes determinísticos usam fakes; fixture Forge usa registries/NBT/itens reais sem launcher. Capabilities públicas e adapters não implementam APIs privadas universais; reparo usa durabilidade/predicado público, não FE/receitas privadas. Reserva/marcador/checkpoint não é ACID/fsync conjunto entre saves. Ambiguidade exige inspeção e rearmamento/update versionado, sem replay cego. Consulte os quatro guias para custos, quotas, LKG e limites completos.
