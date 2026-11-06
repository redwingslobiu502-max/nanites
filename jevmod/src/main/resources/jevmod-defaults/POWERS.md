# Guia dos poderes — JevMod 0.1.0

Este guia descreve a implementação atual para Minecraft Java 1.21 e NeoForge 21.0.167. O build e os testes automatizados passaram; o comportamento visual e a jogabilidade ainda precisam de verificação dentro do Minecraft.

## Onde ficam os arquivos

No perfil do jogo, depois de abrir um mundo com o mod:

```text
config/jevmod/
  POWERS.md              este guia
  jevmod.json            provedor de IA e opções globais
  brains/jarvis.json     cérebro: regras, sensores e lista de poderes
  brains/sentry.json
  powers/laser.json      definição e parâmetros
  powers/laser.js        comportamento
  powers/shield.json
  powers/shield.js
  powers/dash.json
  powers/dash.js
  powers/lightning.json
  powers/lightning.js
  sensors/threat_scan.js
```

No projeto, os modelos originais ficam em `src/main/resources/jevmod-defaults/`. O mod copia os arquivos ausentes ao iniciar o servidor/mundo; preserva os existentes. Um exemplo apagado volta no próximo início. `/jev reload` relê os arquivos sem reinstalar os exemplos, recria os cérebros anexados e interrompe os poderes ativos. Valores alterados só por comando voltam aos defaults na recarga.

## Usar os poderes prontos

Ative comandos no mundo de teste. A cada momento o jogador tem um cérebro anexado. Jarvis possui os quatro poderes; Sentry possui apenas escudo e raio.

```text
/jev list
/jev attach jarvis
/jev on laser
/jev param laser color purple
/jev param laser damage 5
/jev off laser
/jev on shield
/jev off shield
/jev on dash
/jev on lightning
/jev status
/jev detach
```

`on` liga um poder contínuo ou dispara um instantâneo. Um instantâneo precisa de outro comando/decisão para disparar de novo e respeita o cooldown. `off` não desfaz ações já executadas, como deslocamento ou dano. Comandos manuais funcionam sem chave de IA; se o cérebro tiver um provedor funcionando, decisões posteriores da IA podem mudar o estado e os parâmetros novamente.

Um segundo corresponde a 20 ticks quando o jogo roda normalmente.

| Poder | Comportamento atual | Duração/cooldown |
|---|---|---|
| `laser` | Feixe de partículas acompanha a mira; tenta causar dano a um alvo hostil a cada 10 ticks. Blocos interrompem o raycast; o feixe não quebra blocos. | Toggle, até 200 ticks (10 s) por ativação. |
| `shield` | Casca de partículas e efeito vanilla de resistência; o raio é visual, não uma barreira de colisão. | Toggle, até 400 ticks (20 s); resistência residual de até 0,6 s. |
| `dash` | Adiciona velocidade na direção do olhar, inclusive verticalmente; mantém colisões normais. | Instantâneo, cooldown de 40 ticks (2 s). |
| `lightning` | Arco, anel e raio vanilla visual; dano explícito somente ao alvo hostil atingido pela mira. | Instantâneo, cooldown de 60 ticks (3 s). |

Os limites de duração dos toggles valem por ativação. A IA pode reativá-los numa decisão posterior. As regras de alvo hostil são implementadas nos scripts de exemplo; scripts próprios precisam definir seus próprios filtros.

## Parâmetros dos exemplos

| Poder | Parâmetro | Valores aceitos | Default |
|---|---|---|---|
| laser | `color` | red, blue, cyan, purple, yellow | red |
| laser | `range` | 4–48 blocos | 24 |
| laser | `damage` | 1–8 pontos de vida por tentativa de dano | 3 |
| laser | `width` | 0.05–0.4, controla tamanho das partículas | 0.15 |
| shield | `color` | cyan, blue, purple, yellow | cyan |
| shield | `radius` | 1–2.5 blocos, tamanho visual | 1.6 |
| shield | `strength` | 0–2, inteiro, amplificador vanilla de resistência | 0 |
| dash | `speed` | 0.5–2, multiplicador do impulso | 1 |
| dash | `color` | cyan, blue, yellow | cyan |
| lightning | `damage` | 2–12 pontos de vida | 6 |
| lightning | `range` | 4–48 blocos | 24 |
| lightning | `color` | blue, cyan, purple, yellow | blue |

Dano e cura usam pontos de vida: 2 pontos equivalem a 1 coração. Dano efetivamente recebido também depende das regras vanilla. Amplificadores de efeitos começam em zero: `strength: 0` aplica Resistência I. Valores numéricos dos parâmetros são limitados ao intervalo declarado; opções de texto inválidas são rejeitadas. Use ponto decimal nos comandos.

## Como a IA escolhe

Com chave de API e provedor configurados em `jevmod.json`, o cérebro envia sensores e perguntas tipadas. Ele escolhe entre poderes previamente definidos; não inventa nem escreve scripts novos durante o jogo.

O padrão é **OpenRouter** com **`~typesafe/jev-latest`**. Configure `OPENROUTER_API_KEY` no ambiente do processo antes de iniciar o jogo. O [guia oficial do OpenRouter](https://openrouter.ai/blog/tutorials/how-to-use-jev/) confirma esse alias e a API `POST https://openrouter.ai/api/alpha/decisions`, com perguntas noul/choice/score. O alias acompanha lançamentos e pode mudar de versão. `typesafe/jev-router` é um roteador de chat separado; use o identificador completo do Jev Latest indicado aqui.

Para trocar Gemini pelo Jev no mesmo OpenRouter, mantenha o provedor e altere o modelo em `config/jevmod/jevmod.json`, preservando as outras opções:

```json
{
  "provider": "openrouter",
  "openrouter": {
    "model": "~typesafe/jev-latest",
    "api_key_env": "OPENROUTER_API_KEY",
    "base_url": "https://openrouter.ai/api/v1"
  }
}
```

Remova ou atualize overrides `provider`/`model` nos cérebros e use `/jev reload`. Se a chave foi definida depois que o jogo abriu, reinicie o processo para herdar o ambiente. `/jev provider` deve indicar `openrouter:~typesafe/jev-latest` quando o provedor estiver inicializado. O adaptador deriva a URL Decisions a partir da base configurada; não envia esses pedidos ao endpoint de chat. Atualizar o JAR preserva configurações existentes e também cópias existentes deste guia no perfil; consulte o guia do projeto para a revisão mais nova.

```text
/jev say ativa o escudo azul
/jev think
/jev last
/jev provider
```

Jarvis pensa a cada 80 ticks; Sentry a cada 100. Por padrão, uma probabilidade de ativação ≥ 0.6 liga/dispara; ≤ 0.4 desliga toggles. Entre esses valores o estado é mantido. Parâmetros exigem confiança mínima (default do cérebro: 0.35). Uma resposta ausente preserva o estado atual. As requisições são assíncronas; não há garantia de resposta imediata nem de obediência perfeita.

`listen_chat`, `chat_prefix`, `instructions`, `think_every_ticks`, `activate_threshold`, `deactivate_threshold` e `min_confidence` ficam no JSON do cérebro. `provider` e `model` podem sobrescrever a configuração global. Sensores disponíveis: `self`, `nearby_entities`, `looking_at`, `environment`, `chat`, `powers` e `script`.

## Criar um poder: exemplo de cura

Este exemplo é uma receita para adicionar, não um quinto poder já instalado. Crie `config/jevmod/powers/heal.json`:

```json
{
  "id": "heal",
  "name": "Cura",
  "description": "Recupera vida do próprio jogador.",
  "use_when": "Quando o jogador pede cura e está ferido.",
  "kind": "instant",
  "script": "heal.js",
  "cooldown_ticks": 100,
  "params": {
    "amount": {"type": "range", "min": 2, "max": 8, "default": 4},
    "color": {"type": "choice", "options": ["green", "cyan"], "default": "green"}
  }
}
```

Crie `config/jevmod/powers/heal.js`:

```javascript
function run(ctx, params) {
    if (ctx.host.health() >= ctx.host.maxHealth()) return;
    ctx.host.heal(params.amount);
    ctx.vfx.ring({color: params.color, radius: 2, duration: 12});
}
```

Adicione `"heal"` à lista `powers` de `brains/jarvis.json`, preservando os outros IDs. Depois:

```text
/jev reload
/jev errors
/jev list
/jev on heal
/jev param heal amount 6
```

A cura não aumenta a vida máxima. O cooldown começa quando o script é disparado, mesmo se retornar sem curar. Para o cérebro descobrir automaticamente todos os poderes com `ai: true`, use `"powers": ["*"]`. Uma lista explícita, como a do Jarvis atual, precisa ser atualizada ao adicionar poderes.

## Referência da definição JSON

| Campo | Função/default |
|---|---|
| `id` | Identificador; se omitido usa nome do arquivo. Letras minúsculas, números e `_`; começa com letra, até 48 caracteres, sem `__`. |
| `name`, `description` | Nome legível e descrição enviada à IA. |
| `use_when` | Critério de uso enviado à IA; não é uma condição obrigatória executada pelo jogo. |
| `kind` | `toggle` (default) ou `instant`. |
| `script` | Caminho relativo à pasta da definição; default `<id>.js`. |
| `cooldown_ticks` | Cooldown de instantâneos; default 20. O ciclo de toggles atualmente não usa esse campo. |
| `max_active_ticks` | Limite de duração de toggle; 0 (default) significa sem limite. |
| `ai` | Default true; false exclui o poder da seleção automática por `*`. Não exclui um poder listado explicitamente no cérebro. |
| `params` | Objeto com os parâmetros disponíveis. |

Parâmetros aceitam `description`, `default`, `ai` (default true) e `min_confidence` (por padrão herda do cérebro).

| `type` | Configuração |
|---|---|
| `bool` | Valor true/false; default false. |
| `choice` | `options`: lista de 2–255 nomes ou objeto nome → descrição; default é a primeira opção. |
| `range` | `min`, `max` com max > min; `integer` opcional; `levels` de 2–10 níveis para a IA (default 5), número ou lista de descrições. |

`ai: false` em um parâmetro impede alterações pela IA; comandos ainda podem alterá-lo. Isso não torna o poder imune a decisões de ligar/desligar.

## Ciclo dos scripts

| Hook | Quando roda |
|---|---|
| `onStart(ctx, params)` | Ao ligar toggle. |
| `onTick(ctx, params)` | A cada tick ativo; `ctx.tick` começa em zero. |
| `onChange(ctx, params, changed, old)` | Quando parâmetros mudam com a instância do toggle carregada. |
| `onStop(ctx, params)` | Ao desligar toggle, recarregar ou limpar o cérebro. |
| `run(ctx, params)` | Uma vez por disparo instantâneo. |

Hooks são opcionais: um instantâneo sem `run`, por exemplo, não faz nada. Globais persistem enquanto a instância do toggle existir; um instantâneo ganha uma instância nova por disparo. `ctx` e `params` também estão disponíveis como globais. Scripts usam Rhino em modo ES6 interpretado, sem acesso geral a Java, arquivos, rede ou bibliotecas Node/browser. O orçamento de instruções interrompe loops longos. Erros desabilitam o poder até `/jev reload`; procure a mensagem e o log do jogo.

## API disponível para scripts

### Contexto e vetores

`ctx.host` é o jogador/entidade com cérebro; `ctx.world` acessa o mundo; `ctx.vfx` produz efeitos. `ctx.brain` e `ctx.power` são IDs. `ctx.tick` é o tempo ativo do toggle, ou zero num instantâneo. `ctx.stop()` pede o fim de um toggle após o tick atual. `ctx.say(texto)` manda mensagem ao jogador, `ctx.log(texto)` escreve no log, `ctx.random()` gera um número de 0 até antes de 1 e `ctx.color(valor)` converte cor.

Crie vetores com `ctx.vec(x, y, z)`. Campos: `x`, `y`, `z`. Métodos: `add(v)`/`add(x,y,z)`, `sub(v)`, `scale(n)`, `normalize()`, `length()`, `distance(v)`, `dot(v)`, `cross(v)`, `lerp(v,t)`, `rotateY(graus)`. Retornam novos vetores quando aplicável.

### Entidade/corpo: `ctx.host` e entidades encontradas

| Operações | Métodos |
|---|---|
| Identidade | `id()`, `uuid()`, `type()`, `name()` |
| Posição e orientação | `pos()`, `center()`, `eyePos()`, `look()`, `yaw()`, `pitch()`, `distanceTo(pos)` |
| Estado | `isAlive()`, `isPlayer()`, `isHostile()`, `isLiving()`, `isOnGround()`, `is(outraEntidade)` |
| Dimensões e vida, somente leitura | `height()`, `width()`, `health()`, `maxHealth()` |
| Movimento | `velocity()`, `setVelocity(v)`, `push(v)`, `teleport(pos)` |
| Vida e estados vanilla | `damage(pontos)`, `heal(pontos)`, `setFire(segundos)`, `extinguish()`, `effect(id, segundos, amplificador)` |
| Tags | `hasTag(nome)`, `addTag(nome)` |

Velocidade resultante é limitada a módulo 10. Teleporte usa coordenadas no mundo atual e zera distância de queda; não procura automaticamente um destino seguro. `effect` usa IDs vanilla, como `minecraft:speed`, `minecraft:resistance` e `minecraft:levitation`; amplificador zero é nível I. Não existe método de remoção de efeito nesta API, portanto use durações curtas para efeitos reversíveis. Chamadas `mc()` são detalhes internos Java, não uma API para scripts.

### Mundo: `ctx.world`

| Método | Comportamento |
|---|---|
| `raycast(from, dir, range, opts)` | Range 0.5–256; opts opcionais: `entities`, `fluids`, `ignoreHost`. Resultado: `type`, `pos`, `entity`, `block`, `distance`; helpers `isEntity()`, `isBlock()`, `isMiss()`. |
| `entitiesNear(pos, radius, filter)` | Raio 0.5–64, array por proximidade, exclui host; filtros `living`, `hostile`, `players`, `all`. |
| `block(pos)`, `isAir(pos)` | Consultar bloco. |
| `setBlock(pos, id)`, `breakBlock(pos, drop)` | Colocar/quebrar; exigem `allow_world_edits: true`. |
| `explode(pos, power, opts)` | Power 0–8; opts `fire`, `breakBlocks` (defaults false). Quebrar blocos exige `allow_world_edits`. |
| `lightning(pos, visualOnly)` | Criar raio; true evita os efeitos de dano do raio vanilla. |
| `spawn(type, pos)` | Criar entidade vanilla e retornar referência ou null. |
| `particles(type, pos, count, spread, speed)` | Partículas simples, count até 500. |
| `sound(id, pos, volume, pitch)` | Tocar som existente. |
| `isNight()`, `isRaining()`, `time()`, `gameTime()`, `dimension()` | Consultar ambiente; time é hora do dia em ticks, gameTime é tempo total do mundo. |

`allow_world_edits` protege as operações indicadas; não é um bloqueio global de todo efeito no mundo. Spawn, dano e raios têm suas próprias consequências. Use um mundo descartável ao desenvolver.

### Visuais: `ctx.vfx`

| Método | Opções/uso |
|---|---|
| `beam(opts)` | `id`, `to` obrigatório, `from` opcional, `anchor`, `color`, `width`, `intensity`, `duration`, `style`. Sem from parte dos olhos do host/anchor. |
| `ring(opts)` | `id`, `pos`, `color`, `radius`, `width`, `intensity`, `duration`; expande no plano horizontal. |
| `orb(opts)`, `sphere(opts)` | `id`, `pos` ou `on` (entidade), `color`, `radius`, `intensity`, `duration`, `style`; por padrão seguem o centro do host. |
| `stop(id)`, `stopAll()` | Parar um emissor ou todos os emissores deste poder. |
| `dust(pos, color, size, count, spread)` | Poeira colorida. |
| `trail(from, to, type, step)` | Linha de partículas simples. |
| `burst(pos, type, count, speed)` | Rajada de partículas simples. |

Use `style: "solid"`, `"pulse"` ou `"arc"`. `duration` dos emissores é em ticks (1–400). Atualizar o mesmo id renova o efeito; anéis sem id recebem um id baseado no tick. Nomes de cor: red, orange, yellow, gold, lime, green, cyan, blue, purple, magenta, pink, white; também aceita `#rrggbb` ou número. Um parâmetro choice continua limitado às opções do seu JSON.

Nesta versão os visuais são partículas vanilla: sphere é uma casca de pontos e orb é uma forma menor de partículas. Não são meshes, projéteis físicos, hitboxes nem barreiras. O cliente limita emissores/amostras; intensidade não representa iluminação real. STOP interrompe novas partículas; as existentes terminam sua vida normal.

## Corpo: Update 2

A Update 2 adiciona o berço, skins PNG, modelos de mobs registrados (incluindo mods) e importação de malhas OBJ, com formação temporizada de baixo para cima e corpo Jev persistente. A interface F8/`/jevui` permite editar código e visualizar o cérebro dentro do Minecraft. Veja [UPDATE2.md](UPDATE2.md) para uso e limites.

### Transformação do jogador

Para poderes que atuam no personagem, a API atual já permite criar scripts de cura/regeneração, impulso/salto, levitação, buffs de velocidade/força/resistência, teleporte e auras visuais. Apenas dash e shield já vêm instalados nessa categoria; os demais são possibilidades de novos scripts, ainda não implementados nem validados em jogo.

Para transformação corporal, falta uma camada própria: trocar modelo/skin, crescer ou encolher com hitbox sincronizada, adicionar braços/asas, animar membros, alterar câmera/alcance, modificar atributos básicos e salvar uma forma após reconexão. Uma atualização de corpo teria de integrar estado persistente, renderização, colisão e restauração ao desativar a forma. A IA poderia selecionar formas predefinidas por essa camada; o berço atual cria uma entidade separada e não transforma o jogador.

## Diagnóstico rápido

- Poder não aparece: confira o JSON, o arquivo `.js` e a lista `powers` do cérebro; execute `/jev reload`, `/jev errors` e `/jev list`.
- Script parou: veja o log e a mensagem de erro; corrija e recarregue.
- IA não responde: `/jev provider`, chave de API no processo que iniciou o jogo e `/jev last`. O último pedido/resposta fica em `config/jevmod/last_think.json` após uma resposta aplicada.
- Visual não some imediatamente: partículas já emitidas ainda expiram normalmente; efeitos de status também podem continuar pela duração restante.
