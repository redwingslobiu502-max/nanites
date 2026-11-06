# JevMod 0.3.5 — QoL / Imagination

Minecraft Java 1.21, NeoForge 21.0.167, Java 21. Mantém a interface dentro do Minecraft, o Berço temporizado e OpenRouter com `~typesafe/jev-latest`. Receitas antigas e addons com os construtores anteriores continuam aceitos. Faça backup dos mundos antes de trocar o JAR do perfil offline; remova a versão anterior da pasta mods para evitar dois JevMods.

## 1. A Mente opera o Berço

JSON, JS e Java chamam `jev:core` com `berco.begin`, `berco.cancel`, `berco.release` e `berco.status`. Coordenadas são absolutas, até 64 blocos da Mente em chunks carregados. Ambas as metades do Berço resolvem o mesmo trabalho.

```json
{"service":"jev:core","op":"berco.begin","args":{"x":0,"y":-59,"z":0,"spec":{"kind":"player","model":"classic","duration_ticks":200,"resistances":["minecraft:lava"],"metadata":{"team":"fire"}}}}
```

```javascript
var job = ctx.call("jev:core", "berco.status", {x:0,y:-59,z:0});
if (job.ready) {
    var result = ctx.call("jev:core", "berco.release", {x:0,y:-59,z:0,bind:true});
    if (result.released) ctx.memory.set("created_body", result.body_id);
}
```

Status retorna `running`, `ready`, `progress` (0..1), `elapsed` (ticks), `spec` e `error`. Release também retorna `released` e, em caso de sucesso, `body_id`; `bind:true` conecta explicitamente o novo corpo à Mente. Sem espaço ou sem trabalho pronto, não cria corpo e não duplica o resultado. Begin recusa trabalhos em andamento/prontos; cancele ou libere antes. A geração continua de baixo para cima nos ticks configurados.

## 2. Consultar o corpo

`ctx.body.ref()` retorna o mesmo `EntityRef` usado pela API de poderes, ou `null` se o corpo estiver ausente/descarregado. Leia `.pos()`, `.health()`, `.maxHealth()`, `.isAlive()`, `.velocity()` e `.hasEffect("minecraft:poison")`; as operações de EntityRef também estão disponíveis. Atalhos: `ctx.body.pos()` (Vec ou null), `.health()` (0 se ausente), `.isAlive()` (false se ausente). `ctx.call("jev:core","body.info",{})` retorna snapshot JSON com id, posição, vida, vida máxima e receita, ou null.

```javascript
var body = ctx.body.ref();
if (body && body.health() < body.maxHealth() * 0.25) ctx.body.halt();
```

## 3. Dano e morte

`ctx.body.lastDamage()` e `ctx.call("jev:core","body.last_damage",{})` retornam o último golpe aceito ou null. Campos: `type` (message ID, por exemplo `onFire`), `damage_type` (ID do registro, por exemplo `minecraft:on_fire`), `attacker_type` (`environment` sem atacante), `attacker_id` (UUID ou null), `amount` (valor após resistência da receita, antes de armadura/absorção), `requested_amount`, `tick`, x/y/z e `lethal`.

Corpo e Mente vinculada salvam o registro no mundo; a Mente mantém a causa após o corpo morrer e desaparecer. Trocar o vínculo limpa o histórico do corpo anterior. Golpes recusados não substituem o último golpe. Mentes carregadas, inclusive desligadas, recebem o histórico; não há rastreamento de Mentes em chunks descarregados. Programas recebem eventos enfileirados `body_damage` e `body_death` (no golpe fatal), com os mesmos campos e `body_id`.

```javascript
function onEvent(ctx, event, data, config) {
    if (event === "body_death") ctx.memory.set("next_resistance", data.damage_type);
}
```

## 4. Resistências e metadados

Receitas aceitam `resistances: []` e `metadata: {}`. Cada resistência reduz o dano correspondente em **90%** (multiplicador 0.1), usando message ID ou ID completo do registro; não concede imunidade total. Até 64 IDs. Tipos de dano de outros mods também funcionam. A aparência de blaze, golem etc. continua sem copiar automaticamente stats/AI/resistências do mob.

Metadados são JSON definido pelo programa, até 8 KB, preservado na receita, no trabalho do Berço, no arquivo gerado e na entidade. `team`, `color` ou `generation` não mudam o comportamento automaticamente: seus sistemas interpretam esses valores. Na API Java, o construtor antigo de cinco argumentos continua disponível, e `metadata()` retorna uma cópia.

O exemplo `body_factory` vem **desativado**. Edite `systems/body_factory.json` para `enabled:true`, conecte a porta `berco` ao Berço e inclua `body_factory` nos sistemas do Núcleo. Ele monta/libera/vincula um corpo quando não há corpo vivo; após morte, acrescenta resistência ao tipo do último golpe e registra gerações. É um programa JS editável de demonstração, sem chamada à IA, custos materiais ou limite de gerações embutidos. Programe os custos e condições que quiser antes de usá-lo no seu mundo.

## 5. Radar com centro escolhido

`ctx.entities.scan(x,y,z,radius,filter)` pesquisa entidades vivas **já carregadas** em uma esfera ao redor das coordenadas. Raio 1..512, até 256 resultados ordenados do mais próximo ao mais distante. Filtros: `all`, `hostile`, `player`, `passive` (não hostis/não jogadores). Cada resultado contém id UUID, type, x/y/z, health, distance e hostile. O scan não carrega chunks; não vê entidades em chunks descarregados e não expande o alcance de projéteis/portas. `nearby` permanece compatível.

```javascript
var p = ctx.body.pos();
var targets = ctx.entities.scan(p ? p.x : 0, 64, p ? p.z : 0, 256, "hostile");
ctx.memory.set("contacts", targets.length);
```

JSON usa `entities.scan` com args x/y/z/radius/filter e `save_as` para guardar o retorno.

## 6–8. Arquivos e sistemas

- **Excluir:** botão no editor com confirmação. A versão salva vai para `.history/` e um marcador `.deleted` impede reinstalar o exemplo no próximo início. Rascunhos não salvos desse arquivo são descartados somente após confirmação. Para restaurar, copie a versão do histórico de volta pelo importador ou salve o mesmo caminho; salvar remove o marcador. Apagar manualmente um exemplo fora do editor mantém o comportamento antigo de reinstalar arquivos ausentes.
- **Desativar:** `"enabled":false` no JSON de um sistema impede carregar script/módulo e executar todos os hooks. O padrão é true. Salve/aplique ou `/jev reload` para encerrar uma instância existente; `onStop` faz a limpeza. Memória e arquivos permanecem. A flag é da definição: afeta todas as Mentes que usam esse sistema.
- **Arrastar JSON/JS:** solte no editor. A tela mostra uma prévia e a pasta escolhida para cada arquivo. Inferência por `engine/rules/module` → systems; `systems` → minds; `powers/sensors` → brains; `kind:player/mob/obj` → bodies; poderes por kind/params/cooldown. `content_type` pode explicitar o tipo no JSON. JS aceita `// jevmod: systems`, `apis`, `powers` ou `sensors`, a pasta original, ou o JSON associado no mesmo lote. Arquivos ambíguos pedem escolha na tela; confira todos com Anterior/Próximo. Arquivos existentes e rascunhos pedem confirmação de substituição, mantendo histórico dos arquivos salvos. Um lote com erro de validação não grava nenhum arquivo. PNG/OBJ continuam no importador do Berço; importe os dois grupos separadamente.

Exemplo de biblioteca arrastável:

```javascript
// jevmod: apis
var tactical = { lowHealth: function(body) { return body && body.health() < body.maxHealth()/4; } };
```

Para código Java e engines adicionais, continue usando o addon de extensão explicado em UPDATE3.md. Java compila fora do Minecraft; JSON/JS podem ser criados, importados, editados e excluídos no jogo.
