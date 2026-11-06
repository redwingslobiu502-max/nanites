# Imagination Update — Mente programável

Minecraft Java 1.21, NeoForge 21.0.167, Java 21. O corpo é a entidade física criada no berço; a **Mente** é a personalidade, memória, programas e conexões. Ela continua executando sistemas de blocos mesmo quando a bateria do corpo acaba. O comportamento vem dos arquivos e addons, não de uma lista fechada de poderes.

## Começar dentro do Minecraft

1. Obtenha `/give @s jevmod:mind_core` e coloque o Núcleo da Mente. Ele também tem receita: `IGI / RDR / IGI`, com I ferro, G vidro, R redstone e D diamante.
2. Clique com o botão direito. Escolha `jarvis`, `friday` (Sexta-feira) ou `ultron`, edite os IDs dos sistemas e conecte um corpo Jev pelo UUID, se quiser. A Mente não precisa de corpo para automatizar a casa.
3. Abra **Código / APIs** ou F8 para editar `minds/`, `systems/` e `apis/` dentro do jogo. **Salvar e aplicar** ou `/jev reload` recarrega os scripts; memória, corpo e portas continuam no bloco. Versões anteriores dos arquivos ficam em `.history/`.
4. Conecte blocos por portas nomeadas: nome e coordenadas na tela. Use `reactor` para combustível e `ammo` para a defesa dos exemplos. Portas alcançam 64 blocos e trabalham com chunks carregados; wireless alcança até 128.

Também há comandos administrativos (substitua `X Y Z` pela posição do Núcleo):

```text
/jev mind list
/jev mind at X Y Z profile jarvis
/jev mind at X Y Z systems reactor,energy,assistant
/jev mind at X Y Z body @e[type=jevmod:body,sort=nearest,limit=1]
/jev mind at X Y Z port reactor A B C
/jev mind at X Y Z port ammo A B C
/jev mind at X Y Z status
/jev mind at X Y Z say pare o corpo
/jev mind at X Y Z off
/jev mind at X Y Z on
```

`systems none` desliga todos os módulos dessa Mente. Trocar personalidade carrega a composição padrão do perfil. Definir sistemas depois substitui essa composição. As personalidades são JSON editável, não permissões fixas: você pode criar outra Mente ou mudar completamente o comportamento do Ultron.

## Arquitetura para escrever seus sistemas

- **JSON:** compõe personalidades, parâmetros, engines e regras declarativas. `engine: "rules"` executa condições e ações sem JavaScript.
- **JavaScript:** `engine: "javascript"` executa hooks no servidor pelo Rhino. Bibliotecas em `apis/` são carregadas no mesmo escopo do sistema: crie funções, objetos e suas próprias APIs. Não precisa recompilar o mod.
- **Java:** addons NeoForge registram `MindModule`, serviços ou engines de outras linguagens em `MindExtensions`. Têm acesso nativo a `ServerLevel`, blocos, entidades, capabilities e APIs de outros mods. Compilam em um JAR separado e entram na pasta `mods` do perfil offline, como qualquer addon. Java não é compilado dentro do Minecraft; a maioria dos perfis inclui somente um runtime Java.

O exemplo independente em `extension-example/` demonstra um módulo e uma API Java que o JevMod não conhece antecipadamente. `dev.cmd extensionExampleJar` gera `imagination-example-0.1.0.jar`; com ele instalado, selecione `java_counter` (módulo Java), `java_api_client` (JS chama a API Java) ou `java_api_rules` (JSON chama a API Java e guarda o resultado). O mesmo projeto serve de ponto de partida para criar um sistema de energia, física, máquinas, armazenamento, redes ou um interpretador de outra linguagem. O JAR principal não incorpora esse addon.

Isso torna o limite extensível: JSON/JS usam a API padrão; um addon Java pode acrescentar operações de jogo e expô-las a JSON/JS. Não existe promessa de que um script sozinho consiga usar uma operação ainda não implementada na API — nesses casos, escreva a extensão Java.

### Sistema JavaScript mínimo

`systems/my_system.json`:

```json
{"id":"my_system","engine":"javascript","script":"my_system.js","apis":["my_api"],"interval_ticks":20,"config":{"channel":"home"}}
```

`apis/my_api.js`:

```javascript
var myApi = {
    announce: function(ctx, text) { ctx.say("Meu sistema: " + text); }
};
```

`systems/my_system.js`:

```javascript
function onStart(ctx, config) { ctx.wireless.listen(config.channel); }
function onTick(ctx, config) {
    ctx.memory.set("ticks", Number(ctx.memory.get("ticks", 0)) + 20);
}
function onEvent(ctx, event, data, config) {
    if (event === "wireless" && data.channel === config.channel)
        myApi.announce(ctx, data.message);
}
function onStop(ctx, config) { ctx.redstone.output("north", 0); }
```

Inclua `my_system` na composição do Núcleo. Eventos padrão são `redstone` (`power`, `previous`), `wireless` (`channel`, `sender` e os dados enviados), `chat` (`text`) e `decision` (`system`, `answers` ou `error`). `ctx.emit` acrescenta seus próprios eventos. Mensagens são enfileiradas; handlers não chamam recursivamente handlers de outros núcleos.

### JSON sem código

O exemplo `redstone_rules.json` lê entrada real de redstone e produz saída norte. Cada regra tem `when` e `actions`. Condições: `event` (padrão `tick`), `every_ticks`, `channel`, `redstone_min/max` e `state: {key, equals}` ou `{key, lte}`. Ações chamam um serviço: `service` (padrão `jev:core`), `op`, `args`; `save_as` guarda o retorno na memória desse sistema.

Com o addon de exemplo instalado, esta ação chama uma API Java definida pelo jogador e grava `answer: 42`:

```json
{"service":"imagination_example:math","op":"double","args":{"value":21},"save_as":"answer"}
```

Em JavaScript: `ctx.memory.set("answer", ctx.call("imagination_example:math", "double", {value:21}));`.

Operações padrão: `memory.set`, `body.halt`, `body.resume`, `body.move`, `redstone.output`, `wireless.send`, `block.set`, `block.state`, `inventory.consume`, `say`, `emit`. Addons registram outras operações com qualquer formato de argumentos JSON.

### API padrão `ctx`

| Área | Operações |
|---|---|
| Memória por sistema | `ctx.memory.get(key, fallback)`, `set(key, value)`, `remove(key)`, `snapshot()` |
| Memória compartilhada | `readSystem(id, key, fallback)`, `writeSystem(id, key, value)` |
| Blocos | `ctx.blocks.bind(name, x, y, z)`, `port(name)`, `relative(dx,dy,dz)`, `connected(name)`, `connections()` |
| Porta de bloco | `.block()`, `.state()`, `.signal()`, `.setBlock(id)`, `.setState(property,value)` |
| Inventário real | porta `.count(itemId)`, `.consume(itemId,count)`, `.insert(itemId,count)` |
| Energia FE de mods | porta `.energy()`, `.extractEnergy(amount)`, `.receiveEnergy(amount)` |
| Redstone | `ctx.redstone.input()`, `read(side)`, `output(side,0..15)` |
| Wireless | `ctx.wireless.listen(channel)`, `send(channel,data,range)` |
| Corpo | `ctx.body.exists()`, `id()`, `bind(uuid)`, `halt()`, `resume()`, `halted()`, `move(vx,vy,vz)` |
| Entidades | `ctx.entities.nearby(radius,hostilesOnly)` retorna objetos com id/type/x/y/z/health |
| Projéteis | `ctx.projectiles.fire(ammoPort,targetUUID,itemId,speed,damage)` consome um item e dispara |
| IA | `ctx.ai.decide(state,questions)`, `pending()`; resposta assíncrona pelo evento `decision` |
| Extensões/eventos | `ctx.call(namespaceService,operation,args)`, `ctx.emit(event,data)` |
| Utilidades | `ctx.tick()`, `ctx.persona()`, `ctx.config()`, `ctx.system`, `ctx.say(text)`, `ctx.log(text)` |

Lados: `north`, `south`, `east`, `west`, `up`, `down`. Inventários e FE usam capabilities NeoForge; máquinas de outros mods precisam fornecê-las. `.insert` cria itens por pedido explícito do programa (API de construção); para sistemas de produção, consuma os insumos correspondentes no seu código. `fire` usa munição real, tem colisão com blocos/entidades e não devolve o item após o impacto.

## Os três exemplos pedidos

**Energia que para o corpo:** conecte o UUID e selecione `energy`. O script reduz a carga por tick e, ao chegar a zero, suspende movimento e poderes do corpo. A Mente permanece ativa. Inicial, capacidade e consumo ficam em `energy.json`, e a carga atual em `memory.energy.charge`.

**Energia com reator:** selecione `reactor,energy`; conecte a porta `reactor` a um baú com carvão. O módulo `reactor.js` transforma combustível real em carga usando a API programada `engineering.charge`, sem alterar a classe do corpo. O exemplo gera 40 unidades/carvão a cada tentativa de 20 ticks, respeitando capacidade e limiar de reposição. Você pode trocar o combustível, fórmula, inventário ou usar `.extractEnergy` de uma máquina FE.

**Defesa da casa:** selecione `home_defense`, conecte `ammo` a um baú com pepitas de ferro e coloque um hostil no alcance. O programa escolhe alvos, consome pepitas e dispara entidades de projétil que causam dano no impacto. Configure raio, velocidade, dano, munição, redstone e filtro de hostis em JSON. Sem munição ou com parede bloqueando o tiro, não dispara.

**Wireless programado:** `wireless_switch` demonstra publicação e assinatura de um canal. Edite o script para definir quais sinais serão enviados e quais mensagens virarão saída de redstone, abertura de porta, carregamento de bateria ou outro comportamento. Uma conexão wireless não carrega chunks nem atravessa dimensões.

## JARVIS / Sexta-feira / Ultron e OpenRouter

O módulo `assistant` só solicita inferência ao receber `chat`, pelo comando `say` ou por `@jarvis pare o corpo`, `@friday ...`, `@ultron ...` no chat. A mensagem com @ chega ao núcleo ligado mais próximo, pertencente ao jogador, até 64 blocos na mesma dimensão. Usa o provedor configurado no JevMod, mantendo OpenRouter com `~typesafe/jev-latest`, e inclui as instruções da personalidade no estado. O exemplo pede uma decisão tipada `keep/halt/resume` para o corpo e aplica a política do programa; o pedido de parar persiste mesmo com bateria disponível, e retomar não ignora bateria vazia. Não inventa código nem escreve arquivos automaticamente; você ou seu agente escreve as APIs e sistemas. Configure `OPENROUTER_API_KEY` antes de lançar o jogo para usar inferência. Sistemas de energia, reator, defesa, regras e wireless funcionam sem chave e sem internet.

Você pode alterar as perguntas, tipos `noul/choice/score`, estado, limiares e handlers para que a IA opere seus próprios sistemas. O resultado não ganha privilégios extras: só executa ações que seu programa implementa.

## Persistência e execução

Memória JSON, personalidade, seleção de sistemas, portas, identidade e vínculo com o corpo são salvos no mundo. O núcleo precisa estar carregado para executar. Desligar/remover o núcleo encerra os sistemas e zera saídas; o exemplo de energia mantém o corpo parado até reativação. Recarregar arquivos reinicia os hooks com a memória preservada. Um sistema que falha é desligado e seu erro aparece no Núcleo; os outros continuam.

Limites do ambiente JS padrão: orçamento de instruções do JevMod, 256 operações de mundo por hook, memória de 64 KB/núcleo, eventos até 8 KB, fila de 64 eventos, até 32 sistemas e 16 bibliotecas por sistema. São limites de execução da API padrão, não uma lista de ideias permitidas. Java addons são código de mod normal, com acesso nativo e responsabilidade por seu próprio comportamento. Use um perfil offline e mundos de teste com backup para desenvolver.
