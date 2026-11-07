# Etapa 1 — máquinas Forge 1.20.1 e adapters internos

Base preservada: **0.3.1**, sem publicar/copiar JAR para `dist` ou instalação. Esta etapa não implementa assimilação nova, agentes autônomos persistentes nem armadura viva. Não altera Fabric, jevmod ou proteções existentes.

## O que está conectado

`/nanites` e as ferramentas da IA chegam ao mesmo `GameService.machineOperation`. Callbacks da IA passam pelo marshalling existente (`Host.main`, lifecycle e futures canceláveis); Forge rejeita acesso fora da thread servidor. `NanitesForge.Host.machineOperation` chama **ForgeMachines**, que consulta de verdade `ForgeCapabilities.ITEM_HANDLER`, `ENERGY` e `FLUID_HANDLER` no BlockEntity e executa `extractItem/insertItem`, `extractEnergy/receiveEnergy`, `drain/fill`.

Mods que **expõem capabilities Forge conformes** nesses blocos/lados são descobertos automaticamente. Mod instalado não significa integração proprietária disponível. Nenhum adapter externo, API universal `start machine`, reflection, código Java arbitrário, dependência nova ou restart para carregar receitas.

Máquinas não exigem grant, nanites contábeis ou whitelist de mods. Os grants anteriores de intervenções corporais continuam intactos. Nenhum recurso é criado: transferências retiram do produtor/inventário e entregam ao consumidor/inventário.

## Contrato dos alvos

Todos os campos são obrigatórios:

```json
{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"auto"}
```

- `type`: `block` ou `player` (somente o dono autenticado; itens nos 36 slots principais/hotbar, índices 0..35 via PlayerMainInvWrapper; não altera armadura vestida ou offhand).
- `dimension`: ID real; `""` resolve a dimensão atual do dono a cada chamada. Para receitas estáveis, prefira ID explícito.
- Coordenadas absolutas; em `player`, usa-se o inventário do dono atual e a posição/dimensão dele. Mantenha os campos; o campo dimension ainda é validado e deve indicar dimensão existente (prefira `""`).
- `side`: `auto`, `none`, `down`, `up`, `north`, `south`, `west`, `east`. `none` consulta a capability sem face; **não equivale a todos os lados**.
- `auto`: enumera na ordem acima (none primeiro). Inspeção mostra cada face, sem somar capacidades duplicadas. Transferência escolhe a primeira combinação que aceita simulação positiva e executa **uma** transferência de uma identidade/slot por chamada; não transfere novamente por faces que apontem para o mesmo handler.
- Chunk precisa já estar carregado. Chamadas diretas retornam erro `WAITING_CHUNK`; pipelines ficam `WAITING_CHUNK` e repetem essa etapa quando houver carregamento externo. **Nunca force-load**. Fora da borda/altura: erro. Dono offline/morto: erro; logout/morte/troca de dimensão cancela jobs, não desfaz efeitos anteriores.

## Comandos exatos e ferramentas IA

Cada nome abaixo também é uma ferramenta de função da IA com JSON Schema real, obrigatório, sem campos extras. `preview` é booleano; números são inteiros.

```text
/nanites machine_help
/nanites machine_schema transfer_items
/nanites machine_schema adapter_create
```

### Inspecionar e varrer

```text
/nanites machine_inspect {"target":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"auto"},"offset":0,"limit":2}
/nanites machine_inspect {"target":{"type":"player","dimension":"","x":0,"y":0,"z":0,"side":"none"},"offset":0,"limit":16}
/nanites scan_machines {"target":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"auto"},"radius":8,"offset":0,"limit":128}
```

`machine_inspect`: BlockState real, propriedades e valores legais, sinal redstone recebido, slots/inventário/extrabilidade simulada, energia armazenada/capacidade/canExtract/canReceive, tanques/fluidos/capacidades; também escrow do dono. `offset` 0..4095, `limit` 1..16. Em side auto, `effective_limit` é no máximo 2 por lado para manter recibos pequenos; avance offset ou escolha uma face explícita para páginas maiores. NBT no recibo tem resumo de 96 caracteres e indicador `nbt_truncated`; **a operação e escrow conservam NBT integral**, não usam resumo para comparar identidades.

`scan_machines`: raio 0..16, até 128 células e 8 resultados por página. `offset`/`next_offset` são **índices de células**, não de resultados. Ordem estável x mais rápido, depois y, depois z no cubo centrado no alvo. Repita com o `next_offset` retornado até `complete=true`. Centros/raio/dimensão não devem mudar durante paginação. Estado dos blocos continua live (não é snapshot congelado). `PARTIAL_UNLOADED` e `unloaded_cells` indicam células ignoradas; para revisitar, refaça essa página após carregar externamente.

### Transferir itens, FE, fluidos

```text
/nanites transfer_items {"from":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"east"},"to":{"type":"block","dimension":"minecraft:overworld","x":11,"y":64,"z":20,"side":"west"},"from_slot":-1,"to_slot":-1,"amount":16,"preview":true}
/nanites transfer_items {"from":{"type":"player","dimension":"","x":0,"y":0,"z":0,"side":"none"},"to":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"up"},"from_slot":0,"to_slot":-1,"amount":8,"preview":false}
/nanites transfer_items {"from":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"down"},"to":{"type":"player","dimension":"","x":0,"y":0,"z":0,"side":"none"},"from_slot":-1,"to_slot":-1,"amount":8,"preview":false}
/nanites transfer_energy {"from":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"east"},"to":{"type":"block","dimension":"minecraft:overworld","x":11,"y":64,"z":20,"side":"west"},"amount":1000,"preview":false}
/nanites transfer_fluid {"from":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"east"},"to":{"type":"block","dimension":"minecraft:overworld","x":11,"y":64,"z":20,"side":"west"},"from_slot":0,"to_slot":-1,"amount":1000,"preview":false}
```

- `amount`: 1..1.000.000; itens = contagem, energia = FE, fluidos = mB.
- Slots/tanques: -1 automático (primeiros 256) ou índice 0..4095. Itens: retirada de um slot, inserção num slot específico ou distribuição automática.
- **IFluidHandler não possui drain/fill por índice de tanque**. `from_slot` escolhe a **identidade** observada naquele tanque; drain por FluidStack pode retirar essa mesma identidade de outro tanque interno. `to_slot` apenas valida `isFluidValid` no tanque solicitado; **fill decide o tanque efetivo**. O recibo explicita isso. Não promete selecionar tanque que a API não permite.
- Mesma coordenada/handler é rejeitada/ignorada para evitar aliases de faces. Redes de mods podem expor wrappers diferentes sobre o mesmo armazenamento: não há introspecção proprietária para identificar todos esses aliases.
- `preview=true` só consulta/simula; `SIMULATED` não é entrega. `NO_TRANSFER` explica ausência de combinação compatível.
- Retorno real: `simulated`, `extracted`, `moved`, `refunded`, `held`, identidade e faces selecionadas; FE inclui antes/depois observados. Use nova inspeção para observar consumidores após seus próprios ticks.

### Conservação e escrow

Simulação **não reserva** capacidade nem ignora limites de throughput. A implementação simula retirada e entrada antes de retirar de fato, entrega somente a quantidade efetivamente extraída, tenta devolver sobras ao produtor. Se o produtor não recebe (comum em energia/saídas), mantém o restante **em escrow**, não joga fora nem fabrica uma restituição fictícia. `PARTIAL_ESCROW` informa a retenção.

Escrow é `SavedData` do mundo/overworld (`nanites_machine_escrow`), separado por UUID do dono, preserva ItemStack/FluidStack com tags integrais ou quantidade FE e sobrevive a morte, logout, dimensão e salvamento normal. Limite 128 entradas por dono; inspeção mostra primeiras 16. Recupere a primeira entrada repetidamente (índices avançam após remoção):

```text
/nanites machine_recover {"to":{"type":"block","dimension":"minecraft:overworld","x":11,"y":64,"z":20,"side":"auto"},"index":0,"slot":-1,"preview":true}
/nanites machine_recover {"to":{"type":"block","dimension":"minecraft:overworld","x":11,"y":64,"z":20,"side":"auto"},"index":0,"slot":-1,"preview":false}
```

Só a quantidade realmente recebida é removida do escrow. Itens podem ser recuperados para o inventário do dono. Não há transação atômica de disco entre chunks/mods/SavedData: crash abrupto antes de salvar o mundo não tem garantia ACID. Capabilities conformes devem respeitar SIMULATE, identidade e retorno; mods que mentem/lançam exceção **depois** de alterar silenciosamente recursos violam o contrato e não podem ser compensados com certeza por uma API genérica. Esses comportamentos não são certificados aqui.

### Controle real por BlockState/redstone

```text
/nanites machine_control {"target":{"type":"block","dimension":"minecraft:overworld","x":9,"y":64,"z":20,"side":"none"},"property":"powered","value":"true","preview":false}
```

Ex.: alavanca real já colocada e conectada à máquina. Só `powered`/`enabled` booleanos **que existam no bloco alvo**; valores inexistentes são erros. Usa setBlock com atualização de vizinhos, retorna antes/solicitado/observado. `lit` é saída e não controle. Bloco/mod pode reverter propriedade no próximo tick; um flag não equivale a inventar uma API de funcionamento. Confirme sinal/propriedade e estado do consumidor por inspeção.

## Adapters internos executáveis e recarregáveis

São receitas **de composição das operações acima**, com consulta live e predicate, não criação de API inexistente. A IA consegue gerar `source`, criar/persistir/ativar via ferramenta sem o usuário escrever arquivo; executar preview, conferir recibos, aumentar versão e substituir durante a sessão.

Formato de `source`:

```json
{
  "version": 1,
  "steps": [
    {
      "tool": "transfer_items",
      "args": {
        "from": {"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"east"},
        "to": {"type":"block","dimension":"minecraft:overworld","x":11,"y":64,"z":20,"side":"west"},
        "from_slot":0,"to_slot":-1,"amount":8,"preview":false
      },
      "repeat": 16,
      "when": {
        "tool":"machine_inspect",
        "args":{"target":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"east"},"offset":0,"limit":1},
        "path":"sides.0.inventory.0.amount","op":"gt","value":0
      }
    }
  ]
}
```

`when` é opcional. Cada iteração consulta de novo estado real. Dot path permite índices de array; `eq`, `gt`, `gte`, `lt`, `lte`, `exists` (valor booleano nesse último). Se não houver caminho, comparação comum dá false. `repeat` é obrigatório 1..256. 1..32 passos, até 4096 iterações. Etapas podem inspecionar, varrer, transferir itens/FE/fluidos ou alterar controle. Não podem chamar outros adapters, recuperar escrow, criar grants, executar Java, reflection ou comandos arbitrários.

Comando de criação completo, seguido de preview/execução/status:

```text
/nanites adapter_create {"project":"default","name":"alimentar","source":"{\"version\":1,\"steps\":[{\"tool\":\"transfer_energy\",\"args\":{\"from\":{\"type\":\"block\",\"dimension\":\"minecraft:overworld\",\"x\":10,\"y\":64,\"z\":20,\"side\":\"east\"},\"to\":{\"type\":\"block\",\"dimension\":\"minecraft:overworld\",\"x\":11,\"y\":64,\"z\":20,\"side\":\"west\"},\"amount\":1000,\"preview\":false},\"repeat\":20}]}"}
/nanites adapter_execute {"project":"default","name":"alimentar","preview":true}
/nanites adapter_status {"project":"default"}
/nanites adapter_execute {"project":"default","name":"alimentar","preview":false}
/nanites adapter_status {"project":"default"}
/nanites adapter_cancel {"project":"default"}
/nanites adapter_list {"project":"default"}
/nanites adapter_load {"project":"default","name":"alimentar"}
```

Preview também passa pela fila: força `preview=true` nas ações; consultas permanecem reais. Não simula um mundo virtual evoluindo entre etapas. Aguarde `COMPLETED` antes de iniciar outro job do mesmo dono, ou cancele. `QUEUED`/`RUNNING`/`WAITING_CHUNK` **não são conclusão**. `status` mostra etapa, repetição, executadas, ignoradas, último recibo e erro. O cancelamento não reverte entregas anteriores.

Registry versionado: valida **toda** receita e schemas antes de substituir. Modificação de conteúdo exige versão crescente; mesmo conteúdo/versão pode recarregar; regressão/erro deixa última receita válida ativa em memória. Jobs já iniciados capturam a versão validada original, portanto reload não modifica metade de um job.

Persistência por mundo: `<mundo>/nanites/adapters/<UUID>/<projeto>/<nome>.adapter.json`. Use `project:"default"` como projeto padrão. Projeto/nome simples `[A-Za-z0-9_-]{1,48}`; escrita temporária seguida de rename; sem caminhos livres. `list` mostra ativos e arquivos salvos (até 128); `load` valida antes de trocar; `execute` carrega receita salva se ainda não está ativa. Não precisa restart. Jobs são **finitos e locais à sessão**, não agentes autônomos persistentes (próxima etapa de autonomia será separada).

Executor no tick global: até 8 iterações entre donos, uma por dono por tick, orçamento suave de 2ms com rotação justa. Uma iteração/mod callback não é preemptível; scan/slots/repeats são limitados. Operações isoladas são síncronas na thread servidor depois do marshalling; workflows longos usam a fila. Busca simulada de transferência tem limite de 256 candidatos/2ms; `SEARCH_BUDGET_EXHAUSTED` não retirou recursos: escolha lados/slot explícitos ou divida em etapas de adapter. Um handler proprietário bloqueante pode ultrapassar orçamento; não há execução Minecraft em worker.

## APIs para as etapas seguintes

- `core/MachineTools`: `NAMES`, `OPERATIONS`, `ADAPTERS`, `schema(String):JsonObject`, `validate(String,JsonObject)`, schemas compartilhados por IA/comandos/receitas.
- `GameService.Host.machineOperation(UUID,String,JsonObject)` default UnsupportedOperationException (fakes/Fabric continuam compiláveis, sem alterar Fabric).
- `GameService.machineOperation(UUID,String,JsonObject):JsonObject` (thread servidor); `machineGateway(UUID):ControlledTools.MutationGateway` (async marshalling/lifecycle).
- `GameService.machineSnapshot(UUID):JsonObject`, `machineLiveProvider(UUID):ControlledTools.ExtensionProvider`, `MachineAdapters.snapshot(UUID)`/`status(UUID,String)` são metadados live. `ControlledTools.extensions(provider)` atualiza `data.machine_live` em **state e catalog a cada leitura**, inclusive após hot reload na mesma rodada, com marshalling e lifecycle. Estado físico vem de `machine_inspect`/`scan_machines`, não de cache de inferência.
- `MachineAdapters(Path,Backend)`, `Backend.execute(UUID,String,JsonObject)`, `validate(String)`, `command(UUID,String,JsonObject)`, `tick()`, `forget(UUID)`, `WaitingChunk`: composição, persistência, captura de versão e executor reutilizáveis.
- `CapabilityTransfer.Port.extract/receive(int,boolean)`, `move(Port,Port,int,boolean,Escrow):Result`; resultado contabiliza simulated/extracted/moved/refunded/held.
- `ForgeMachines.execute(MinecraftServer,UUID,String,JsonObject)` consulta/adquire capabilities a cada chamada, sem guardar LazyOptional invalidado; `EscrowData extends SavedData` conserva sobrantes.
- `ControlledTools` e `MultiTurnAgent` expõem schemas, validam argumentos e entregam recibos em turnos seguintes. Não há ativação fictícia de adapter.

## Validação

Comando Windows (Java 17, cache offline existente):

```bat
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
gradlew.bat -Ploader=forge :core:check :shared-game:check :forge:build --offline --console=plain
```

`shared-game:machineTests` integra `check`: portas fake de inventário/tanques/energia para limites/simulação/compensação/escrow, registry/pipelines/predicados live, validação, reload last-known-good, persistência, cancelamento/lifecycle, comandos/gateway e transporte IA multiturmo create → execute → status → inspect. Regressões anteriores de core/runtime/construção continuam executadas.

Resultado final: **BUILD SUCCESSFUL em 38s**, `validation-logs/stage1-machines-final.log`. Core: 33 + 57; shared-game: construção 252, jogo 51, máquinas 84, runtime 91; ForgeAttributeTests: 15. Teste de máquinas inclui transmissão dos 31 schemas completos e snapshots state/catalog live após reload. Ajuste necessário ao protocolo: teto de schemas por pedido 32 (antes 16), reserva conservadora Forge 65536 dentro do máximo já existente; TLS, grants e proteções de dano não foram relaxados. A reserva é cumulativa por rodada; quotas continuam limitadas e jobs já enfileirados podem ser consultados em rodada posterior.

**Não foi iniciado Minecraft nem IA real**; testes fake não certificam cada mod instalado. Forge é compilado contra as APIs reais do cache. Logs da etapa em `validation-logs/stage1-machines-build*.log`; o resumo final da chamada registra o resultado final. JAR em `forge/build/libs` é somente saída intermediária do build solicitado, não publicação final.
