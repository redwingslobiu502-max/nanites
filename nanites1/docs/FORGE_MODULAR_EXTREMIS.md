# Forge 1.20.1 — base modular/runtime/Extremis 0.3.0

**Implementação de base Forge-only, não certificação de gameplay.** A entrega auditada é condicionada ao gate dedicado; o relatório final `../dist/VALIDATION-FORGE-0.3.0.txt` é a autoridade para hash/status de publicação. Interoperabilidade com outros mods é **parcial, não universal**. Somente Forge 47.4.28 / Java 17 ganha os itens/backend. Código Fabric e jevmod não foram editados; defaults novos de Host são inertes. `dist` antigo 0.2.0 não foi substituído. Não foi iniciado Minecraft, aceito EULA, instalado modpack ou consultada IA real.

## Estado A–D

| Área | Base implementada | Limitação explícita |
|---|---|---|
| A | Quatro ArmorItem IRON vestíveis/craftáveis, visual vanilla; peitoral com três módulos consumidos, NBT validado, proteção paga no servidor | Sem GUI/visual customizado, módulos permanentes no item; tirar peitoral desativa seus efeitos. NBT/equipamento/crafting reais precisam QA em jogo |
| B | Extremis opt-in: regeneração até 1 HP/s, velocidade +10% da base, ataque +1; TTL e opt-out removem overlays | Não é biologia universal dos quadrinhos, ressurreição, imunidade ou tecnologia arbitrária. Probe OP somente presença de capabilities |
| C | Perfis p0..p7 definidos/redefinidos on-the-fly em dados, overlays/guard/regen; comandos e `emit intervene.pN self = segundos` | Somente o próprio ServerPlayer carregado. Sem alvos externos, consentimento/ownership, inventário de entidades ou internals universais |
| D | Ferramentas define_profile/apply_script/start_intervention/cancel_intervention executam após grant explícito do dono, via host.main com revalidação e recibos | apply_script delegado **somente estado**, sem emit/transfer; efeitos de mundo via leases. Grants OP para outros donos não implementados. Fake não certifica LLM real |

## Preparação e itens

Carregue `nanites:reservoir` e use uma `nanites:box` na mão principal: caixa consumida deposita 500.000.000.000 nanites. O crafting de armadura consome outra caixa, não deposita saldo.

Receitas shapeless, uma unidade de cada ingrediente:

- `minecraft:iron_helmet` + `nanites:box` → `nanites:modular_helmet`.
- `minecraft:iron_chestplate` + caixa → `nanites:modular_chestplate`.
- `minecraft:iron_leggings` + caixa → `nanites:modular_leggings`.
- `minecraft:iron_boots` + caixa → `nanites:modular_boots`.
- Ferro + slime_ball + redstone → `nanites:module_cushioning`.
- Ferro + shield + redstone → `nanites:module_impact`.
- Ferro + white_wool + redstone → `nanites:module_thermal`.

Vista o peitoral; segure o módulo correspondente e execute:

```text
/nanites armor install cushioning
/nanites armor install impact
/nanites armor install thermal
/nanites armor list
```

Instalação consome exatamente um módulo, inclusive em criativo; duplicatas/itens errados/slot excedente são rejeitados. Módulos não são devolvidos. NBT `NanitesModules` aceita somente três strings únicas da whitelist. Efeitos consultam o equipamento/stack autoritativo do servidor e validam a whitelist; cliente não escolhe atributos, custo ou dono. Não há assinatura/prova de origem do NBT: criação/edição privilegiada de itens (OP/criativo/outros mods) permanece uma fronteira de confiança e exige QA separado.

Cushioning reduz até 4 HP de queda; impact até 2 HP de outros danos **não fogo**; thermal até 2 HP de fogo, somente com material isolante explicitamente absorvido (`/nanites absorb` segurando `minecraft:white_wool`, ou mapeamento do operador). O crafting com lã não equivale à absorção. **Cap global por dono: 8 HP por bucket de 20 ticks de servidor, compartilhado por escudo legado + módulos + guard runtime** (não inclui armadura vanilla/efeitos de outros mods). Escudo/módulos custam 1.000.000 nanites/HP bloqueado, debitado antes da redução; guard usa reserva antecipada da lease. Sem saldo, escudo/módulo não bloqueia. Ordem: shield → módulos → guard; nenhum caminho pode exceder o saldo de proteção global. Em Forge, escudo de fogo exige `protect.insulating` e material absorvido; `protect` genérico não contorna a restrição. Guard de fogo também exige esse material. Remoção de módulo/equipamento não recria orçamento.

## Perfis runtime: alteração real sem recompilar

```text
/nanites runtime define p0 0.1 1 0 0 2 1
/nanites runtime list
/nanites runtime start p0 10
/nanites runtime cancel
/nanites runtime define p0 0.05 0 2 0.1 1 0
/nanites runtime start p0 5
```

Ordem dos seis números: `speed attack armor knockback guard regen`.

| Campo | Cap | Efeito |
|---|---|---|
| speed | 0..0.10 | MOVEMENT_SPEED MULTIPLY_BASE (+10% da base no máximo) |
| attack | 0..1 | ATTACK_DAMAGE ADDITION |
| armor | 0..2 | ARMOR ADDITION |
| knockback | 0..0.10 | KNOCKBACK_RESISTANCE ADDITION |
| guard | 0..4 HP | Redução máxima por hit; budget total guard × segundos |
| regen | 0..1 HP | Um pulso no máximo a cada 20 ticks; nunca desfaz HP curado |

Caps limitam o overlay deste mod, não sobrescrevem atributos/potions/efeitos legítimos de outros mods. Somente UUID da lease é removido; não se usa setBaseValue para aplicar/restaurar. Atributos inexistentes e alvo indisponível falham antes do débito. Na falha parcial do backend há cleanup sem reembolso. `NaN`, infinitos e negativos são rejeitados.

Uma lease por dono, TTL 1..60 segundos (20 ticks de servidor por segundo nominal; com lag pode durar mais em relógio real). Timer monotônico do servidor, independente de dimensão/horário do mundo. Reemit/stacking durante lease é rejeitado, não prolonga nem duplica. Custo integral reservado antecipadamente, incluindo manutenção por toda a duração:

```text
1.000.000 + segundos × max(1, ceil((speed×100 + attack + armor + knockback×100 + guard + regen) × 1.000.000))
```

Sem upkeep ilimitado: terminado TTL, é preciso iniciar outra lease pagando outra vez. Pode haver menos pulsos que a duração nominal, pois não há catch-up e o tick de expiração não cura.

Perfis/leasing/grants não persistem nem retomam automaticamente. Logout, morte, mudança de dimensão, saída da entidade/stop removem overlays e esquecem perfis/opt-in/grants. Revogar grant também cancela lease. Código de script pode persistir, mas perfil referido precisa ser redefinido depois de novo login. Remover lease não reembolsa recursos nem desfaz cura/danos já ocorridos. **Falha de ativação** remove somente os UUIDs próprios e compensa o débito integral, sem criar saldo: restaura a sessão pré-débitada. Falha de DSL intervenção restaura também estado/contabilidade do evento antes de VFX. Hot-swap delegado é preparado em fork, sem mutar sessão viva antes do commit. Tentativas autorizadas que falham ainda consomem chamada/budget do grant (limite anti-retry), não recursos do dono. Cancelamento de agente/timeout cancela future pendente; cancel/main commit são linearizados: cancelamento que vence antes do início impede execução, mas não desfaz efeitos já confirmados por recibo.

### Script manual

Primeiro defina p0, depois:

```text
/nanites edit program rt version v1; state hits = 0; on impact group owner { if (event.damage > 0) { add hits = 1; emit intervene.p0 self = 10; } }
/nanites apply
```

Também existe `examples/forge-runtime.nano`. Use export/example para criar o arquivo do dono, coloque o conteúdo pelo editor e use `/nanites load nome`. Não são aceitos caminhos fornecidos pelo jogador. O script autoriza apenas self, com oito slots fixos de capabilities cujo **conteúdo é runtime**. Primitivas suportadas não exigem adaptador por ajuste. Perfil indefinido falha no evento. Preview/ativação não executam world intents. Dispatch distingue protect, shapes e interventions; novas ações não viram partículas por engano.

## Extremis reversível

```text
/nanites runtime cancel
/nanites extremis on 30
/nanites extremis off
```

`on segundos` é o opt-in explícito; nenhum login ou agente habilita Extremis sozinho. Usa lease com +10% speed, +1 attack e até 1 HP/s. Custo: 1.000.000 + segundos × 12.000.000. `off` cancela overlays e revoga opt-in; cura já feita permanece. Sem restauração de valores base e sem remoção de efeitos de terceiros.

```text
/nanites tech
```

Somente OP nível 2, raio cúbico 2, até 16 block entities carregadas com presença de Forge ENERGY/ITEM_HANDLER/FLUID_HANDLER. Sem force-load, conteúdo de inventários, extração/inserção, energia criada, escrita em máquinas ou garantia de ownership/compatibilidade universal. Não é integração VERIFIED para mods externos.

## IA executora com grant delegado

Modo default continua manual/proposal-only. Configurar modelo/servidor TLS é operação do administrador, fora desta validação; token não foi lido e nenhuma chamada real foi feita aqui. Se for usar IA depois, configure explicitamente o endpoint loopback HTTPS já previsto pelo projeto e um modelo, mantendo TLS.

```text
/nanites grant self 60 8 100000000
/nanites ai Defina p0 com speed 0.05 attack 0 armor 0 knockback 0 guard 1 regen 0 e ative por 5 segundos usando as ferramentas; consulte catalog antes.
/nanites runtime list
/nanites revoke
```

Grant: somente dono autenticado para self; duração 1..60s, 1..8 chamadas mutantes, budget 1..100.000.000 nanites. Não deposita nanites; precisa saldo real. Uma nova concessão invalida token anterior e remove lease anterior. A IA não recebe ferramenta para se conceder autoridade, selecionar UUID arbitrário, ler segredos, executar Java/processos, reescrever JAR ou carregar classes.

Ferramentas mutantes só entram no catálogo de uma execução com grant ativo:

- `define_profile`: `{profile, speed, attack, armor, knockback, guard, regen}`; p0..p7, custa 1.000.000.
- `start_intervention`: `{profile, seconds}`; paga preço completo, cabe no budget **e no TTL restante do grant**.
- `cancel_intervention`: `{}`; consome chamada, custo zero.
- `apply_script`: `{source}`; hot-swap real de código **somente estado, sem palavras emit/transfer**, custa 1.000.000. Código pode persistir; rollback manual restaura código sem reembolsar ledger. Scripts com world/accounting actions precisam revisão manual nesta base.

Mutação é agendada em host.main; worker aguarda Future bounded, não bloqueia tick. Antes de executar revalida online/alvo, lifecycle, token/expiração/calls/budget e revisão de sessão/perfis. Cada sucesso devolve `EXECUTED_SERVER_MAIN`, revisões novas e custo; apenas os recibos avançam revisões esperadas deste run. Depósito/evento/comando concorrente pode invalidar run, sem aceitar resultado obsoleto. Uma ferramenta em fila após revoke/logout/stop/cancel não ganha autoridade. Erros podem consumir chamada/budget autorizado; não se devolvem custos consumidos. Leituras state/read_script são snapshot pré-run, recibos representam o estado novo. Confirme mensagens `IA executou ...`, não só o texto livre da IA.

Sem grant: `/nanites ai` mantém proposta → `/nanites proposal 0` → revisar → `/nanites apply`.

## Build/check e evidência

Na pasta `nanites`, PowerShell explícito:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'
$env:TEMP = Join-Path (Get-Location) 'build\review-temp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Force $env:TEMP | Out-Null
.\gradlew.bat -Ploader=forge :core:check :shared-game:check :forge:build --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Build/check falhou' }
```

Usa cache Gradle original, sem copiar caches/mundos. Não execute build.ps1/audit.ps1 antigos para publicar este lote: exigem os dois loaders e ainda têm regras 0.2.0. JAR de desenvolvimento esperado: `forge/build/libs/nanites-forge-1.20.1-0.3.0.jar`. Publicação em dist/auditoria final continuam separadas.

Validação executada: **BUILD SUCCESSFUL**, Java 17, comando Forge acima; log `validation-logs/forge-runtime-0.3.0-check.log`. CoreTests **33**, ExpansionTests **57** (FAKE_TRANSPORT_ONLY), GameTests **50**, RuntimeTests **69**, ForgeAttributeTests **10**: **219 checks Java**. RuntimeTests cobre caps/owner/grant/revoke/TTL/custos/lifecycle/guard/regen/cleanup/conflitos/recibos/perfis/script typed/fake autoexec e política de módulos. ForgeAttributeTests usa AttributeInstance/RangedAttribute reais isolados (sem registro global/bootstrap ou mundo) para preservação de modifiers/base concorrentes e operação speed. Um primeiro teste com Attributes globais falhou por ausência de bootstrap; corrigido para objetos reais isolados e backend com lookup lazy. Teste fake não prova LLM/gameplay reais. Warnings de deprecação Forge/Gradle e terminal não bloquearam o build.

Verificação read-only executada: `powershell -NoProfile -File tools/verify-forge-base.ps1`, **30 checks** de receitas/modelos/traduções/metadata/bytecode Java17; não é auditoria completa de release. JAR de desenvolvimento validado:

- `forge/build/libs/nanites-forge-1.20.1-0.3.0.jar`: **226598 bytes**, SHA256 `51E2142210187A92910E2C3CB26626D6397CF7F8B384D054D871F050DF91B756`.
- `dist/nanites-forge-1.20.1-0.2.0.jar` preservado: **195121 bytes**, SHA256 `E86FE74A2BACEEE435EB7915DC79BDE24A0C82A99492CAA5578D533B8001A1FD`.
- `dist/nanites-fabric-1.21.1-0.2.0.jar` preservado: **198748 bytes**, SHA256 `6A3A34CEF07CF67D082BD6BACAD0FAA82DAD10CDE2B2D0866B1BD47B52381731`.

Comandos runtime/grant e gateway são exercitados no service com Host fake; política de opt-in/TTL/reversão Extremis é exercitada diretamente. Comandos Forge armor/tech têm código compilado e política testada, **não execução em jogo**. Receitas/modelos foram conferidos estruturalmente no JAR, mas crafting/visual real ficam para QA posterior.

## Arquivos/APIs deste lote

Novos:

- `shared-game/src/main/java/dev/nanites/game/RuntimeInterventions.java`: Profile, Lease, Grant, Backend; define/start/tick/damage/cancel/grant/authorize/revoke/forget.
- `shared-game/src/main/java/dev/nanites/game/ArmorModules.java`: política validada install/validate/reduction.
- `forge/src/main/java/dev/nanites/forge/ForgeRuntime.java`: transient modifiers e probe read-only.
- `forge/src/main/java/dev/nanites/forge/ModularArmor.java`: instalação/NBT/equipamento/guard pago.
- `shared-game/src/test/java/dev/nanites/game/RuntimeTests.java`.
- `forge/src/test/java/dev/nanites/forge/ForgeAttributeTests.java`.
- `forge/src/main/resources/data/nanites/recipes/{modular_helmet,modular_chestplate,modular_leggings,modular_boots,module_cushioning,module_impact,module_thermal}.json`.
- `forge/src/main/resources/assets/nanites/models/item/` com os mesmos sete stems `.json`.
- `examples/forge-runtime.nano`, `tools/verify-forge-base.ps1` (somente leitura) e este guia.
- `validation-logs/forge-runtime-0.3.0-check.log`: evidência do build/check final.
- `validation-logs/forge-runtime-0.3.0-resources.log`: verificação estrutural e hashes.

Compatibilidade adicional Fabric: **BUILD SUCCESSFUL** em `-Ploader=fabric :fabric:compileJava`, com JDK21 já instalado (`C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`), log `validation-logs/forge-runtime-fabric-compatibility.log`. Não foram editados fonte/versão Fabric nem gerado/publicado seu JAR. A primeira tentativa Java17 foi rejeitada pelo Loom (MC1.21.1 exige Java21); a tentativa correta recompôs automaticamente o cache Loom e emitiu warnings de package-info duplicado de dependências, mas compilou. Backend Forge permanece desabilitado pelos defaults em Fabric. Aprovação de build Forge continua Java17, não Java21.

Editados:

- `core/src/main/java/dev/nanites/core/ControlledTools.java`: gateway assíncrono opcional, quatro schemas/tool defs mutantes, allowlist manual original preservada.
- `core/src/main/java/dev/nanites/core/MultiTurnAgent.java`: contexto delegado vs manual.
- `shared-game/src/main/java/dev/nanites/game/GameService.java`: Host defaults inertes, política Forge, dispatch typed, custos/runtime/comandos/gateway/recibos/cleanup.
- `forge/src/main/java/dev/nanites/forge/NanitesForge.java`: registros reais, Host backend, comandos e eventos de lifecycle.
- `shared-game/build.gradle`, `forge/build.gradle`: novos checks; **version 0.3.0 só Forge**.
- `forge/src/main/resources/META-INF/mods.toml`: versão Forge 0.3.0.
- `game-resources/common/assets/nanites/lang/{en_us,pt_br}.json`: traduções compatíveis, sem registros novos em Fabric.

## Pendências da próxima etapa

1. QA controlado em jogo: crafting/vestir/modelos, NBT inválido, consumo módulo/caixa, dano/cancelamentos de outros mods, sync atributos, pulses e logout/morte/dimensão/stop; não foi iniciado cliente/servidor nesta sessão.
2. Auditoria final do JAR/reobf e publicação Forge-only com script dedicado; preservar Fabric/dist 0.2.0.
3. Se necessário: alvos externos com consentimento/ownership verificável e grants OP explícitos; não aceitar UUID arbitrário.
4. Ferramenta apply_script com world actions temporárias exigiria lifecycle de código delegado, budget por evento e rollback TTL; nesta base usa leases executoras para mundo e rejeita emit/transfer no hot-swap delegado.
5. GUI módulos/Extremis, remoção com regra de recursos, mais primitivas/attributes whitelist após validação específica; nenhuma promessa de modificar qualquer mod/internal.
