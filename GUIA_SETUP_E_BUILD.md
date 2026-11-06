# Nanites — guia de setup, build e revisão real

Revisão de 06/11/2026, exclusivamente de `nanites` 0.2.0. A raiz deste guia é `D:\Downloads\nanobots mod`; o projeto está em `nanites/`. `jevmod` é somente referência/base histórica: **não é dependência para instalar Nanites e não foi buildado nem alterado nesta revisão**.

**Conclusão de escopo:** há implementação concreta de itens, saldo, scripts, escudo e partículas nos dois loaders; não há implementação completa de todos os objetivos do roadmap. Compilar, executar o serviço sem Minecraft e auditar JARs são evidências diferentes de testar o jogo. Não trate planos antigos em `docs/` como funcionalidades entregues.

## 1. Instalar e iniciar, sem IA

Use duas instâncias separadas, nunca os dois JARs na mesma pasta `mods`.

| Minecraft | Java da instância | Loader/dependências fixados no projeto | Mod correto |
|---|---|---|---|
| 1.20.1 | JDK/JRE 17 | Forge 47.4.28 | `nanites/dist/nanites-forge-1.20.1-0.2.0.jar` |
| 1.21.1 | JDK/JRE 21 | Fabric Loader 0.16.14 **e Fabric API 0.116.17+1.21.1** | `nanites/dist/nanites-fabric-1.21.1-0.2.0.jar` |

Para compilar precisa de **JDK**, não só JRE. Não use o Java 8 padrão do sistema. Instale/configure manualmente o loader da instância; Forge: https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html ; Fabric: https://fabricmc.net/use/installer/ . Fabric API é dependência separada, não embutida.

1. Feche o jogo; faça backup conjunto de mundo e `config/nanites` antes de mexer em saves existentes.
2. Copie apenas o JAR correto de `nanites/dist` para `mods` da instância. Não instale `nanites-core.jar`, `fabric-0.2.0.jar`, JAR `-dev` ou Gson avulso.
3. Em multiplayer, use a versão correspondente no servidor e clientes. Isso ainda precisa de teste real de cliente/servidor.
4. Inicie manualmente um **mundo novo de teste**, primeiro sem outros mods além das dependências obrigatórias. A automação desta revisão não instala perfis nem abre Minecraft.
5. Entre no mundo e digite `/nanites status`. O serviço cria os arquivos de configuração ao iniciar o servidor integrado/dedicado.

Confira a seção de validação ao fim antes de confiar na pasta `dist`: arquivos antigos existentes não provam que um build novo passou.

## 2. Itens, receitas e depósito

Receitas confirmadas nos JSONs dos dois loaders: Forge usa `data/nanites/recipes`, Fabric 1.21.1 usa `data/nanites/recipe`; o resultado Fabric usa `id`.

### Caixa — `nanites:box`

Na bancada 3×3:

```text
I R I
R G R
I R I
```

`I` = barra de ferro (4), `R` = pó de redstone (4), `G` = barra de ouro (1). Resultado: **uma caixa**, carga fixa de **500.000.000.000 nanites**; stack máximo 16.

### Reservatório — `nanites:reservoir`

```text
I . I
R V R
I I I
```

`.` = vazio; ferro (5), redstone (2), vidro (1). Resultado: um reservatório, stack máximo 1. É **item de acesso ao saldo do jogador naquele mundo**, não recipiente com saldo portátil em NBT.

### Usar

Com cheats, somente para diagnóstico:

```text
/give @s nanites:reservoir 1
/give @s nanites:box 2
/nanites status
```

Tenha o reservatório nos slots normais do inventário e a caixa na **mão principal**; clique direito no ar. O loader busca o reservatório em `inventory.items`, não em armadura/mão secundária. Cada uso aceito consome **uma caixa, inclusive no Creative**, e deposita 500 bilhões. Cooldown de 10 ticks (~0,5 s a 20 TPS); segurar o botão pode consumir caixas em usos sucessivos. Sem reservatório, stack vazio ou uso de caixa na mão secundária: depósito recusado.

Saldo `storage` e gasto `spent` são por UUID/mundo. Transferir o item reservatório a outro jogador não transfere recursos. O item é exigido **no depósito**; a lógica atual não o exige para continuar protegendo depois de abastecer. Não há retirada/reembolso. Cada reservatório contábil tem limite de 10 trilhões; `spent` é irreversível e também tem teto: não é armazenamento infinito nem gasto vitalício ilimitado. Cheats/admin podem criar caixas vanilla; isso não é defesa contra administradores.

## 3. Escudo, partículas e material

O script inicial já está ativo, sem IA:

- `fall`: solicita até **20 pontos de HP** de redução e anel de raio paramétrico 2. Custo solicitado: **20.200.000** nanites/evento.
- `impact`: até **4 HP** e anel 1. Custo **4.100.000**.
- A redução total é limitada a 20 HP/evento. Sem saldo suficiente ou regra rejeitada não há proteção nem emissão desse evento.
- Custos são por ação **solicitada**, não por dano efetivamente evitado; queda de 8 HP ainda solicita 20. O efeito pode ser suprimido pelo budget visual sem reembolso.
- Anel/coluna usam partículas vanilla `END_ROD`, **não blocos sólidos, entidades construtoras, armadura custom ou OBJ**. Limite: 24 partículas por dono/20 ticks e budget global 256/tick (emissões em grupos de 24). Configure partículas “Todas” e veja em terceira pessoa.
- `protect` dentro de `tick` custa recursos mas **não acumula escudo para dano futuro**. Coloque proteção em handlers de dano.

Material opcional:

```text
/nanites material
# segurando lã branca na mão principal:
/nanites absorb
```

Absorção consome um item **exatamente mapeado pelo operador**; padrão `minecraft:white_wool`. Registra UUID, registry, contagem (até 16), revisão e evidência em `<mundo>/nanites/materials/<UUID>.json`. Pedra não mapeada deve falhar sem consumo. Isso é regra explícita do Nanites, **não inferência de física da lã ou certificação de qualquer mod**. Trocar o mapa invalida material anterior diferente.

Carregue `game-insulating.nano` conforme a próxima seção: somente fogo (`event.fire == 1`) recebe até 3 HP de redução, com custo 3.200.000 e coluna. Sem material e saldo, não funciona. Esse exemplo substitui o escudo padrão; não mantém automaticamente proteção de queda.

`/nanites reactor` retorna **UNSUPPORTED**. Não existe extração/produção de energia, absorção geral de reator, combustível externo ou integração concreta com mods energéticos.

## 4. Programar, importar e trocar scripts

É uma DSL limitada, **não Java executável**. Não existe editor gráfico dedicado. Comandos são autenticados pelo UUID do jogador, sem parâmetro para alvo/dono arbitrário.

```text
/nanites example meu_script
/nanites preview meu_script
/nanites load meu_script
/nanites export backup
```

`example` cria o script padrão em `config/nanites/scripts/<UUID>/meu_script.nano` **da instância/servidor**, não deste repositório. Em servidor remoto, o operador precisa editar o arquivo no servidor. UUID aparece em status. Use editor de texto UTF-8 e copie o conteúdo de um exemplo de `nanites/examples/` para esse arquivo. `example` e `export` não sobrescrevem arquivos existentes.

- `game-shield.nano`: equivalente ao escudo padrão.
- `game-structure.nano`: coluna de partículas a cada 40 ticks (~2 s), custo 300.000/emissão, além de proteção de queda. Não constrói blocos.
- `game-insulating.nano`: proteção de fogo dependente de material.
- **`fall.nano` é demo histórica do core**, com `builders`, `structure` e capacidades não autorizadas no jogo; não use como script Minecraft.

Nomes: 1–48 caracteres, somente letras/números/`_`/`-`; sem extensão no comando, caminhos ou `..`. Arquivos até 64 KiB; symlinks/traversal recusados.

`preview` simula queda de 8 HP isoladamente; não debita saldo nem aplica partículas. **Não valida todos os eventos, material/saldo reais ou rendering**; um preview aceito ainda pode ter evento real rejeitado. `load` valida e ativa hot-swap; intents do preview não são executados na ativação.

Edição curta no chat:

```text
/nanites edit program inert version v1; state hits = 0;
/nanites apply
/nanites rollback
```

`edit` prepara texto; `apply` ativa após validação. `rollback` troca somente o código anterior **desta sessão**; nunca devolve saldo, rebobina mundo ou desfaz absorção. Histórico/preparações/propostas são descartados no logout/restart. Exportação salva código, não recursos.

### Subconjunto de linguagem aceito no jogo

```text
program exemplo version v1;
state hits = 0;
on fall group owner {
  if (event.damage > 0) {
    add hits = 1;
    emit protect self = 4;
    emit shape.ring self = 1;
  }
}
```

DSL suporta estados inteiros, expressões/condições, funções, `repeat` limitado, `add`, `transfer` e `emit`. A presença de sintaxe no core não concede capacidade externa no jogo. Preserve campos/schema compatíveis, por exemplo `state hits`, ao editar; mapas de migração complexa do core não são expostos por comandos Minecraft.

| Fronteira real | Valores |
|---|---|
| Grupo / alvo | `owner` / `self` |
| Eventos | `fall`, `impact`, `tick` |
| Campos de dano | `damage`: centésimos de HP; `distance`: centésimos de bloco, limitado a 4096 unidades; `fire`: 0/1 pela tag real |
| Tick | servido a cada 20 ticks; `age` = gameTime em ticks |
| Capacidades | `protect`, `protect.insulating`: 0..20; `shape.ring`, `shape.column`: 1..4 |
| Custos | proteção: 1.000.000/unidade; forma: 100.000/unidade |
| Quotas | 4 eventos/32 ações core, até 8 world actions/evento, fuel 16.384, AST até 4096 nós/profundidade 32 |

Erros de overflow, orçamento, alvo/capacidade ou tentativa de reciclar `spent` rejeitam a execução. Não há shell, URL livre, carregamento de Java ou comando de bloco no script.

## 5. IA local opcional — desabilitada por padrão

**Nenhuma IA paga ou LLM real foi chamado nesta revisão.** Não é necessário configurar IA para usar itens/escudo/scripts. Para confirmar bloqueio sem rede, com configuração padrão:

```text
/nanites ai proponha um escudo
```

Deve explicar que o operador precisa configurar `ai.model`. Não invente o nome do modelo.

Arquivo: `config/nanites/server.properties` da instância/servidor. Não compartilhe tokens. Exemplo **sem segredo**, não é leitura de valores pessoais:

```properties
ai.model=REQUIRED
ai.address=https://127.0.0.1:8081/v1
ai.token=
max.players=128
materials.insulating=minecraft:white_wool
```

Para habilitar manualmente:

1. Disponibilize um modelo local com protocolo compatível com chat completions/ferramentas; o projeto não instala modelos/servidor de IA.
2. O serviço deve escutar no endpoint **exato** `https://127.0.0.1:8081/v1` na máquina do servidor Minecraft; em multiplayer não é o loopback do jogador. A rota chamada é `/chat/completions`.
3. Configure o nome **exato** servido em `ai.model` e token somente se seu operador local exigir; não procure/importa automaticamente credenciais.
4. TLS/hostname são verificados pela JVM, sem redirects/proxy ou fallback HTTP. Certificado precisa de SAN para IP 127.0.0.1 e cadeia confiável. Truststore JVM, se necessário, é configuração manual do operador; não desabilite TLS/hostname.
5. Reinicie normalmente o servidor. Não altere `ai.address`: endereço alternativo é recusado na inicialização.
6. Solicite, aguarde e revise:

```text
/nanites mods
/nanites ai Proponha Nanite Script v1 preservando state hits, group owner, escudo de queda e anel
/nanites proposal 0
/nanites apply
```

Proposta não executa automaticamente. `proposal 0` prepara e mostra fonte/revisão base; `apply` é aprovação do próprio dono e revalida o estado atual. Dano, depósito, troca de código ou tick que modifica estado podem invalidar resultado/proposta; peça outra. Logout cancela trabalho e invalida a geração antiga. Fique parado com o padrão durante a solicitação.

Worker fora da thread de tick, até 2 jogadores com job de IA; até 6 turns/16 chamadas, reserva de 30.000 tokens, saída/histórico limitados, timeout de chamada 45 s, resposta até 128 KiB e `max_tokens=1024` por chamada. São quotas técnicas, não garantia de qualidade do modelo/TPS.

Nove ferramentas: `read`, `search`, `list_mods`, `list_sources`, `catalog`, `state`, `read_script`, `validate_script`, `propose_script`. Só dados/fontes previamente autorizadas e snapshot destacado. Não decompilam por iniciativa da IA, certificam materiais, instalam adapters nem executam proposta/Java. Testes de transporte são **fake**, não validação de TLS/modelo real.

## 6. Decompile seletivo e cache — opt-in do operador

Desabilitado por padrão. Exige jogador dono autenticado **e OP nível 2**, modId na allowlist e JAR real exposto pelo loader. OP sozinho não aprova qualquer mod. Não decompila automaticamente todo o modpack; mods nested/dev sem JAR regular exposto não são extraídos.

Instale manualmente uma distribuição confiável do Vineflower. O projeto não baixa/fornece a ferramenta. Configure caminhos absolutos canônicos, com `/` no Windows, e versão real do JAR:

```properties
# Exemplo; trocar pelo arquivo/versão reais do operador:
decompiler.jar=D:/ferramentas/vineflower.jar
decompiler.version=versao-real-do-jar
decompiler.allow-mods=nanites
```

Não coloque espaços em torno dos modIds separados por vírgula: a allowlist é comparada literalmente. Reinicie o servidor; sem IA, execute manualmente:

```text
/nanites decompile nanites dev/nanites/game/GameService.class
/nanites source list nanites
/nanites source read nanites dev/nanites/game/GameService.java 0
```

Use o caminho efetivamente retornado por `source list`; próxima fatia usa `next` mostrado na leitura. Leitura pelo comando: até 4096 caracteres/fatia. Uma classe é selecionada por job, worker com timeout 45 s. O processo usa Java da instância e somente ferramenta configurada; não executa/carrega classes do mod.

Resultado só é aprovado se dono ainda online, OP, geração/revisão atuais. Cache sob `config/nanites/source-cache`, índices por dono em `source-index`; chave inclui SHA256 completo do JAR, loader, Minecraft, versão loader, versão/hash do decompiler e schema. Limites incluem cache 64 MiB, fonte 256 KiB, até 128 entradas/namespace, 64 aliases/dono. Não há promessa de limpeza/LRU automática nem comando geral de limpar cache; esta revisão não apaga caches.

Fontes são **dados não confiáveis**, não prova de compatibilidade, capacidade VERIFIED ou autorização de integração energética. Execução real do Vineflower/TLS/modelo e fluxo em Minecraft continuam sem teste aqui.

## 7. Persistência, backup e falhas

- Estado: `<mundo>/nanites/<UUID>.snapshot.json`.
- Material: `<mundo>/nanites/materials/<UUID>.json`.
- Código editável: `config/nanites/scripts/<UUID>/<nome>.nano`.
- Fontes/index: `config/nanites/source-cache` e `source-index`.
- `/nanites save` **agenda** snapshot; não confirma durabilidade imediata.

Cópia isolada é enfileirada a cada 100 ticks (~5 s), depósito, save, hot-swap e logout. Worker único coalesce até 128 donos; stop normal aguarda flush até 10 s e reporta erro se não salvar. Login carrega material/snapshot uma vez; relog imediato usa cópia pendente atual. Material é escrito no comando de absorção.

**Não há transação durável conjunta** entre inventário/playerdata/crafting, snapshot Nanites, material e efeitos. Crash, disco cheio ou restaurar só uma parte do backup pode perder/desalinhar dados. Não force crash em mundo valioso. Faça backup de mundo **e config/nanites juntos** depois de desligar normalmente. Não há journal/outbox universal, retirada/refund ou reset seguro de recursos por comandos.

## 8. Build e checks no Windows

Scripts lidos antes de execução: `tools/audit.ps1` **antes de** `build.ps1`, também `test.ps1`, `tools/review-build.ps1` e `gradlew.bat`. Build scripts/configs dos módulos confirmados. Não execute `tools/review-build.ps1` indiscriminadamente: ele copia todo o cache Gradle local, pode ser lento e consumir muito espaço.

PowerShell:

```powershell
Set-Location 'D:\Downloads\nanobots mod\nanites'
# Offline: dependência Gson 2.10.1 já local, nenhum download/LLM:
powershell -NoProfile -ExecutionPolicy Bypass -File test.ps1 -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'
powershell -NoProfile -ExecutionPolicy Bypass -File test.ps1 -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
# Builds reais, Forge seguido de Fabric e auditoria/publicação:
powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1
```

`-GsonJar 'caminho/gson-2.10.1.jar'` muda a dependência local do teste; se faltar, o teste falha sem baixar. `test.ps1`: javac `--release 17 -encoding UTF-8 -Xlint:all -Werror`, CoreTests/ExpansionTests, Demo/ProgrammingDemo e JAR core em `build/run-<GUID>/nanites-core.jar`. Ele **não testa o loader nem shared-game** e não produz mod instalável. Demos são intents/contabilidade, não construção/energia no mundo.

`build.ps1` usa JDK17/21 padrão acima, configuráveis com `-Java17`/`-Java21`. Em outra máquina ajuste também `org.gradle.java.installations.paths` de `nanites/gradle.properties`. Gradle wrapper 8.8, ForgeGradle 6.0.54 e Loom 1.7.4 já fixados; não é preciso copiar nada do jevmod. Gradle pode baixar dependências faltantes; o build normal **não é necessariamente offline**.

Comandos internos exatos, sequenciais porque compartilham outputs core/shared:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'
.\gradlew.bat -Ploader=forge :core:check :shared-game:check :forge:build --console=plain
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
.\gradlew.bat -Ploader=fabric :core:check :shared-game:check :fabric:build --console=plain
```

`check` inclui tarefas JavaExec `coreTests`, `expansionTests` e `gameTests`; **não confunda `test NO-SOURCE` com ausência desses testes**. `gameTests` é harness de serviço, não Forge GameTest/servidor Minecraft. Forge precisa de `reobfJar`; Fabric, `remapJar`. `dist` só é publicado depois de ambos os builds e auditoria passarem. Logs: `validation-logs/<timestamp>-forge.log`, equivalente Fabric, `dist/VALIDATION.txt`.

Auditoria confere zip/duplicatas, classes core/game, descriptors, recursos/receita, ausência de Gson embutido, major 61 no core/Forge e 65 no Fabric, nomes SRG/intermediary e refmap do mixin Player. Isso não prova crafting/rendering/injeção em runtime. Para audit separado, passe **os logs reais de ambos os builds**:

```powershell
# Substituir pelos nomes da sua rodada; audit também copia os JARs para dist:
powershell -NoProfile -ExecutionPolicy Bypass -File tools\audit.ps1 -ForgeLog 'validation-logs/SEU_TIMESTAMP-forge.log' -FabricLog 'validation-logs/SEU_TIMESTAMP-fabric.log'
```

### Pouco espaço em C / redirecionamento PowerShell

Nesta máquina C tinha ~0,8 GiB livres, D ~21 GiB. Temporários foram direcionados somente neste processo para `nanites/build/review-temp`; caches/mundos não foram apagados. Não foi usada a cópia parcial como cache de build.

Ao redirecionar um script inteiro com `*>` em Windows PowerShell 5 e `$ErrorActionPreference=Stop`, stderr **normal** de `java -version`/`JAVA_TOOL_OPTIONS` pode virar `NativeCommandError`. Isso aconteceu antes de compilar; foi contornado sem alterar o script, redirecionando no **cmd.exe**, não afrouxando `-Werror`.

Em **cmd.exe**, após `cd /d "D:\Downloads\nanobots mod\nanites"`:

```bat
set "TEMP=D:\Downloads\nanobots mod\nanites\build\review-temp"
set "TMP=D:\Downloads\nanobots mod\nanites\build\review-temp"
set JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=D:/Downloads/nanobots mod/nanites/build/review-temp"
powershell -NoProfile -ExecutionPolicy Bypass -File test.ps1 -JavaHome "C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot" > validation-logs\offline-jdk17.log 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1 > validation-logs\build-console.log 2>&1
```

Crie as pastas de log/temp se ausentes. As variáveis valem para esse terminal/processo; não mude configurações globais. Não use `clean`, apague cache ou altere mundos como primeira tentativa de corrigir erro. Monitore o log até `BUILD SUCCESSFUL`/falha e confira o exit code, não só existência de JAR. Para Gradle realmente offline com cache completo, acrescente `--offline` aos comandos manuais; dependência ausente deve ser registrada como bloqueio.

## 9. Resultado desta continuação e artefatos

**Rodada encerrada: ambos os builds e a auditoria passaram.** Completude funcional foi conferida por leitura de código/recursos e pelos testes abaixo, não inferida apenas da compilação.

| Execução | Resultado atual | Tempo/tarefas |
|---|---|---|
| `test.ps1`, JDK17 | CoreTests 33 + ExpansionTests 57 = **90**, duas demos, compilação estrita/JAR core PASS | offline, Gson local |
| `test.ps1`, JDK21, release17 | **90**, duas demos, compilação estrita/JAR core PASS | offline, Gson local |
| Forge: core/shared check + build | **33 + 57 + 50 = 140 testes/checks**, build/reobf PASS | 1m39s; 18 tarefas acionáveis: 15 executadas, 3 up-to-date |
| Fabric: core/shared check + build | **33 + 57 + 50 = 140 testes/checks**, build/remap PASS | 1m59s; 16 tarefas acionáveis: 14 executadas, 2 up-to-date |
| Auditoria de ambos os JARs | descriptors, recursos, Java17/21, SRG/intermediary, mixin/refmap PASS | publicou `dist` |
| Inspeção adicional dos JARs publicados | **28 checks PASS**: duas receitas/ingredientes/resultados/modelos/texturas/idiomas, ausência de testes e AsyncSnapshots Java17 | estática e offline |

Contagens antigas de **87/54/22** no README/docs são históricas: código atual executou **90 core/50 game** por loader. `GameTests` usa Host de teste, não runtime Minecraft; `test NO-SOURCE` nos loaders não esconde esses harnesses JavaExec. Não apareceu `SKIP symlink creation` nas quatro rodadas de core. Testes de IA usam transporte fake, sem conexão LLM. A mensagem `Nanites VFX failed ... VFX failure` foi **falha deliberadamente injetada pelo harness**, seguida de PASS: confirma que falha visual não remove a proteção contábil já confirmada.

Avisos não bloqueantes: Forge usa `FMLJavaModLoadingContext.get()` marcado deprecated/para remoção; ambos avisam de features Gradle incompatíveis com Gradle 9. Não foram ocultados nem alterados; permaneceu Gradle 8.8. Nenhum bloqueio final de build.

Logs atuais, dentro de `nanites/`:

- `validation-logs/continuacao-offline-jdk17-cmd.log` e `continuacao-offline-jdk21-cmd.log`.
- `validation-logs/20261106-180720-forge.log` e `20261106-180720-fabric.log` (timestamp local UTC-3 no nome).
- `validation-logs/continuacao-build-console.log`, `dist/VALIDATION.txt` e `validation-logs/continuacao-recursos-artefatos.log`.

Core de teste, **não instalar no Minecraft**: `build/run-61e1df3f2c95476b91a2027cf715f8dd/nanites-core.jar`, 142278 bytes, 21:07:01.5662691Z; JDK21: `build/run-302d70f312774c25ae0df7043e8a5084/nanites-core.jar`, 142482 bytes, 21:07:19.4708475Z. Datas UTC de 06/11/2026.

### JARs atuais publicados, não os históricos

| Arquivo para instalar | Bytes / entradas | Última escrita UTC (06/11/2026) |
|---|---|---|
| `nanites/dist/nanites-forge-1.20.1-0.2.0.jar` | **195121 / 124** | **21:08:59.7102270Z** |
| `nanites/dist/nanites-fabric-1.21.1-0.2.0.jar` | **198748 / 127** | **21:11:00.6885272Z** |

SHA256 atual:

```text
Forge:  E86FE74A2BACEEE435EB7915DC79BDE24A0C82A99492CAA5578D533B8001A1FD
Fabric: 6A3A34CEF07CF67D082BD6BACAD0FAA82DAD10CDE2B2D0866B1BD47B52381731
```

Hashes/tamanhos/timestamps de `dist` coincidem com os respectivos `forge/build/libs` e `fabric/build/libs`. `dist/VALIDATION.txt` foi atualizado pela auditoria às 21:11:04.5715927Z. Mesmos nomes/versão 0.2.0, conteúdo novo: substitua o JAR antigo da instância, não mantenha duplicatas. `fabric/build/libs/fabric-0.2.0.jar` permaneceu histórico, 190760 bytes/20:27:05Z; **não foi o JAR validado/publicado**.

Artefatos **anteriores à continuação**, timestamps UTC de 06/11/2026:

| Arquivo | Bytes | Última escrita UTC antes do build |
|---|---:|---|
| `nanites/dist/nanites-forge-1.20.1-0.2.0.jar` | 187624 | 20:29:40.2547423Z |
| `nanites/dist/nanites-fabric-1.21.1-0.2.0.jar` | 190846 | 20:30:15.8535066Z |
| `nanites/fabric/build/libs/fabric-0.2.0.jar` (histórico, não recomendado) | 190760 | 20:27:05.1933571Z |

A cópia de cache deixada pela revisão interrompida ainda estava executando. Foram identificados e encerrados **somente** seu processo `review-build.ps1` e filho robocopy antes de retomar, evitando que disparasse outra sequência de builds. Arquivos parciais foram preservados. Não foram executados bootstrap, probes de rede, Minecraft ou IA.

## 10. Completude e próximos testes reais

**Confirmado por leitura de implementação/recursos:** dois itens e receitas; intake de caixa; saldo por dono/mundo e gasto irreversível; handlers fall/impact/tick; redução/partículas orçadas; edição/import/export/preview/rollback de código; material mapeado/fogo; snapshot assíncrono; comandos IA com proposta/revisão; decompiler seletivo e cache opt-in. Testes automatizados não substituem todos esses testes no loader.

**Não implementado:** blocos sólidos nanite, construção arbitrária/ilimitada, desmontagem/retirada, conteúdo portátil em NBT, armadura/OBJ custom, editor gráfico, energia/reator externo, carregamento/hot-reload de adapters Java, transação cross-Minecraft universal. SPI/interfaces e demos históricas não entregam essas funcionalidades.

**Ainda não validado in-game:** boot de cada loader, registros/crafting/usos reais/cooldown, mixin Fabric e sua ordem com outros mods, ponto de aplicação do escudo versus armadura em Forge/Fabric, rendering/partículas, morte/relogin/dimensões, servidor dedicado/multiplayer, claims/modpacks, TPS, crash/recovery. A automação não abriu o jogo nem aceitou EULA/criou mundo real.

Roteiro prático (cada loader, mundo novo):

1. Boot e `/nanites status`; craft ambos os itens e compare JSONs/receitas acima.
2. Sem reservatório: caixa não consumida; com reservatório/mão principal: +500 bilhões por uso, uma caixa removida; testar mão secundária, cooldown e Creative.
3. Queda moderada (6–8 blocos) e ataque não letal antes/depois de abastecer; registrar HP, `storage`, `spent`, `hits` e VFX. Cuidado: 1 coração = 2 HP.
4. Carregar inert/rollback; preview não gasta, rollback não reembolsa. Importar os três exemplos Minecraft; confirmar coluna/fogo com lã branca e recusa de pedra.
5. Save, sair normalmente/reabrir; conferir saldo/script/material. Dois jogadores devem ter saldos/arquivos independentes; relog descarta proposta/histórico antigo.
6. Confirmar IA REQUIRED/decompile negado sem configuração, **sem habilitar IA paga**. Teste real opcional de IA/TLS/Vineflower precisa de setup manual e aprovação separada.
7. Só depois testar o modpack, registrando divergências e ordem/cancelamento de dano. Consulte também [INSTALL](nanites/INSTALL.md) e [roteiro detalhado](nanites/TESTE_NO_JOGO.md).

Ao reportar falha: loader/MC/Java, caminho/hash do JAR, mods instalados, comando, passos, HP/saldo antes/depois e trecho de `logs/latest.log`/crash-report. **Remova tokens e dados pessoais antes de compartilhar; não envie server.properties completo**.
