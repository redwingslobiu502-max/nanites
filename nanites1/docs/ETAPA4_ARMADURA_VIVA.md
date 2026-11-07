# Etapa 4 — implementação e validação observada

Projeto: `D:\Downloads\nanismo\nanobots\nanites1`, Nanites 0.3.1, Forge 1.20.1-47.4.28, ForgeGradle 6.0.54, Gradle 8.8, Java 17. Não é auditoria de release/publicação.

## Entrega

Resposta adaptativa por família de dano, aprendizado/decay persistido no peitoral, módulos históricos preservados e proteção cobrada uma única vez no ProtectionBudget compartilhado. Reparo de seis slots com ingrediente assimilado real, predicado `Item.isValidRepairItem`, reservas/commit e saldo real; preview sem mutação. Perfil persistente/reload LKG, manutenção opt-in e marcador durável de operação ambígua. Ferramentas armor_status/configure/reload/repair e snapshots integrados ao GameService; goals podem observar/reparar, mas não rearmar configuração automaticamente. Detalhes, custos e comandos em [ARMADURA_VIVA.md](ARMADURA_VIVA.md).

Não resetou arquivos existentes, receitas, etapas de máquinas/assimilação/autonomia, Fabric, dist ou instalação. Arquivos existentes foram modificados com substituição pontual; fontes/documentação novas foram criadas separadamente. O commit de assimilação agora decodifica antes do débito, recusando payload inválido antes de consumir estoque.

## Arquivos exatos desta implementação

Novos:
- `core/src/main/java/dev/nanites/core/LivingArmorTools.java`
- `shared-game/src/main/java/dev/nanites/game/LivingArmor.java`
- `shared-game/src/test/java/dev/nanites/game/LivingArmorTests.java`
- `forge/src/test/java/dev/nanites/forge/ForgeLivingArmorTests.java`
- `docs/ARMADURA_VIVA.md`
- `docs/ETAPA4_ARMADURA_VIVA.md`
- `tools/validate-living-armor.cmd`
- `tools/offline-merge-api.init.gradle`

Editados:
- `core/src/main/java/dev/nanites/core/MachineTools.java`
- `shared-game/src/main/java/dev/nanites/game/GameService.java`
- `shared-game/src/test/java/dev/nanites/game/AssimilationTests.java`
- `shared-game/build.gradle`
- `forge/src/main/java/dev/nanites/forge/ModularArmor.java`
- `forge/src/main/java/dev/nanites/forge/NanitesForge.java`
- `forge/src/main/java/dev/nanites/forge/ForgeAssimilation.java`
- `forge/build.gradle`

## Comando final e ambiente

Executado da raiz:

```bat
tools\validate-living-armor.cmd
```

O script usa JAVA_HOME `C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot`, GRADLE_USER_HOME `build\review-gradle-home`, TEMP/TMP `build\review-temp`, JAVA_TOOL_OPTIONS `-Djava.io.tmpdir=D:/Downloads/nanismo/nanobots/nanites1/build/review-temp -XX:ActiveProcessorCount=2` e o Gradle 8.8 existente. Invocação efetiva:

```bat
"C:\Users\Andrey\.gradle\wrapper\dists\gradle-8.8-bin\dl7vupf4psengwqhwktix4v1\gradle-8.8\bin\gradle.bat" -I "D:\Downloads\nanismo\nanobots\nanites1\tools\offline-merge-api.init.gradle" -Ploader=forge :core:check :shared-game:check :forge:build --offline --no-daemon --max-workers=2 --console=plain
```

Saída final: `validation-logs/stage4-armor-final.log`, **BUILD SUCCESSFUL in 1m 12s**, **29 actionable tasks: 17 executed, 12 up-to-date**; wrapper confirmou **Offline validation exit code: 0**. Compile de produção e testes, jar/reobfJar e checks Forge concluídos. Após esse build somente documentação foi atualizada.

| Suite | Resultado observado |
|---|---:|
| CoreTests | 33 |
| ExpansionTests | 57 |
| AssimilationTests | 1087 |
| AutonomyTests | 83 |
| ConstructionTests | 252 |
| GameTests | 51 |
| LivingArmorTests | 46 |
| MachineTests | 84 |
| RuntimeTests | 91 |
| ForgeAssimilationTests | 11 |
| ForgeAttributeTests | 15 |
| ForgeLivingArmorTests | 13 |

Todos passaram: 1823 checks/casos reportados, incluindo 59 novos checks de armadura viva. As suites determinísticas usam fakes nas fronteiras de mundo/modelo; os testes Forge usam objetos reais de NBT/SavedData/atributos/itens sem lançar jogo. A mensagem esperada `Nanites VFX failed after accounting commit: VFX failure` pertence ao caso de falha de VFX de GameTests, que passou.

Artefato gerado e confirmado por `dir forge\build\libs`: `forge/build/libs/nanites-forge-1.20.1-0.3.1.jar`, 395018 bytes. Não copiado/publicado. Jars antigos 0.2.0/0.3.0 na pasta não foram removidos.

## Falhas anteriores corrigidas e limites

- Falha offline anterior: índice do cache não encontrava `net.minecraftforge:mergetool:1.1.5:api`, embora o jar/POM reais existissem no cache do usuário. O script copia esses artefatos existentes para `build/offline-maven` e o init script limita esse repositório ao módulo exato. Sem download, substituição de versão ou dependência fictícia.
- Primeiro teste Forge tentou Bootstrap completo e falhou em NetworkHooks por ausência de transformação EventBus no JavaExec. O fixture final inicializa somente registries e marca por reflexão o guard privado de bootstrap. NBT, itens e predicados em teste continuam reais; não é teste de launcher/EventBus nem alteração de produção.
- Avisos de APIs Forge/Gradle depreciadas e terminal avançado permanecem. Não são erros de build.
- Nenhum Minecraft cliente/servidor/mundo, modelo real ou curl foi executado; não houve validação em jogo nem promessa de compatibilidade universal com mods. Eventos posteriores e predicados/capacidades de terceiros precisam de teste real.
- Reparo usa durabilidade e predicado público; não executa receitas privadas, não recarrega FE, não certifica capacidades de mods.
- Marcador de ambiguidade evita replay cego, mas não oferece ACID/fsync entre perfil, SavedData, saldo e arquivo do jogador. Após queda, inspecione estado antes de rearmar com versão maior.
- Auditoria de release/publicação e validação em jogo permanecem para invocações separadas, conforme solicitado.
