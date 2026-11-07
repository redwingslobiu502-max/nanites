# Arquitetura e ADR-001: runtime dinâmico

## Fronteiras

```
Servidor Minecraft (thread de tick, autoridade e permissões)
  Forge 1.20.1 / Java 17        Fabric 1.21.1 / Java 21
       adapters de mundo, inventário, persistência, eventos, energia e rede
                          |
                   core / Java 17
       Ledger -> reservatórios agregados, conservação
       CapabilityCatalog -> fatos/evidências/requisitos, definições custom
       EmbeddedComponent -> snapshot imutável com combustível e estado
       RuleEngine -> eventos/grupos, transação e migração
                          |
                   snapshots/deltas limitados
                          |
                  render/VFX no cliente
```

Somente o core existe. Não há fontes dependentes de Minecraft, plugins de loader ou manifests anunciando um mod compilável. Java 21 pode consumir o bytecode Java 17; isso não substitui testar adapters contra as APIs reais de cada loader. Forge e Fabric não compartilharão classes de renderer, registro, networking ou energia. O projeto de referência é de outro target e não serve como scaffold desta base.

## Decisão de runtime

**Agora (lote 1):** `ScriptLanguage.compile(file,source)` implementa Nanite Script 1: lexer/parser próprios -> AST selada auditável com arquivo/linha -> `Program/ScriptAction` interpretados pelo RuleEngine. Expressões long checked, precedência, funções de expressão, if/else, repeat limitado, event/state fields e intents genéricos validados. Fonte/AST/profundidade/fuel global são limitados. Não há compilação/reflection/classloader de programas, eval, callbacks de usuário ou Java arbitrário. IR antiga Transfer/AddState segue compatível. Não é uma linguagem JVM de execução livre.

`NaniteSession` implementa ledger privado, dono/revisão, fila, preview isolado, stage/tick/migração/rollback do script. `SnapshotStore` implementa snapshots JSON estritos/atômicos. `LocalChatClient`, `MultiTurnAgent`, `ControlledTools` e `SourceCache` são implementações reais; runner Vineflower seletivo opcional exige ferramenta explicitamente confiada. APIs, exemplos, quotas e limites exatos em [LOTE1.md](LOTE1.md).

**Próximo:** adapters reais Forge 1.20.1 e Fabric 1.21.1, edição/UI/sync, composição material e execução de intents com revisão do mundo. Tipos de unidades além de long e sintaxe mais rica podem evoluir com migrações explícitas, sem ampliar autoridade dos scripts. Preview atual é simulação do núcleo, não renderer nem física Minecraft.

**Por quê:** callbacks Java irrestritos não permitem interromper com segurança código hostil dentro da JVM. Threads/Future.cancel/interrupção não são isolamento nem timeout seguro. Subprocesso controlado poderia ser estudado para linguagens externas, com protocolo de propostas e limites do sistema operacional; nem isso dá autorização para alterar o mundo diretamente. Nesta fase não há callbacks de execução nos programas; as interfaces Java de adapters/IA são fronteiras de infraestrutura **confiável**, não sandbox para mods desconhecidos.

## Contabilização

- `500_000_000_000L` nanites por caixa; nenhuma entidade por unidade. Dois reservatórios com 500 bilhões custam duas entradas no mapa, não um trilhão de objetos.
- Conta identifica reservatório e definição abstrata, sem slot de armadura. Uma ponte e um bracelete seguem as mesmas operações.
- Total é soma checked dos saldos iniciais, máximo `Long.MAX_VALUE` (~18,4 milhões de caixas inteiras). Capacidade e saldo não negativos. Overflow falha, não satura/wrap; não usar `double` para posse.
- Cada lote aplica transferências em cópia; erro em qualquer passo descarta a cópia. Auto-transferência só é aceita se saldo suficiente. Transferência zero ainda exige contas válidas. Não existe criação/remoção em runtime.
- Crafting, destruição, drops e import/export de inventário exigirão transações de autoridade e registro de origem, não alteração silenciosa do total. Persistência precisa guardar revisão/schema e evitar duplicações na reabertura do mundo.
- Limite atual: todos os nanites contabilizados são fungíveis; composição material por lote, massa e energia ainda não modeladas. Capacidades do catálogo não convertem quantidade em energia.

## Propriedades, componentes e confiança

`Property` define domínio permitido; `Conflict` define combinações proibidas de forma genérica. `Capability` tem requisitos de propriedades e origem. `Definition` pode ser qualquer estrutura. Exemplo: `insulating=true` e `conductive=false`; `true/true` conflita por regra configurada, não por nome da armadura.

`VERIFIED` só é aceito quando adapter+versão está na lista de confiança E os fatos exigidos também são verificados. A evidência é rastreável, mas não criptograficamente comprovada por esta biblioteca. Quem pode construir catálogo/lista de confiança é código de servidor confiável. Texto da IA, JAR decompilado ou string de item desconhecido nunca deve emitir confiança. Testes usam `fixture@1`, explicitamente **não** integrador real.

`EmbeddedComponent` guarda tipo, origem, proprietário, combustível tipado, estado numérico e requisitos. Realocação pura valida destino, conserva combustível/estado/requisitos e não muda o original. Não administra inventário real/posse exclusiva: snapshots retidos não são componentes duplicados no mundo. Um futuro registro de componentes únicos e adapter transacional terá de garantir exclusividade. `debitFuel` somente calcula nova contabilidade; não calcula RF/FE, potência, calor, queima ou subprodutos. Não implementamos absorção de reator sem adapter real.

## Tick e hot-swap

1. `stage` valida referências, grupos/eventos, campos e migração. Segundo candidato pendente é rejeitado, não substitui silenciosamente.
2. Antes do tick o programa ativo é o anterior. Apenas um candidato é consumido na próxima chamada `tick`.
3. Estado compatível: mesma versão e mesmas chaves; novos defaults não zeram estado. Incompatível: migração explícita com versões de origem/destino, renomes, drops intencionais e defaults para novas chaves. Perda implícita, campo desconhecido, drop+rename ou colisão são erros.
4. Tick copia eventos/estado e avalia regras na ordem das listas. Grupos/eventos precisam ser registrados. Transferências são enfileiradas; alterações de estado são checked. Não há condições que leiam saldos intermediários nesta IR.
5. Estado migrado e novos mapas são construídos antes do commit do lote de ledger. Tudo válido: publicar programa e estado. Qualquer `RuntimeException` antes/do commit: resultado de falha; ledger, estado e programa anterior permanecem. Candidato falho é descartado. Intents de mundo só são publicados após commit; nenhuma execução de efeito externo nesta fatia. Rollback de script após ativação preserva ledger/estado atuais, não desfaz mundo.
6. Erros fatais da JVM, falta de memória ou morte de processo não têm rollback garantido. Futuro save/adapter precisará journal ou prepare/commit com revisão.

`RuleEngine.snapshot()` é a leitura composta sincronizada. Para uso real, manter ledger privado ao serviço de servidor e nunca expor mutações paralelas externas; leitura direta do ledger não é uma visão atômica conjunta do programa/estado. A ordem de locks é engine -> ledger, sem chamada inversa/callback. Eventos recebidos pela rede terão validação de dono, grupo, distância e custo: registro de grupos hoje não constitui ACL nem permissão de jogador.

## Limites e desempenho

- Por programa: 64 regras; por regra: 32 ações; estado: 64 campos; IDs: 96 caracteres.
- Por tick configurável: 1..256 eventos e 1..4096 ações, fuel global 16.384 passos para scripts. Regras com evento/grupo diferente são ignoradas; busca limitada por eventos x regras. Repeat/recursão de funções são limitados por contagem/profundidade/fuel, nunca loops livres ou objetos por nanite. Fonte/AST/tokens também limitados; ver LOTE1.md.
- Orçamento de operações **não é timeout de parede seguro**. Alocação, GC, entrada gigante antes da API ou adapters bloqueantes ainda podem custar tempo. Integrador deve limitar número de reservatórios, bytes de entrada, filas de eventos e jamais chamar LLM/decompiler no tick. Não há benchmark de mundo real nesta etapa.
- Pipeline futuro: consolidar pedidos por grupo/região -> resolver transação no servidor -> emitir deltas de baixa frequência -> renderer com amostragem/LOD/instâncias e orçamento de partículas. Não enviar um pacote nem criar entidade para cada nanite. VFX não define posse, dano ou combustível.
