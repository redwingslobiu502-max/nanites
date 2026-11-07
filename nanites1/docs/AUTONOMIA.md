# Etapa 3 — autonomia persistente e fábricas (Forge 1.20.1)

Base 0.3.1 preservada. Sem publicar dist, iniciar jogo, acessar rede, alterar armadura, Fabric ou jevmod.

## Execução efetivamente conectada

`NanitesForge.serverTick(END)` já chama `GameService.serverTick`: agora executa construção, objetivos e `MachineAdapters.tick`. Objetivos usam o mesmo gateway real de máquinas/assimilação. Cada transferência chega às capabilities Forge existentes; a máquina continua processando suas **próprias receitas**, combustível, FE e fluidos no tick normal do mod. Não existe receita industrial universal, processo privado iniciado por reflection, recurso virtual, force-load ou API inventada.

- `mode: factory`: daemon declarativo, **independente de modelo/API**, repete uma receita interna finita a cada intervalo.
- `mode: language`, referência de receita vazia: rodadas periódicas do `MultiTurnAgent` assíncrono, protocolo real de function tools, observação e replanejamento.
- `mode: language`, referência preenchida: daemon sem modelo a cada transferência; feedback de stall/recibos parciais/predicado não atendido permite replanejar com modelo na próxima rodada. A IA pode `adapter_create` e `objective_update` para anexar/trocar a receita durante execução.
- Configuração de modelo existente permanece compatível. `ai.model=REQUIRED` produz `waiting_model` somente para objetivos sem daemon. Todos os comandos de controle continuam funcionando.

## Comandos e tools

```text
/nanites objective list
/nanites objective status fundicao
/nanites objective pause fundicao 1
/nanites objective resume fundicao 2
/nanites objective stop fundicao 3
/nanites factory list
/nanites machine_schema objective_create
/nanites machine_schema factory_update
```

`factory` e `objective` são aliases do **mesmo registro durável**; não criam registros duplicados. Controle incrementa `version`. Use sempre a versão atual retornada por status; não reutilize números antigos. Nomes simples, 1..48 caracteres. `create nome JSON` e `update nome versão JSON` aceitam configuração bruta, sem duplicar escapes. `adapter define projeto nome JSON` equivale ao `adapter_create` existente.

As 14 function tools são `objective_` e `factory_` com sufixos `create`, `list`, `status`, `update`, `pause`, `resume`, `stop`. Schemas fechados:

- create: `{"name":"fundicao","source":"JSON da configuração serializado como string"}`;
- update: mesmos campos mais `"version":1`;
- status: `{"name":"fundicao"}`; list: `{}`;
- pause/resume/stop: `{"name":"fundicao","version":1}`.

Autenticação de dono é do Host, nunca de UUID informado pelo modelo. Versão protege controles; geração interna protege callbacks. Não exige aprovação/grant para fábricas. Ferramentas de armadura/intervenção não fazem parte do agente autônomo.

## Exemplo literal sem modelo: forno vanilla real

Prepare **blocos únicos** carregados em `minecraft:overworld`, y=64,z=0:

| x | bloco | conteúdo |
|---|---|---|
| 0 | baú de entrada | slot 0: minério bruto compatível com forno |
| 1 | forno normal | slots inicialmente vazios |
| 2 | baú de combustível | slot 0: carvão |
| 3 | baú de saída | slot 0 vazio |

O forno vanilla consome minério e carvão e produz lingotes sozinho. O adapter somente abastece e recolhe por capabilities sided: cima=input, oeste=combustível, baixo=saída. Slots são **índices da capability da face**, não necessariamente os índices da GUI. Não copie esses índices para máquinas de mods sem `machine_inspect`.

Comandos longos excedem o limite do chat vanilla: use o editor de command block ou console com `execute as @p run nanites ...`, identificando o jogador correto. Não foi executado nenhum destes comandos nesta implementação.

Primeiro, registre o loop interno (comando completo, uma linha):

```text
execute as @p run nanites adapter define default smelt {"version":1,"steps":[{"tool":"transfer_items","args":{"from":{"type":"block","dimension":"minecraft:overworld","x":0,"y":64,"z":0,"side":"none"},"to":{"type":"block","dimension":"minecraft:overworld","x":1,"y":64,"z":0,"side":"up"},"amount":1,"preview":false,"from_slot":0,"to_slot":0},"repeat":1,"when":{"tool":"machine_inspect","args":{"target":{"type":"block","dimension":"minecraft:overworld","x":1,"y":64,"z":0,"side":"up"},"offset":0,"limit":1},"path":"sides.0.inventory.0.amount","op":"lt","value":1}},{"tool":"transfer_items","args":{"from":{"type":"block","dimension":"minecraft:overworld","x":2,"y":64,"z":0,"side":"none"},"to":{"type":"block","dimension":"minecraft:overworld","x":1,"y":64,"z":0,"side":"west"},"amount":1,"preview":false,"from_slot":0,"to_slot":0},"repeat":1,"when":{"tool":"machine_inspect","args":{"target":{"type":"block","dimension":"minecraft:overworld","x":1,"y":64,"z":0,"side":"west"},"offset":0,"limit":1},"path":"sides.0.inventory.0.amount","op":"lt","value":1}},{"tool":"transfer_items","args":{"from":{"type":"block","dimension":"minecraft:overworld","x":1,"y":64,"z":0,"side":"down"},"to":{"type":"block","dimension":"minecraft:overworld","x":3,"y":64,"z":0,"side":"none"},"amount":1,"preview":false,"from_slot":0,"to_slot":0},"repeat":1,"when":{"tool":"machine_inspect","args":{"target":{"type":"block","dimension":"minecraft:overworld","x":1,"y":64,"z":0,"side":"down"},"offset":0,"limit":1},"path":"sides.0.inventory.0.amount","op":"gt","value":0}}]}
```

Depois, crie a fábrica, sem API de IA:

```text
execute as @p run nanites factory create fundicao {"mode":"factory","text":"Abastecer forno real e coletar lingotes continuamente; manter saída observada.","scope":{"dimension":"minecraft:overworld","min":{"x":0,"y":64,"z":0},"max":{"x":3,"y":64,"z":0},"machines":[{"type":"block","dimension":"minecraft:overworld","x":0,"y":64,"z":0,"side":"none"},{"type":"block","dimension":"minecraft:overworld","x":1,"y":64,"z":0,"side":"none"},{"type":"block","dimension":"minecraft:overworld","x":2,"y":64,"z":0,"side":"none"},{"type":"block","dimension":"minecraft:overworld","x":3,"y":64,"z":0,"side":"none"}]},"intervalTicks":40,"budget":8,"recipe":{"project":"default","name":"smelt"},"monitor":{"args":{"target":{"type":"block","dimension":"minecraft:overworld","x":3,"y":64,"z":0,"side":"none"},"offset":0,"limit":1},"path":"sides.0.inventory.0.amount","op":"gte","value":1}}
```

```text
/nanites factory status fundicao
/nanites machine_inspect {"target":{"type":"block","dimension":"minecraft:overworld","x":3,"y":64,"z":0,"side":"none"},"offset":0,"limit":1}
```

Observe `cycles`, `measured`, `history`, `checkpoint`, `error`, `cycleStalled`. `measured=true` confirma **o limiar configurado**, não todas as receitas possíveis nem uma produção instantânea. Saída vazia enquanto o forno cozinha pode ser `retrying`; rechecagens têm backoff, nunca texto “feito” como evidência. A fábrica não vira `done` ao chegar a um lingote: monitora e abastece até pause/stop. Retirar a saída ou repor entrada muda o feedback real.

## Objetivo em linguagem natural com replanejamento

Exemplo literal, sem daemon inicial; configurar modelo pelo procedimento existente antes de querer rodadas de IA:

```text
execute as @p run nanites objective create gerente {"mode":"language","text":"Gerencie a fundição continuamente nesta região. Observe forno e estoques, consulte technology_list, material_stock e recipe_search. Crie adapter interno com predicados live para abastecer combustível e minério e retirar somente saída real. Anexe a receita por objective_update deste gerente, conservando mode language para replanejar stalls. Não trate texto feito como prova; confirme lingotes observados no baú de saída.","scope":{"dimension":"minecraft:overworld","min":{"x":0,"y":64,"z":0},"max":{"x":3,"y":64,"z":0},"machines":[{"type":"block","dimension":"minecraft:overworld","x":1,"y":64,"z":0,"side":"none"},{"type":"block","dimension":"minecraft:overworld","x":3,"y":64,"z":0,"side":"none"}]},"intervalTicks":200,"budget":12,"recipe":{"project":"","name":""},"monitor":{"args":{"target":{"type":"block","dimension":"minecraft:overworld","x":3,"y":64,"z":0,"side":"none"},"offset":0,"limit":1},"path":"sides.0.inventory.0.amount","op":"gte","value":1}}
```

Não rode `gerente` e `fundicao` sobre a mesma instalação ao mesmo tempo para este exemplo: há singleflight por dono, mas objetivos distintos podem alternar ciclos e disputar os mesmos recursos. Pause o anterior. Na criação via IA, solicite esses mesmos limites em linguagem natural usando `/nanites ai ...`; `objective_create` é ferramenta real da rodada manual também.

## Persistência e lifecycle

Arquivos por mundo e UUID: `<mundo>/nanites/objectives/<UUID>_<nome>.goal.json`. Receitas continuam em `nanites/adapters/<UUID>/<projeto>`. JSON possui configuração, versão/geração, ciclos, falhas, backoff restante, últimas observações, resumo episódico e até 16 recibos limitados. Status/modelo expõem quatro episódios recentes e snippets limitados; o JSON durável mantém os demais. Estado não é compartilhado entre mundos ou donos.

- Escrita temporária + `FileChannel.force(true)` + rename **atômico obrigatório**, sem substituir arquivo bom por candidato inválido. Falha de disco desabilita efeitos; status continua visível. Corrija armazenamento e reinicie.
- Checkpoint fixa **a versão e o source da receita em execução** e o próximo cursor. Nova versão ativa aplica no próximo ciclo, não reescreve passos do ciclo antigo.
- Antes de cada efeito, salva intenção; depois salva cursor e recibo. Restart normal retoma o próximo passo após validar chunks/telemetria, sem repetir transferências já confirmadas. `when` live é obrigatório para writes de daemons.
- Queda entre efeito e confirmação durável é **ambígua**, pois capabilities/SavedData e filesystem não são transação ACID conjunta. O objetivo fica `failed` e não repete a intenção. Inspecione mundo/escrow e faça update com versão atual após decidir a correção. `resume` sozinho recusa ação ambígua.
- `running`, `paused`, `waiting_model`, `waiting_chunks`, `waiting_player`, `retrying`, `failed`, `done` são observáveis. Paused/failed/done permanecem assim no reload. Dono offline conserva objetivos, histórico e cursor; login não força chunks.
- Editar/pause/resume/stop, logout, mudança de dimensão/morte e parada do servidor invalidam callbacks antigos. Texto/recibos de geração antiga não sobrescrevem objetivos atuais.
- Manual tem prioridade: nova rodada manual, transferência/adapter manual ou construção manual cede a rodada automática, **não apaga o objetivo**. A automação volta após liberação. `ai_cancel` cancela somente a rodada manual; `objective pause/stop` controla autonomia.

## Limites e garantias reais

128 objetivos/mundo, 16/dono, 1..8 máquinas de presença, configuração até 8 KiB, texto até 2048 caracteres. Dimensão explícita e caixa absoluta; endpoints de máquina da autonomia são blocos, nunca armadura. Ferramentas Stage2 de assimilação conservam seus contratos sobre o inventário principal do dono autenticado. Alvos/varreduras/blueprints/receitas são checados contra região. `intervalTicks` 20..72000; `budget` 1..24 limita chamadas do modelo e iterações por ciclo de daemon. Predicados `eq|gt|gte|lt|lte|exists` consultam `machine_inspect`, inclusive índices em caminhos JSON.

Agendador não cria threads/timers: roda no tick, round-robin, até quatro rodadas de observação/start por tick, deadline suave 2 ms. Adapter mantém 8 iterações globais/tick e uma/dono/tick, deadline suave 2 ms. I/O atômico e capabilities de terceiros podem ultrapassar prazo suave; não há interrupção insegura de operação em andamento. No máximo duas rodadas de modelo globais, singleflight por dono, transporte compartilhado e futures/agents fechados em cancelamento. Backoff exponencial limitado; falhas não ambíguas de rodada chegam a failed após oito tentativas; stalls de recursos continuam monitorados com intervalo limitado.

Sem capabilities conformes ou com API de processamento privada não acessível, o status/recibo mostra impossibilidade/stall; não existe sucesso fingido. Máquina precisa ter receita, combustível/energia/fluidos reais e ser carregada pelo jogo. Campos de inventário/capability dependem do mod e face: inspecione antes de configurar predicados. Escrow e assimilação conservam garantias e limitações da Etapa 2.

## Verificação offline

`gradlew.bat :core:check :shared-game:check :forge:build --offline --no-daemon --max-workers=2` com Java 17. `AutonomyTests` usa clock/ticks fake, ports reais do algoritmo `CapabilityTransfer`, `MachineAdapters`, `ControlledTools`, `MultiTurnAgent` com transporte fake e `GameService.serverTick`; máquina fake consome input+FE+fluido e produz saída em ciclos separados. Nenhum jogo ou request HTTP real é executado. Cobertura: recorrência, pause/restart, dono/chunks, cursor sem replay, crash ambíguo, versões/LKG, stale futures/recibos, singleflight, backoff, limites/região, controle bruto, criação de adapter por IA e daemon anexado pela rodada automática real.
