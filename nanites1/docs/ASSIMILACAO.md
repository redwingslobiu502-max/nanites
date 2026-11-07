# Etapa 2 — assimilação real (Forge 1.20.1 / Nanites 0.3.1)

Esta etapa acrescenta inventário interno contábil e observação de tecnologias. **Não implementa autonomia (etapa 3), fabricação de armadura (etapa 4), clonagem de código de mods ou energia gratuita.** Os sistemas anteriores, inclusive armadura já existente, não foram modificados para gastar esse estoque. Não há lista fixa de mods nem aprovação por bloco/mod.

## O que é material assimilado

`assimilate_material` retira a quantidade EXATA de um slot real do inventário principal do dono (0..35), depois de validar ID, identidade completa, quantidade, perfil, quotas e roundtrip NBT. Não usa o antigo `absorb`: aquele contrato anterior continua separado. Registro de item consultado no registry não é posse de material.

A identidade é SHA-256 do SNBT canônico de `ItemStack.save`, com Count normalizado em 1 e chaves CompoundTag ordenadas recursivamente. Preserva tipos NBT, listas, arrays, dano, nome, encantamentos e ForgeCaps serializáveis. A quantidade é armazenada separadamente (long); NBT nunca é truncado no estoque. O SNBT original completo permite recuperação. Propriedades não serializáveis de uma capability privada não podem ser clonadas; roundtrip incompatível é rejeitado.

A composição conhecida é **identidade do item + classificações observadas pelas tags**, não análise química. Cada trait contém evidência da API usada:

- `tag_membership`: `ItemStack.getTags`, sem inferir física secreta do nome;
- `max_stack`, `max_damage`, `damage`: propriedades do stack real;
- `fuel_ticks`: `ForgeHooks.getBurnTime(stack, RecipeType.SMELTING)`, não FE;
- `tool_actions`: ações registradas em `ToolAction.getActions` para as quais `stack.canPerformAction` é verdadeiro;
- ferramentas `TieredItem`: nível, velocidade e bônus de ataque públicos do Tier;
- `ArmorItem`: defesa, toughness, slot; atributos públicos por EquipmentSlot em todos os itens;
- sem bônus aplicado ao jogador por observar essas propriedades.

Provenance inclui dono, dimensão, tick mundial, origem/slot, namespace, versão disponível do mod, Minecraft, Forge, Nanites e versão do formato de observação. Valor indisponível é `unavailable`. O histórico retém os últimos oito depósitos por identidade; contadores `deposited`, `withdrawn` e `quantity` permanecem cumulativos. Perfil derivado válido substitui o anterior atomicamente; candidato inválido não substitui o último perfil bom.

`repair_protection_stock_units` é o número de itens reais disponíveis no pool reutilizável, **não pontos de proteção nem HP/FE**. Seu uso posterior exige débito pela API, custo/receita explícitos e regras da etapa futura.

## Ferramentas IA e comandos

Mesmos nomes, argumentos e backend para IA e `/nanites NOME JSON`. Todos os campos listados nos schemas são obrigatórios; campos extras, quantidades fracionárias e valores fora do intervalo são rejeitados.

| Ferramenta | Parâmetros |
|---|---|
| `material_inspect` | `query` string, `registry` boolean, `offset` >=0, `limit` 1..8 |
| `assimilate_material` | `slot` 0..35, `item` ID, `identity` hash observado, `amount` 1..64 |
| `material_stock` | `offset` >=0, `limit` 1..8 |
| `recover_material` | `key` hash do estoque, `amount` 1..2304 |
| `technology_scan` | `target`, `offset` >=0, `limit` 1..8 |
| `technology_assimilate` | `target`, `project`, `name`, `source` string JSON de adapter ou string vazia |
| `technology_list` | `offset` >=0, `limit` 1..8 |
| `recipe_search` | `query` string, `type` ID ou vazio, `offset` >=0, `limit` 1..8 |

Target mantém o contrato da etapa 1: `{type,dimension,x,y,z,side}`. Tecnologia exige `type:"block"`; dimensão vazia é a atual. Side: `auto`, `none`, `up`, `down`, `north`, `south`, `east`, `west`. Não carrega chunks à força. `project`/`name` aceitam `[A-Za-z0-9_-]{1,48}`.

### Exemplo: retirar e recuperar quatro lingotes

Coloque pelo menos quatro lingotes no inventário e consulte:

```text
/nanites material_inspect {"query":"minecraft:iron_ingot","registry":false,"offset":0,"limit":1}
/nanites material_inspect {"query":"forge:ingots","registry":true,"offset":0,"limit":4}
```

O segundo comando pesquisa defaults do registry/tags e NÃO fornece itens. Para o primeiro, copie o `slot`, `item` e `identity` realmente retornados. **Substitua `HASH_OBSERVADO` pelo hash completo (64 hexadecimais) e 0 pelo slot retornado**:

```text
/nanites assimilate_material {"slot":0,"item":"minecraft:iron_ingot","identity":"HASH_OBSERVADO","amount":4}
/nanites material_stock {"offset":0,"limit":4}
/nanites recover_material {"key":"HASH_OBSERVADO","amount":4}
```

Sucesso de consumo é `CONSUMED_EXACT_SERVER_MAIN`, com `consumed:4`. Recuperação é `RECOVERED_EXACT_SERVER_MAIN`, `recovered:4`. ID/NBT alterado desde inspect, estoque reservado ou inventário sem capacidade suficiente causam erro **antes do débito**. Recuperação distribui em stacks legais, preferindo identidade/NBT iguais; não joga itens no chão. Mudança de limite de stack por atualização do mod pode exigir recuperação em quantidades menores.

### Exemplo: tecnologia observada e integração executável

Troque coordenadas pelo bloco existente e já carregado:

```text
/nanites technology_scan {"target":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":10,"side":"auto"},"offset":0,"limit":2}
/nanites technology_assimilate {"target":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":10,"side":"auto"},"project":"fabrica","name":"observador","source":""}
/nanites technology_list {"offset":0,"limit":4}
/nanites adapter_execute {"project":"fabrica","name":"observador","preview":false}
/nanites adapter_status {"project":"fabrica"}
```

Source vazio cria adapter versão 1 com uma operação real `machine_inspect`. **É integração operacional de observação, não fabricação de itens nem cópia da máquina.** Execute/poll até `COMPLETED`; `QUEUED` não é conclusão. Após reinício, `technology_list` conserva project/name/blueprint; `adapter_load` ou `adapter_execute` carrega o arquivo salvo sem reload do jogo.

Para integrar alimentação/extração reais, envie em `source` uma string JSON da receita declarativa da etapa 1 (`version`, `steps`, `tool`, `args`, `repeat`, `when` opcional; veja MAQUINAS.md). Exemplo mínimo explícito de observação pela face superior:

```text
/nanites technology_assimilate {"target":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":10,"side":"up"},"project":"fabrica","name":"observador","source":"{\"version\":2,\"steps\":[{\"tool\":\"machine_inspect\",\"args\":{\"target\":{\"type\":\"block\",\"dimension\":\"minecraft:overworld\",\"x\":10,\"y\":64,\"z\":10,\"side\":\"up\"},\"offset\":0,\"limit\":1},\"repeat\":1}]}"}
```

Conteúdo alterado exige versão maior; substituição inválida conserva o adapter anterior. Antes da publicação, a implementação Forge valida targets carregados, capacidades requeridas pelos passos de transferência nas faces solicitadas, controles reais `powered/enabled` e predicates. Ausência da API é erro explícito; existência de API não garante recurso disponível, throughput ou receita privada executável. Execução futura sempre resolve capacidades novamente e fornece recibos reais.

Tecnologia registra BlockState/propriedades legais, tags, faces com item/energy/fluid capabilities, valores observados e provenance. Não há detector universal de reator ou associação automática de toda receita a todo bloco do mesmo mod.

### Exemplo: pesquisar receitas públicas

```text
/nanites recipe_search {"query":"minecraft:iron_ingot","type":"","offset":0,"limit":4}
/nanites recipe_search {"query":"forge:ingots/iron","type":"minecraft:crafting","offset":0,"limit":2}
```

Busca ID, inputs públicos em `Ingredient.toJson`, output/tags. Resultado contém ID, type, serializer, namespace/version, inputs, output e limitações. Crafting shaped expõe width/height; cooking expõe tempo/experiência. Receitas especiais, condições datapack/mod, contagens/layout customizados e APIs privadas **não são tratados como semântica genérica**. Publicar adapter NÃO modifica o RecipeManager e NÃO registra arbitrariamente um serializer/código novo de mod. A IA pode criar receitas de integração em tempo real via `adapter_create` e vinculá-las via `technology_assimilate`.

### Paginação e IA

Inventário usa cursor de slots; registry usa IDs ordenados; receitas usam cursor sobre **todos** os IDs ordenados (inclusive não correspondentes). Uma página vazia não significa fim: continue `next_offset` até `complete:true`. Inspect/receitas examinam no máximo 128 candidatos por chamada; receitas também têm orçamento de 5ms entre candidatos (uma API de mod pode exceder isso). Páginas têm orçamento de tamanho e podem retornar menos que `limit`. Reload de datapacks pode mudar o cursor; reinicie em 0.

Stock/technology usam chaves ordenadas e `revision`. Stock não expõe SNBT integral na ferramenta; conserva integral no SavedData e mostra provenance mais recente/contagem retida. Tecnologia omite detalhes grandes de blueprint com flag explícita e link do adapter; receitas grandes na observação são resumidas com flag e podem ser pesquisadas separadamente. Erros de API/mod aparecem como erros, não integração verificada fictícia.

Os oito schemas entram no allowlist comum sem remover ferramentas anteriores: catálogo Forge máximo atual = **39** (35 sem grant runtime). `LocalChatClient.toolCeiling()` usa união real de nomes, hard cap 128 e request <=262144 bytes. O orçamento conservador cumulativo da IA cresce com tamanho real dos schemas/turnos (`catalogBudget`), limitado a 524288 byte-tokens; limites de turns/calls/transcript permanecem. Aumentar catálogo não dá execução Java/arbitrária. TLS e endpoint anterior preservados.

`GameService.machineGateway` e `machineLiveProvider` fazem marshalling via `Host.main`; callbacks cancelados ou de geração anterior não executam. `state`/`catalog` consultam `machine_live.assimilation` atualizado, incluindo revision, número de identidades/tecnologias/reservas, unidades reais disponíveis e recipe_count ao vivo. Adapters recém-assimilados aparecem imediatamente no catálogo de adapters. Nenhuma atividade autônoma nova é agendada.

## Persistência, contabilidade e crash

Fonte de verdade: SavedData do Overworld, `<mundo>/data/nanites_assimilation.dat`, particionado por UUID. Formato 1, JSON validado e fragmentado em StringTags pequenos para não exceder UTF de NBT. Mantém material/quantidades/reservas/provenance/perfis/tecnologias em reinício, morte, logout e troca de dimensão; não há arquivo pessoal de configuração necessário.

Invariantes: `quantity = deposited - withdrawn`; `available = quantity - sum(reservas)`; débito nunca pode gastar reserva alheia; commit é uma vez por token. Validar ledger e preparar TODOS os stacks precede as atribuições diretas no inventário principal. Não usa um handler de mod que possa aceitar parcialmente no meio da transação.

**Não é ACID contra crash.** Delta do inventário do jogador e SavedData são gravados por ciclos distintos do Minecraft; queda entre saves pode perder/duplicar a última operação. Não há WAL/fsync/2-phase commit entre esses arquivos. Sucesso de operação confirma mutação server-thread e dirty flag, não flush síncrono. Faça backup/save normal do mundo; não anuncie segurança ACID. SavedData corrompido/incompatível é desabilitado e recusa sobrescrita/reset vazio; arquivo existente ilegível também recusa substituição vazia.

Registro de tecnologia e arquivo adapter também não são ACID em conjunto: preflight valida observações, APIs, blueprint e quota; depois publica adapter e SavedData. Falha posterior retorna erro explícito dizendo que adapter já está ACTIVE, sem fingir tecnologia registrada; rescan/retry. Substituições dentro de cada registry/ledger têm last-known-good.

Quotas: 256 identidades, 128 tecnologias e 128 reservas por dono; 4096 donos e JSON mundial <=16777216 caracteres; payload SNBT <=16384 caracteres, perfil <=8192, provenance de depósito <=1024; quantidade por identidade <=1000000000. Ledger faz validação/cópia bounded no main thread; bases grandes ou APIs lentas de mods podem aumentar custo, não há garantia de latência rígida.

Nesta etapa assimilação de origem máquina NÃO foi adicionada: transfira primeiro os itens reais para o inventário do dono com `transfer_items` da etapa 1, recupere eventual escrow e só então assimile. O escrow anterior permanece independente. Não há perda intencional do NBT nem success fake para capabilities privadas não expostas.

## APIs para etapas 3/4 (não implementadas aqui)

`forge/src/main/java/dev/nanites/forge/ForgeAssimilation.java`, métodos static públicos, todos exigem thread principal do MinecraftServer:

```java
String identity(ItemStack stack);
String deposit(MinecraftServer s, UUID owner, ItemStack owned, int amount, String origin);
ItemStack withdraw(MinecraftServer s, UUID owner, String key, int amount);
long available(MinecraftServer s, UUID owner, String key);
JsonObject traits(MinecraftServer s, UUID owner, String key); // profile com traits/evidence/tags
String reserve(MinecraftServer s, UUID owner, String key, int amount, String purpose);
JsonObject reservations(MinecraftServer s, UUID owner); // tokens/key/amount/purpose, para reconciliar reinício
void release(MinecraftServer s, UUID owner, String token);
ItemStack commit(MinecraftServer s, UUID owner, String token);
JsonObject live(MinecraftServer s, UUID owner);
JsonObject execute(MinecraftServer s, UUID owner, String tool, JsonObject args);
void validateTechnology(MinecraftServer s, UUID owner, JsonObject normalizedArgs);
```

Deposit **consome/shrink** o stack real entregue; o chamador confiável precisa já possuir/remover esse recurso de sua origem, não construir um stack do nada. Withdraw/commit **debitam** e entregam item original; caller deve inserir com limite de stack real ou usar o material em custo comprovável. Quantidades grandes retornadas pela API devem ser repartidas pelo caller, não serializadas como um único Count NBT byte. Reserva não gasta ainda; commit gasta uma vez, release libera. Estabeleça finalidade/custo e reconcilie tokens persistidos; não converta `fuel_ticks` em FE fictícia.

Camada testável sem loader: `shared-game/.../AssimilationLedger.java`, `Sample`, `Inventory` (`size/get/prepare/set`), `consume/deposit/peek/withdraw/recover/reserve/reservations/reserved/commit/release/traits/stock/technologies/live/save/load`. Implementação Inventory deve preparar tudo e fazer `set` infalível depois de validar; não reutilize direto em capacidades externas não transacionais.

Integração IA/futura: `GameService.machineOperation(UUID,String,JsonObject)`, `machineGateway(UUID)`, `machineSnapshot(UUID)`; tecnologia listada fornece project/name para os adapters existentes. `Host.validateTechnology` e `Host.assimilationSnapshot` têm implementação Forge. Blueprint de máquina segue `MachineAdapters` da etapa 1; planejamento autônomo e consumo por armadura ficam para etapas futuras.

## Verificação

Comando em cmd.exe, Java17 offline (somente build/testes, sem jogo):

```bat
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
gradlew.bat :core:check :shared-game:check :forge:build --offline --no-daemon --max-workers=2
```

Testes novos: `AssimilationTests` cobre inventário fake, NBT/identidade/tag/evidência, consumo/recovery exatos, capacidade e preflight inválidos, owner/world isolation, reservas/commit/release/reinício, corrupção/LKG, 1000 operações determinísticas de conservação, comandos, catálogo live e loop IA com seis ferramentas em sete turnos. `ForgeAssimilationTests` usa NBT/SavedData REAIS (arrays/tipos/nome/encantamento/ForgeCaps, >65535 caracteres, binary roundtrip e dados corrompidos), sem iniciar Minecraft.

Build de integração documentado em `validation-logs/stage2-assimilation-build-3.log`: sucesso, 1087 checks novos compartilhados, 11 checks novos NBT, regressões anteriores preservadas. A verificação final após ajustes está em `validation-logs/stage2-assimilation-final.log`. Não houve bump de versão, publicação/cópia de jar em dist nem alterações em jevmod/Fabric. O jar gerado por Gradle é apenas artefato normal do build solicitado. Não se verificou comportamento dentro de um jogo/modpack real, conforme restrição de não iniciar o jogo.
