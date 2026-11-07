# Teste ponta a ponta — quatro etapas, Forge 1.20.1 / Java 17

**Release local:** `dist/stage4-armor-20261107/nanites-forge-1.20.1-0.3.1-stage4.jar`. O sufixo distingue a entrega; a versão interna continua **0.3.1**. Auditoria e SHA256: [RELEASE_STAGE4_FORGE.md](RELEASE_STAGE4_FORGE.md).

## O que foi verificado e o que falta

Offline: logs finais das quatro etapas, build/reobf da etapa 4, classes/recursos/metadados do JAR e igualdade de hash/tamanho da cópia. O build final reportou 1823 checks/casos (59 novos de armadura). Fronteiras de mundo/modelo usam fakes; testes Forge usam NBT/itens/atributos reais em fixture de registries, **não launcher/EventBus**.

**Todos os passos de gameplay abaixo são verificações a executar pelo usuário.** Não houve lançamento de Minecraft, servidor ou modelo real nesta entrega. Registre resultados/recibos e `logs/latest.log`; teste primeiro em mundo novo com backup, depois em cópia do modpack.

## 0. Instalação e preparação

1. Com a instância fechada, use Minecraft **1.20.1**, Forge **47.4.28** e **Java 17**. Copie somente o JAR stage4 para `mods` (servidor e clientes em multiplayer). Retire outros JARs Nanites **da instância**, preservando os de `dist`: duas versões do mesmo mod não podem coexistir. Não use JAR dev, core separado ou Fabric.
2. Inicie manualmente mundo com cheats. Execute `/nanites status`. Com cheats: `/give @s nanites:reservoir 1` e `/give @s nanites:box 2`; com reservatório no inventário, clique com caixa na mão principal no ar, aguardando pelo menos 10 ticks entre usos. Confirme consumo e saldo real.
3. Nos exemplos, coordenadas/lados/slots são exemplos, não detecção automática. Use alvos existentes, distintos e carregados. Comandos JSON longos podem exceder chat vanilla: use command block/console como `execute as @p run nanites ...` (sem `/`, escolhendo explicitamente o jogador correto em multiplayer). JSON é em uma linha. Não inicie modelo: os testes manuais/factory funcionam sem ele.

## 1. Máquinas reais e adapters recarregáveis

Prepare produtor em (10,64,20) e consumidor em (11,64,20). Inspecione ambos; uma face pode não expor a capability. Baús permitem testar itens; FE e fluidos exigem blocos de mods com APIs públicas compatíveis e recursos reais, não baús vanilla.

```text
/nanites machine_help
/nanites machine_schema transfer_items
/nanites machine_inspect {"target":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"auto"},"offset":0,"limit":2}
/nanites machine_inspect {"target":{"type":"block","dimension":"minecraft:overworld","x":11,"y":64,"z":20,"side":"auto"},"offset":0,"limit":2}
/nanites transfer_items {"from":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"east"},"to":{"type":"block","dimension":"minecraft:overworld","x":11,"y":64,"z":20,"side":"west"},"from_slot":-1,"to_slot":-1,"amount":16,"preview":true}
/nanites transfer_items {"from":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"east"},"to":{"type":"block","dimension":"minecraft:overworld","x":11,"y":64,"z":20,"side":"west"},"from_slot":-1,"to_slot":-1,"amount":16,"preview":false}
/nanites transfer_energy {"from":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"east"},"to":{"type":"block","dimension":"minecraft:overworld","x":11,"y":64,"z":20,"side":"west"},"amount":1000,"preview":false}
/nanites transfer_fluid {"from":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"east"},"to":{"type":"block","dimension":"minecraft:overworld","x":11,"y":64,"z":20,"side":"west"},"from_slot":0,"to_slot":-1,"amount":1000,"preview":false}
```

Para FE/fluidos, faça primeiro a mesma chamada com `preview:true`. Após cada ação, inspecione origem/destino de novo; confira `extracted`, `moved`, `refunded`, `held` e contagem/FE/mB reais (a máquina pode consumir no seu próprio tick). Preview não deve gastar. Teste destino cheio/sem API, recurso insuficiente e chunk descarregado: não aceite `SIMULATED`, `QUEUED`, `WAITING_CHUNK` ou texto “feito” como transferência concluída. `PARTIAL_ESCROW` exige inspeção/recuperação, não repetir cegamente. Sintaxe de `machine_recover` e limitações de tanques: [MAQUINAS.md](MAQUINAS.md).

Crie o adapter literal `alimentar` de **MAQUINAS.md → Adapters internos executáveis** (usa FE e os mesmos alvos), depois:

```text
/nanites adapter_execute {"project":"default","name":"alimentar","preview":true}
/nanites adapter_status {"project":"default"}
/nanites adapter_execute {"project":"default","name":"alimentar","preview":false}
/nanites adapter_status {"project":"default"}
/nanites adapter_load {"project":"default","name":"alimentar"}
```

Aguarde `COMPLETED` antes da próxima execução. No arquivo `<mundo>/nanites/adapters/<UUID>/default/alimentar.adapter.json`, mude a quantidade e aumente `version`; `adapter_load` deve ativar a nova receita sem restart. Tente JSON inválido/versão regressiva e confirme erro com última receita boa preservada; restaure arquivo válido. Jobs em curso mantêm a versão capturada, cancelamento não desfaz entregas. Para teste vanilla sem FE, use o adapter `smelt` da etapa 3 abaixo.

## 2. Assimilação, recuperação e tecnologia

Ponha quatro lingotes reais no inventário principal. Copie o slot e o hash completo de `material_inspect` (não do registry); substitua os placeholders abaixo. Reservas não devem poder ser gastas duas vezes.

```text
/nanites material_inspect {"query":"minecraft:iron_ingot","registry":false,"offset":0,"limit":4}
/nanites assimilate_material {"slot":0,"item":"minecraft:iron_ingot","identity":"HASH_OBSERVADO","amount":4}
/nanites material_stock {"offset":0,"limit":4}
/nanites recover_material {"key":"HASH_OBSERVADO","amount":4}
/nanites technology_scan {"target":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"auto"},"offset":0,"limit":2}
/nanites technology_assimilate {"target":{"type":"block","dimension":"minecraft:overworld","x":10,"y":64,"z":20,"side":"auto"},"project":"fabrica","name":"observador","source":""}
/nanites technology_list {"offset":0,"limit":4}
/nanites adapter_execute {"project":"fabrica","name":"observador","preview":false}
/nanites adapter_status {"project":"fabrica"}
/nanites recipe_search {"query":"minecraft:iron_ingot","type":"","offset":0,"limit":4}
```

Consumo: `CONSUMED_EXACT_SERVER_MAIN`/4 e inventário -4; recuperação: `RECOVERED_EXACT_SERVER_MAIN`/4 e estoque -4/inventário +4. Repita com item nomeado/encantado para verificar identidade/NBT preservados; altere o stack após inspect e confirme recusa por identidade obsoleta. Source vazio só cria observador: **não fabrica nem copia máquina**. Salve/feche normalmente e reabra manualmente; confira estoque/tecnologia/adapters. Assimile de novo material compatível para o reparo da etapa 4. Detalhes/paginação: [ASSIMILACAO.md](ASSIMILACAO.md).

## 3. Fábrica persistente sem modelo

Siga **[AUTONOMIA.md → Exemplo literal sem modelo: forno vanilla real](AUTONOMIA.md)**: baús únicos em x=0/2/3, forno em x=1, todos y=64,z=0; minério bruto na entrada e carvão no combustível. Copie de lá as duas linhas completas existentes: `execute as @p run nanites adapter define default smelt {...}` e `execute as @p run nanites factory create fundicao {...}`. Os JSON completos já estão no guia; não são placeholders executáveis aqui. Esse exemplo usa faces/índices reais do forno, predicados live e monitor de saída.

```text
/nanites factory list
/nanites factory status fundicao
/nanites objective status fundicao
/nanites machine_inspect {"target":{"type":"block","dimension":"minecraft:overworld","x":3,"y":64,"z":0,"side":"none"},"offset":0,"limit":1}
/nanites objective pause fundicao 1
/nanites objective resume fundicao 2
/nanites objective stop fundicao 3
```

**1/2/3 só valem se forem as versões atuais**; consulte status e substitua após qualquer controle/update. `factory`/`objective` são aliases do mesmo registro. Confirme minério e carvão consumidos pelo forno, lingotes físicos, `cycles`, `measured`, `history` e `checkpoint`; `measured:true` só certifica o limiar configurado. Produção demora ticks reais.

Antes de `stop`, teste falta de combustível, saída cheia, relog e restart normal: objetivos/cursor/histórico devem persistir, pause deve permanecer, dono/chunks ausentes devem aguardar sem force-load. Reponha recursos e confirme retomada sem replay de passos confirmados. Não provoque crash em mundo valioso; uma intenção ambígua exige inspeção e update versionado, `resume` sozinho não resolve. `mode:language`/IA real continua teste opcional **não verificado**; não concorra `gerente` e `fundicao` sobre os mesmos blocos.

## 4. Armadura viva, perfil e reparo real

Equipe peças modulares (ex.: `/give @s nanites:modular_chestplate 1`), desgaste moderadamente o peitoral em Survival e reserve saldo e lingotes assimilados. O peitoral é necessário à adaptação; use `material_stock` para obter `HASH_REAL_DO_ESTOQUE` (64 hex). Para instalar impacto, obtenha `/give @s nanites:module_impact 1` e segure o módulo correspondente na mão.

```text
/nanites armor list
/nanites armor install impact
/nanites armor status
/nanites armor_status {}
/nanites machine_schema armor_configure
/nanites material_stock {"offset":0,"limit":4}
/nanites armor repair {"slot":"chest","key":"HASH_REAL_DO_ESTOQUE","preview":true}
/nanites armor_repair {"slot":"chest","key":"HASH_REAL_DO_ESTOQUE","preview":false}
/nanites armor configure {"version":2,"enabled":true,"adaptive":true,"autoRepair":true,"repairKey":"HASH_REAL_DO_ESTOQUE","repairSlot":"chest","intervalTicks":100}
/nanites armor reload
```

Configure com versão maior que a atual. Preview não altera equipamento/estoque/saldo; reparo consome uma unidade compatível e **100.000 nanites por ponto**, até um quarto da durabilidade máxima por ingrediente. Teste também `head|legs|feet|mainhand|offhand` com equipamento danificado/material aceito por `Item.isValidRepairItem`; incompatível/sem saldo/sem dano não deve cobrar. Confira nome, encantamento e `NanitesModules` preservados.

Repita impactos/quedas/projéteis moderados e observe exposição no peitoral, redução e gasto compartilhado com módulos/runtime: **1.000.000 nanites/HP efetivamente bloqueado**, teto 8 HP/20 ticks; decay a cada 200 ticks vestido. Compare `adaptive:false` (nova versão), sem peitoral e jogador sem saldo. Para fogo, thermal e isolamento do contrato histórico `absorb` são necessários: siga [ARMADURA_VIVA.md](ARMADURA_VIVA.md) e [FORGE_MODULAR_EXTREMIS.md](FORGE_MODULAR_EXTREMIS.md), sem ensaios letais.

Desligue manutenção com `autoRepair:false` em configuração de versão maior. Perfil em `<mundo>/nanites/living-armor/<UUID>.json`: teste reload válido/inválido, relog/restart normal e last-known-good. `enabled:false` não remove atributos vanilla nem impede reparo manual explícito. Se aparecer `pendingRecovery`/`.repair-pending`, pare, inspecione equipamento/estoque/saldo e só rearme com versão maior; reload sozinho não limpa ambiguidade.

## Limitações de aceitação

Capabilities públicas/adapters não são compatibilidade universal; processamento privado, FE de armadura, receitas proprietárias e dados não serializáveis não são implementados por inferência. Transferência de fluido não garante tanque físico escolhido além do permitido por `IFluidHandler`. Saves de jogador, chunks, saldo e SavedData não formam ACID; marcadores/checkpoints impedem replay cego, não garantem rollback/flush conjunto contra crash. Eventos de mods posteriores podem alterar dano. Fakes offline e fixture Forge não certificam rendering, EventBus, IA real ou seu modpack. Anote esses testes como **pendentes**, não aprovados por causa do build.
