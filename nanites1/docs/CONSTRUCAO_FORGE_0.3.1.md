# Construção real e IA — Nanites 0.3.1, somente Forge Minecraft 1.20.1

Este código não integra nem modifica `jevmod`. A construção agora usa **blocos reais**, não `shape.ring/shape.column` (esses efeitos históricos continuam sendo partículas). Não houve execução de Minecraft ou validação de um modelo real nesta implementação. O resultado visual e a compatibilidade com seu modpack precisam do seu teste.

## 1. Compilar sem abrir o jogo

No **CMD**, com Java 17 já instalado, execute:

```bat
cd /d D:\Downloads\nanobots\nanites
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
gradlew.bat -Ploader=forge :core:check :shared-game:check :forge:build --offline --console=plain
```

Ajuste somente o caminho do Java se diferente. `--offline` usa o cache existente; se faltar dependência, o build falha sem publicá-la. Não rode os scripts dual/históricos para esta entrega. As tarefas `coreTests`, `expansionTests`, `gameTests`, `runtimeTests`, `constructionTests` e `forgeAttributeTests` devem terminar com sucesso, além de `reobfJar`. O JAR da versão é `forge/build/libs/nanites-forge-1.20.1-0.3.1.jar`. Um arquivo antigo em `dist` não prova que seu novo build passou. Consulte o relatório de verificação desta revisão.

Instale somente o JAR Forge desta revisão na pasta `mods` da instalação **Forge 1.20.1** (Forge 47.4.28 ou compatível), substituindo o Nanites anterior com o jogo/servidor fechado. Não instale duas versões de Nanites simultaneamente. Mods usados nos blueprints precisam estar previamente instalados no cliente/servidor conforme as exigências de cada mod. **Não há hot-install de JARs**.

## 2. Teste mínimo manual (sem IA)

Use um mundo de teste descartável/criativo: os comandos sobrescrevem blocos e não fazem backup/rollback. Você mesmo abrirá o jogo; nenhum jogo foi iniciado pelos testes automatizados.

1. Execute estes comandos no jogo, indo para uma região que você possa sobrescrever:

```text
/gamemode creative
/tp @s 0 82 0
/nanites build limits
/nanites status
/nanites blocks minecraft:stone 0 4
/nanites observe {"dimension":"minecraft:overworld","x":0,"y":80,"z":0,"radius":0}
/nanites build submit {"origin":{"dimension":"minecraft:overworld","x":0,"y":80,"z":0},"voxels":[{"x":0,"y":0,"z":0,"block":"minecraft:stone"}]}
/nanites build status
```

2. Espere alguns ticks e consulte `/nanites build status` novamente. Para esse plano de um bloco, o resultado esperado é `COMPLETED`, `processed=1`, `total=1`, `placed=1` **ou** `already_matched=1` se já era pedra. `observed` deve mostrar o estado atual. Confira visualmente e com `/nanites observe` que existe pedra em **0,80,0**. `QUEUED` não significa pronto.
3. Digite um bloco inexistente no mesmo JSON, por exemplo `inexistente:bloco`: deve aparecer erro e o último plano válido não deve ser substituído. Para blockstate inválido use `minecraft:oak_log[axis=q]`; para válido `minecraft:oak_log[axis=x]`.

Construção nesta fatia é edição direta do mundo: **não exige caixa, saldo, grant ou aprovação por bloco, nem consome inventário**. Escudo, armadura e intervenções antigas mantêm seus próprios custos/regras. Não há economia de materiais de construção implementada.

## 3. Blueprint manual misto/arbitrário por arquivo

O chat pode limitar o comprimento de comandos; use arquivo para planos maiores.

1. `/nanites example sessao` cria um script exemplo e a pasta `config/nanites/scripts/SEU_UUID/`. Seu UUID aparece em `/nanites status`. Se o exemplo já existir, não precisa repetir. A pasta `config` é a **da instalação do jogo/servidor**, não a do repositório.
2. Crie manualmente o arquivo `config/nanites/scripts/SEU_UUID/manual.blueprint.json` com este conteúdo UTF-8 (sem comentários). Só o nome simples `manual` será usado no comando:

```json
{
  "origin": {"dimension": "minecraft:overworld", "x": 0, "y": 80, "z": 0},
  "voxels": [
    {"x": 0, "y": 1, "z": 0, "block": "minecraft:oak_log[axis=x]"},
    {"x": 2, "y": 3, "z": -1, "block": "minecraft:glass"}
  ],
  "cuboids": [
    {"from": {"x": 0, "y": 0, "z": 0}, "to": {"x": 6, "y": 0, "z": 6}, "block": "minecraft:stone_bricks"},
    {"from": {"x": 0, "y": 1, "z": 6}, "to": {"x": 6, "y": 4, "z": 6}, "block": "minecraft:oak_planks"}
  ],
  "spheres": [
    {"center": {"x": 3, "y": 6, "z": 3}, "radius": 2, "block": "minecraft:glass"}
  ],
  "lines": [
    {"from": {"x": 0, "y": 1, "z": 0}, "to": {"x": 6, "y": 4, "z": 0}, "block": "minecraft:iron_block"}
  ]
}
```

3. Execute:

```text
/nanites build load manual
/nanites build status
/nanites observe {"dimension":"minecraft:overworld","x":2,"y":83,"z":-1,"radius":0}
```

Todos os voxels e pontos dos geradores são **relativos à origem**. Portanto o voxel `(2,3,-1)` vai para `(2,83,-1)`. Cuboids são sólidos com extremos inclusivos, aceitam extremos invertidos; spheres são sólidas em grade inteira; lines usam DDA arredondada, incluindo extremos. A lista livre de voxels permite qualquer geometria dentro dos limites técnicos, não apenas esses geradores.

Ordem fixa de expansão: `voxels`, `cuboids`, `spheres`, `lines`. Em sobreposição prevalece o último material, mas a posição mantém a ordem da primeira inclusão. Sobreposições contam no limite de expansão. Se precisar de ordem de colocação física específica (suportes antes de peças), use somente a lista `voxels` na ordem desejada.

**Editar durante a sessão:** altere o arquivo, execute `/nanites build load manual` novamente. O plano antigo só é substituído depois da validação completa do novo. Não há watcher automático; o comando recarrega do disco sem reiniciar. Um plano novo não remove automaticamente os blocos colocados pelo antigo. Para removê-los, inclua explicitamente `minecraft:air` no novo plano.

**Cancelar imediatamente o restante:** `/nanites build cancel`. Blocos já colocados ficam. Morte, troca de dimensão, logout e encerramento também cancelam trabalho pendente; a fila não é persistida para retomada após reinício.

Para testar progresso visível, use um cuboid de `from=(0,0,0)` a `to=(99,0,99)` (10.000 voxels), execute `build load`, confira o status e cancele. `placed` não deve continuar aumentando depois do cancelamento. Não use planos enormes para o primeiro teste de seu modpack.

## 4. Blocos de mods, pesquisa e observação

```text
/nanites blocks * 0 8
/nanites blocks stairs 0 8
/nanites blocks stairs 8 8
```

A busca é substring do ID de registry, sem lista fixa de materiais. Use `next_offset` retornado para a próxima página: uma página pode ser menor por tamanho do resultado. Retorna IDs, blockstate padrão e propriedades/valores possíveis. Substitua materiais no blueprint por IDs reais de mods instalados, preservando as propriedades reais listadas. Um nome desconhecido ou propriedade inexistente/inválida falha **antes** de substituir o plano. Se aparecer `properties_truncated`, peça página menor/consulte a documentação do bloco; o resultado não inventa propriedades.

`observe` exige exatamente `dimension,x,y,z,radius`, coordenadas absolutas inteiras; `radius` vai de 0 a 2. `dimension:""` significa a dimensão atual do jogador. O resultado inclui posição atual do jogador, tick e blocos carregados, ou `CHUNK_UNLOADED`/`OUT_OF_WORLD`. A consulta não gera/carrega chunks.

A execução espera em `WAITING_CHUNK` se o próximo chunk não está carregado. Vá normalmente para a região e consulte novamente. Não há tickets nem carregamento forçado na fila. Ela não pula o voxel aguardando, para preservar a ordem de suportes/colocação.

## 5. Pedir construção real à IA

A configuração existente usa **HTTPS local exato** `https://127.0.0.1:8081/v1/chat/completions`, com verificação TLS/hostname preservada, e um modelo compatível com `tools`/`tool_calls` OpenAI. Configure **você mesmo** `ai.model` em `config/nanites/server.properties` e o serviço local/TLS; não exponha seu token. A configuração inicial de conexão/modelo é lida ao iniciar o servidor; alterá-la exige reinício nesta fatia. Scripts/blueprints e novas perguntas não exigem reinício. Não há fallback que desabilite TLS, nem foi feita chamada a um LLM real na validação automatizada.

Com IA configurada, na mesma região de teste:

```text
/nanites ai Construa de verdade um arco assimétrico de pedra e vidro com vão livre, origem 20 80 0 no overworld. Consulte blocos/mundo e execute submit_blueprint, não apenas descreva.
/nanites build status
```

O texto do modelo sozinho não executa nada: precisa retornar chamadas de ferramentas válidas. A IA recebe descrições de schema e pode escolher blocos instalados, expandir geometria, submeter, observar e replanejar. Uma confirmação `IA submit_blueprint: ... QUEUED` é recibo real de fila; só status/observação confirma progresso/efeitos. Falha do modelo, TLS ou limite da conversa pode deixar um plano já submetido em execução; use `build cancel` se quiser pará-lo.

Replanejamento no mesmo servidor:

```text
/nanites ai Cancele o restante da construção anterior e substitua por uma ponte curva assimétrica de madeira e pedra, origem 20 80 0 no overworld. Use ferramentas e consulte o resultado.
/nanites build status
/nanites ai Consulte construction_status e observe os blocos já colocados. Relate apenas o que as ferramentas confirmam.
```

Nova `/nanites ai ...` cancela a rodada anterior e invalida respostas/callbacks antigos por geração. Não cancela automaticamente construção que já entrou na fila: o novo pedido deve chamar `cancel_construction` ou `submit_blueprint` para substituir. `/nanites ai_cancel` cancela só a conversa. `/nanites build cancel` cancela conversa **e** trabalho pendente. Submissão manual válida também invalida a rodada antiga. O gateway verifica geração e revisão de construção na thread servidor; respostas antigas não sobrescrevem novos planos. `state`/`read_script` usam um provider de **estado atual**, não o snapshot inicial da conversa.

### Protocolo efetivamente exposto

Todas as chamadas seguem o protocolo de ferramentas já existente: função com argumentos JSON exatos; resposta JSON `{trust,data}` ou `{trust,error}` inserida na próxima rodada do modelo. Cada nome abaixo é uma ferramenta, não um comando para colar no chat:

| Ferramenta | Argumentos exatos | Efeito |
|---|---|---|
| `world_observe` | `dimension,x,y,z,radius` | Consulta live e posição do jogador na thread servidor |
| `block_catalog` | `query,offset,limit` | Registry instalado paginado, limite 1..32 |
| `submit_blueprint` | `source` (string JSON do blueprint) | Valida e agenda/substitui fila real |
| `construction_status` | `{}` | Estado live, progresso, último bloco observado |
| `cancel_construction` | `{}` | Para restante, não desfaz blocos |
| `state` | `{}` | Snapshot atual de script/estado/saldo |
| `read_script` | `offset,limit` | Texto ativo atual, até 8192 caracteres/página |

Os workers nunca leem `ServerLevel`; consultas e mutações são marshalled por future para a thread servidor. A espera da ferramenta ocorre no worker, com timeout de 10 segundos; callbacks já cancelados não executam no servidor. Modelos/conversas têm limites reais: até 8 rodadas, 24 chamadas, 30.000 tokens reservados e 65.536 caracteres de histórico; saída por completion 1024 tokens. Use geradores compactos e pedidos menores. Não há agente infinito/autônomo de manutenção: para revisar após a conversa terminar, faça uma nova pergunta.

## 6. Hot reload dos scripts existentes

Blueprint não é DSL `.nano`; são caminhos de execução distintos. O hot-swap existente de scripts foi preservado, com rollback de código last-known-good:

```text
/nanites edit program vivo version v1; state hits = 0; on tick group owner { add hits = 1; }
/nanites apply
/nanites status
/nanites export vivo
```

Edite `config/nanites/scripts/SEU_UUID/vivo.nano` com o servidor aberto e execute:

```text
/nanites preview vivo
/nanites load vivo
/nanites status
/nanites rollback
```

`load` ativa sem reinício; texto inválido não troca o código atual. Estado compatível é preservado (mudar o valor default de `hits` não zera o contador já existente). `rollback` reativa código anterior, **não reembolsa saldo, não desfaz HP nem blocos**. Alterações manuais invalidam a rodada IA antiga. Propostas `.nano` da IA continuam por `proposal índice` e `apply`; construção se executa pelas ferramentas, não por DSL ignorada.

## 7. Limites técnicos e lacunas reais

Em `config/nanites/server.properties`, estes parâmetros opcionais são lidos ao iniciar. Não existe aprovação por bloco:

```properties
build.blocks.per.tick=64
build.max.voxels=32768
build.tick.micros=2000
```

Orçamento **global**, round-robin entre donos, com até 64 passos/tick e até ~2 ms entre passos. Um único `setBlock`/hook de mod não é interrompível: pode ultrapassar o orçamento. Faixas de configuração: blocos/tick 1..65536, expansão 1..1000000, micros 1..50000. Parsing/validação inicial ainda são síncronos no callback servidor e podem custar mais em planos grandes; limite seu tamanho. JSON de arquivo até 4 MB; `source` IA até 65.536 caracteres; strings de bloco até 512 caracteres, dimensão até 240, JSON com profundidade máxima 32. Esferas têm raio máximo 128 e orçamento de varredura, mas a lista de voxels não fica limitada a essas formas.

Validação de altura, borda do mundo, overflow de coordenadas, dimensão existente e registry precede colocação. Estado canônico é observado após `setBlock`; rejeição/mismatch para em `FAILED` com erro e progresso já realizado. `COMPLETED` significa que os passos foram observados como desejado **no momento da colocação**, não garantia de que ninguém/mod/física altere o mundo depois. Status relê o último bloco; `observe` consulta outras posições.

Flags de colocação são `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE` (18): atualiza cliente, evita propagação automática de formas/notificações de vizinhos na própria fila. Isso reduz cascatas/chunks incidentais, mas não é reprodução da colocação por jogador: portas/metades, redstone, conexões de cercas/escadas etc. podem exigir estados/voxels complementares explícitos. Hooks `onPlace` e ticks de mods ainda podem produzir efeitos próprios (inclusive caros ou carregar chunks por conta do mod); o Nanites não controla código externo. Não há NBT de block entities, importação de schematics, consumo/crafting de materiais, rotação automática, rollback do mundo, integração de energia universal ou retomada persistida da fila. Não estamos declarando toda auditoria corrigida nem compatibilidade universal de mods.

`constructionTests` testa parsing/geometria, material misto, tamanho/overflow, execução incremental, cancel/substituição, validação antes de substituir, falha observada, chunks ausentes, justiça global, thread worker negada, provider live, geração obsoleta, futures cancelados e chamada IA fake passando pelo protocolo real até efeitos no fake host. Isso não substitui seu teste visual/runtime Forge.
