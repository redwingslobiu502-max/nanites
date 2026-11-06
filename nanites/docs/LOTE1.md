# Lote 1 — contratos implementados para Forge 1.20.1 / Fabric 1.21.1

## Escopo real

Core Java 17, Gson 2.10.1 local. Sem dependências Minecraft. Linguagem, execução, preview/hot-swap/migração, snapshots, cliente chat assíncrono, loop multi-turn, ferramentas e cache são implementações, não placeholders. `IntegrationContracts.ComponentAdapter` continua contrato **sem absorção de mundo**. VFX/energia/receitas/registries/networking/editor são próximos lotes. Não copiar jevmod.

## Nanite Script 1

Fonte textual -> lexer/parser -> records selados de AST com `Site(file,line)` -> `Module.program()` -> `RuleEngine.ScriptAction`. A AST é a IR auditável; não é bytecode Java. `file` é rótulo de diagnóstico, nunca caminho para abrir arquivo.

Ordem: `program <id> version <schema>;`, zero ou mais `state <id> = <inteiro não negativo>;`, funções `fn nome(arg,...) = expressão;`, handlers `on <evento> group <grupo> { ... }`. Comentários `#` até fim da linha. IDs ASCII, case-sensitive; `.`/`:` permitidos. Sem strings, imports, reflection, objetos Java, callbacks de usuário, filesystem, processos ou rede. Programas atuais são dados, não plugins carregados.

- Expressões `long` checked: `+ - * / %`, comparações `> < >= <= == !=`, `&& ||` com curto-circuito, parênteses e sinal negativo. Booleano = 0/falso ou não-zero/verdadeiro; comparação retorna 0/1. Overflow/divisão por zero falham, não saturam.
- `state.campo`, `event.campo`, parâmetros de função. Campo de evento ausente é erro com origem, não zero silencioso. Adapter fornece unidades documentadas e valores derivados do servidor.
- `add campo = expressão;` soma delta ao estado não negativo. Expressões veem alterações anteriores no mesmo handler/evento.
- `transfer origem destino = quantidade;` cria movimento agregado conservativo; sem mint, entidade por nanite ou energia fictícia.
- `emit capacidade alvo = valor;` cria intent numérico validado por allowlist de `WorldSpec`; **não executa o mundo**.
- `if (expressão) { ... } else { ... }`; `repeat (expressão) { ... }`, contagem 0..256. Não há `while`. Funções podem chamar outras, incluindo recursão, mas profundidade/fuel sempre limitam.

Limites: fonte 65.536 caracteres, tokens 16.384, AST 4.096 nós, profundidade 32, 64 funções com até 16 parâmetros, 64 handlers/64 campos. Por tick: fuel global 16.384 passos de expressão/statement, até 256 eventos e 4.096 ações (configuráveis para menos). Repetição vazia/nested/recursiva também é limitada; alocação do output tem teto. Não há benchmark de ms/tick; budgets não substituem limitar tamanho das entradas antes de construir objetos Java.

`examples/fall.nano` detecta queda e solicita proteção/reconfiguração isolante numa estrutura arbitrária. `ProgrammingDemo` executa esse recurso empacotado. `protect` e `reconfigure.insulating` são IDs de fixture: não integrações existentes nem nomes especiais de suits. A propriedade isolante/condutor e sua evidência continuam validadas por `CapabilityCatalog`; adapter só registra uma capacidade de mundo após comprovar suporte e verificar requisitos do material. A linguagem não promove evidência.

## API do adapter — autoridade e execução

Construir **uma** `NaniteSession` privada por dono/domínio de servidor. `Policy(groups,events,world,limits)` vem do adapter confiável, não de pacotes/IA. `Reservoir` aceita definições custom e capacidade long; caixa = `Ledger.NANITES_PER_BOX` = 500 bilhões. Session não expõe ledger mutável.

```java
var policy = new NaniteSession.Policy(
    Set.of("builders"), Set.of("fall"),
    Map.of("protect", new RuleEngine.WorldSpec(Set.of("structure"), 0, 100)),
    new RuleEngine.Limits(8, 128));
// source deve usar apenas capacidades incluídas em policy.
var session = new NaniteSession(ownerFromServer, policy, reservoirs, balances,
                               "player.nano", source);
long revision = session.revision();
session.enqueue(ownerFromServer, revision,
    new RuleEngine.Event("fall", "builders", Map.of("distance", 8L, "speed", -900L)));
var tick = session.tick(ownerFromServer, session.revision());
// Apenas tick.result().committed() autoriza consumir tick.intents().
// Adapter ainda revalida dono, alvo, dimensão, distância, recursos e revisão DO MUNDO.
```

`ownerFromServer` deve ser UUID/ID autenticado derivado da conexão/entidade real; não aceitar `owner` fornecido no pacote. `enqueue` é ponte de evento **do servidor**, não botão para clientes inventarem queda/velocidade. O adapter deve impor policy/rate limit e uma sessão por domínio; esta biblioteca não autentica conexões nem cria ACL multiusuário. IDs de alvo são escopados ao dono e resolvidos pelo servidor, jamais paths ou URLs.

Operações mutáveis exigem `actor + expectedRevision`; dono alheio dá `SecurityException`, revisão velha dá `ConcurrentModificationException`. `enqueue`, `stage`, `tick`, `discardEvents` e rollback elevam revisão checked. Falha de tick conserva fila/estado/ledger/programa, descarta candidato e eleva revisão; o dono pode corrigir script ou descartar eventos explicitamente. `processed` conta eventos comprometidos, não ticks externos. Mapas/listas de leitura são imutáveis.

### Preview, hot-swap, migração, rollback

- `preview(actor, revision, file, source, migration, events)` constrói engine/ledger isolados; não altera sessão/revisão, não aplica intents. Compilação/preview devem ser feitos num worker; são limitados, mas ainda usam CPU/locks breves.
- `stage(actor, revision, file, source, migration)` valida AST/eventos/grupos/reservatórios/capacidades/estado; um único candidato pendente. Ativação somente no próximo `tick` bem-sucedido.
- `Migration(fromVersion,toVersion,renames,drops)` explicita cada perda/renomeação; colisões são negadas. Versão+chaves iguais preservam estado, defaults não resetam.
- Falha pré-commit reverte integralmente o lote **interno**. `rollbackScript(actor,revision,migration)` re-stage do último script em memória usando estado atual, preservando transferências já comprometidas. Migração inversa exige decisão explícita quando schema mudou. Histórico anterior não é persistido.
- `RuleEngine` standalone preserva construtor/IR originais; novo construtor aceita `Map<String,WorldSpec>`. `drainWorldActions()` entrega outbox comprometido; outro tick não sobrescreve outbox não drenado. `NaniteSession.tick` drena e devolve intents no resultado.

**Não há transação universal com Minecraft/outros mods.** Intents deste lote não são duráveis e não têm confirmação externa/idempotência. Não usar efeitos irreversíveis até o próximo adapter implementar prepare/revisão/commit e deduplicação próprios. Crash após executar um intent e antes de salvar pode exigir recuperação específica; não reaplicar automaticamente. Reverter script não desfaz física, blocos, inventários, energia ou ticks passados.

## Persistência de núcleo

`NaniteSession.save(actor)` captura formato 1: dono, revisão, rótulo/fonte/schema, definições/capacidades de reservatórios, saldos, total, estado, fila de eventos e contador processed. Não salva candidato pendente: exige fronteira de ativação resolvida. `SnapshotStore(root).write(slot, save)` e `.read(slot)`; `NaniteSession.restore(save,authenticatedOwner,policy)` reconstrói engine e ledger, revalida script e policy atuais.

Limites: snapshot 1 MiB, 256 reservatórios/eventos, 64 state/event fields; integers exatos, não double; totais checked/capacidade/conservação, schema e owner. Gson `JsonReader` estrito, chaves duplicadas negadas, profundidade 32/nós 16.384; nenhum `fromJson` refletivo construindo estado sem validação. Versão desconhecida falha: não existe migração automática entre formatos de disco.

Escrita: temporário no mesmo diretório -> `FileChannel.force(true)` -> `ATOMIC_MOVE + REPLACE_EXISTING`. Sem fallback não atômico; leitura nunca escolhe temporário. Snapshot antigo ou outro dono no mesmo slot são negados. Slots só `[a-zA-Z0-9_-]`; root canônico, componentes symlink/traversal negados. Falha de escrita não exige apagar save válido.

**Usar um único serviço/writer por root**; locks são por instância, sem coordenação multiprocesso. Capture Save e grave num worker serializado fora do tick. Não é journal global, assinatura/HMAC, transação de inventário ou garantia contra administrador local editando/substituindo arquivos. Root/cache/work devem ser diretórios privados, não graváveis por clientes/mods hostis; verificações de path não são sandbox contra um atacante do SO fazendo TOCTOU/hardlinks. Power-loss/fsync de diretório depende do SO/FS; nenhuma garantia universal de durabilidade é anunciada. Snapshot reaberto preserva o último estado publicado, não ações externas posteriores.

## Cliente local e agente

```java
var config = new LocalChatClient.Config(IntegrationContracts.LOCAL_AI,
    modelChosenByOwner, Duration.ofSeconds(15), 2048, 65536, 1);
var client = new LocalChatClient(config, new LocalChatClient.JvmTransport());
var agent = new MultiTurnAgent(client);
// tools recebe apenas aliases cache previamente selecionados + sessão autenticada + catálogo aprovado.
CompletableFuture<MultiTurnAgent.Outcome> job = agent.run(userText, tools,
    new MultiTurnAgent.Limits(8, 16, 65536, 65536), trace -> enqueueUiUpdate(trace));
// Nunca get/join dentro do tick. job.cancel(true); fechar agent/client ao descarregar mundo.
```

Base exata **https://127.0.0.1:8081/v1**, única rota `/chat/completions`, JSON OpenAI (`model`, `messages`, `tools`, `max_tokens`, `stream:false`); modelo obrigatório, sem nome assumido. Não procura tokens/credenciais. `JvmTransport`: HttpClient assíncrono, TLS e hostname da JVM **padrão**, sem bypass/fallback, redirects NEVER, conexão 5s, requests até 60s, sem proxy para evitar sair do loopback. Certificado deve ser confiável e válido para o IP 127.0.0.1. Certificado/modelo/servidor real ainda não testados.

BodySubscriber limita bytes enquanto recebe; timeout total independente cancela transporte mesmo se cabeçalhos chegaram. In-flight 1..4, request 256 KiB, response 256 KiB no máximo, max_tokens 1..8192. Indisponibilidade/3xx/429/5xx/malformed resultam em future excepcional; não há retry oculto nem thread de tick esperando. `Transport` injetável retorna future; testes usam somente fake (inclusive indisponibilidade/timeout/cancel).

`MultiTurnAgent` tem um worker daemon e apenas uma execução ativa, 1..16 turnos, 1..32 calls (máximo 8 por resposta), 64 mensagens, transcript até 131.072 caracteres. Antes de cada request reserva UTF-8 bytes de input/tools + margem + max_tokens contra quota; isso é estimativa **conservadora, não tokenizer exato**. Usage reportado não diminui quota. Valida assistant role, choices e finish_reason; respostas cortadas/refusals não geram proposta. `Trace` entrega cada resultado de ferramenta imediatamente após append, em ordem; próxima rodada recebe assistant tool_calls e tool_call_id correlacionados. Sem streaming token/SSE nesta fase. Listener é callback de infraestrutura confiável: UI deve enfileirar sem bloquear worker e conservar auditoria conforme policy do servidor. Trilha não é persistida automaticamente.

### Ferramentas reais

| Nome | Argumentos exatos | Implementação |
|---|---|---|
| `list_mods` | `{}` | Aliases autorizados, não scan de filesystem/mod loader |
| `list_sources` | alias | Entradas do manifesto daquele cache |
| `read` | alias, entry, offset, limit | Slice de source; limit <=8192 |
| `search` | alias, entry, query, offset, max_hits | Busca literal limitada; até16 hits |
| `catalog` | `{}` | Catálogo JSON previamente aprovado; não muta confiança |
| `state` | `{}` | Snapshot permitido ao dono |
| `read_script` | offset, limit | Slice do script ativo com schema/revisão |
| `validate_script` | file, source | Compila+preview vazio, sem ativar; não prova todas condições dinâmicas |
| `propose_script` | file, source | Mesmo validate; guarda até8 propostas em memória com baseRevision e status UNAPPROVED |

Schemas OpenAI gerados, additionalProperties:false e validação runtime independente, argumentos/resultado/total limitados. São 32 calls e 65.536 caracteres de output por instância de tools, até16.384 por resposta. Criar uma instância por conversa. Não há read livre, list de diretórios, shell, Java, upload, URL, decompile-tool, auto-stage ou mark_verified. O dono/adapter revisa `Proposal`, reconfirma baseRevision e chama `session.stage` fora da autoridade da IA. Tools atualmente propõem schema compatível sem migração; mudança de schema exige revisão/migração explícita via adapter.

Fonte decompilada é envelopada como **UNTRUSTED_SOURCE_DATA_NOT_INSTRUCTIONS** e o system message repete a fronteira. Isso não garante que um modelo nunca siga prompt injection no texto; a segurança vem de ele não ter ferramentas de execução, acesso livre ou promoção de VERIFIED. Testes demonstram negativa de shell/traversal/URL e que propostas não alteram a sessão, não comportamento de um modelo real.

## Cache e decompiler opcional

`SourceCache(root).put(key,entry,source)`, `.list(key)`, `.read(key,entry,offset,limit)`, `.search(key,entry,query,offset,maxHits)`, `.invalidate(key)`. Chave = SHA-256 integral do JAR + loader + Minecraft + loaderVersion + decompilerVersion + schemaVersion, serialização por comprimento -> SHA-256 namespace. Payloads por hash do conteúdo; manifesto formato1 publicado atomicamente e verificado na leitura; corrupção é negada. Charset UTF-8, offsets/cursor em unidades UTF-16 Java; slice pode separar surrogate pair, consumidor deve tratar Unicode se precisar limites por codepoint.

Até128 entradas/chave, 256 KiB/source, 16.384 caracteres/slice API direta, 32 hits/search, disco64 MiB e scan limitado. Invalidação remove somente namespace selecionado e rejeita arquivos inesperados/links. Versão mudada nunca reutiliza namespace antigo. Sem LRU automático: arquivos órfãos de publicação/replacement continuam contando quota até invalidar chave; root privado e writer único. Sources são documentação, nunca classes carregadas.

`VineflowerRunner.Config(javaExecutable,decompilerJar,version,approvedMods,workRoot,timeout)` exige arquivos regulares canônicos explicitamente configurados/confiados. `.decompile(relativeJar,classEntry,loader,mc,loaderVersion,schema,cache)` seleciona **uma** `.class`, calcula hash integral por stream, valida tamanho/ratio/magic, extrai só essa classe. `ProcessBuilder` sem shell: java, `-Xmx256m`, `-jar`, jar do decompiler, `--folder`, input próprio, output próprio. Nenhum arg extra da IA, nenhum JAR de mod na linha de execução. Fingerprint inclui versão+SHA do decompiler; variáveis de injeção JVM removidas. Mod não é executado.

Quotas: JAR256 MiB, decompiler64 MiB, 16.384 ZIP entries, classe1 MiB/ratio<=100, log256 KiB (drain assíncrono), output2 MiB/128 nós, timeout<=60s, kill/limpeza ao falhar/cancelar; source publicado limitado a256 KiB. Heap/timeout e polling não são sandbox/quotas rígidas de processo nativo: decompiler é infraestrutura confiável, pode haver overshoot de output entre polls; executar em worker exclusivo/root privado. Sem dependências de contexto ou árvore inteira por padrão; uma classe isolada pode produzir source incompleto. JAR Vineflower real **não configurado/executado nos testes desta entrega**; comportamento CLI/version-output requer smoke test com ferramenta escolhida. Não chamar no tick.

## Próximos adapters

Forge 1.20.1/Java17 e Fabric 1.21.1/Java21 devem construir contra APIs reais, usar Session para posse/revisão, eventos derivados do servidor e intents explicitamente suportados; implementar sync/editor, inventário e save do mundo. Estado de combustível/components exige registro único e snapshot equivalente. Absorção geral de reator continua bloqueada sem adapter que preserve combustível/NBT/inventário/temperatura/subprodutos e prove prepare/commit/rollback. Decompilação é evidência DECLARED apenas. VFX é futuro e desacoplado da contabilidade: nunca 500 bilhões de entidades/partículas.
