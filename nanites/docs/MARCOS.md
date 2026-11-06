# Marcos até um mod jogável

## Estado atual — Lote 2, Nanites 0.2.0

**Forge1.20.1/Java17 e Fabric1.21.1/Java21 compilados contra dependências Minecraft/loader reais; reobf/remap e auditoria passaram.** Artefatos em `../dist`, instalação em [../INSTALL.md](../INSTALL.md), teste manual em [../TESTE_NO_JOGO.md](../TESTE_NO_JOGO.md), logs/hashes/capacidades em [LOTE2.md](LOTE2.md). Não lançamos Minecraft nem LLM; runtime/visual/dedicated server/modpacks continuam pendentes de teste manual.

M1 parcial: serviço autoritativo por dono/mundo, intake real consumindo caixa, spent irreversível, snapshots validados individuais. Sem save transacional universal com playerdata/crafting, retiradas portáteis ou journal de recuperação cross-Minecraft.
M2/M3: dois adapters concretos com registries/item/recipes/lang/texturas, comandos, fall/impact/tick, redução de dano orçada e VFX reais pela renderização vanilla cliente. 87 testes core +22 checks de serviço por build. Não são stubs.
M4: comandos editor/import/export/preview/load/rollback do código funcionais; sem GUI complexa, sem mapas de migration expostos no jogo. Migrations core preservadas.
M5: IA local comandos/worker/nove ferramentas/owner review e source/decompile seletivo opt-in integrados; TLS/LLM/decompiler real não executados nesta validação. Sem execução Java/instalação/reload de adaptadores pelo jogador ou IA.
M6: absorção de material de inventário explicitamente mapeado com registro por UUID e proteção de fogo condicionada. **Reator/energia externa UNSUPPORTED**, SPI core futura sem implementação externa instalada.
M7 parcial: anel/coluna numéricos budgeted, renderer vanilla visível; **não blocos sólidos, OBJ/armadura custom nem construção ilimitada**.

Os critérios de roadmap e o estado do lote1 abaixo são históricos/pendências, não promessas de funcionalidades já entregues.

## Lote 1 concluído — núcleo ampliado

Implementados: Nanite Script 1 (parser/AST/expressões/condicionais/funções/budgets), dono/revisão e fila, preview/hot-swap/migração/rollback do script, snapshots JSON versionados validados/atômicos, cliente local HTTPS e loop multi-turn assíncronos, nove ferramentas allowlist e cache real. Runner Vineflower seletivo opcional implementado, ainda sem smoke test com JAR real. **87 testes passam (33 anteriores + 54 novos) e duas demos em JDK17 e JDK21, bytecode17**. Contrato atual: [LOTE1.md](LOTE1.md).

M1 está parcialmente entregue no núcleo; falta transação com inventário/registro de componentes e save coordenado do mundo. M4 linguagem/hot-swap entregue; falta editor/UI e efeitos reais. M5 cliente/agente/cache entregue com fake; falta validação de TLS/modelo e decompiler reais. Próximo lote: adapters M2/M3 contra APIs reais, sem fingir que intents já alteram mundo. M6 absorção geral de reator e M7 VFX continuam futuros.

As seções M0 e critérios futuros abaixo preservam o roadmap original; leia este estado do lote 1 como atualização das partes já implementadas.

## M0 — esta entrega (core independente)

Implementado: contabilidade long/conservação/lotes, definições livres, propriedades/capacidades com evidência, componente-snapshot, IR de regras limitada, hot-swap/tick/migração/rollback, testes sem dependências, demo/JAR core, contratos documentados. Sem mundo, rede ou servidor IA. Critério: compilar Java 17 e passar harness local; resultado registrado em VALIDACAO.md.

## M1 — autoridade e persistência

Serviço por mundo/dono com ledger privado, IDs únicos de reservatório/componente, revisão monotônica e journal/save versionado. Transações com inventário/crafting caixa; import/export com conservação; recuperação pós-crash. ACL por dono/grupo e limite de reservatórios/bytes/eventos. Critérios: reabrir mundo sem duplicar caixa/componente, rejeitar pacote velho/alheio, migrar save e recuperar falha antes/depois de commit. Não fingir que a atual realocação pura já é absorção no mundo.

## M2 — primeira integração jogável Forge 1.20.1

Criar adapter **compilável contra Forge real**, Java 17, após seleção explícita de toolchain/dependências. Item caixa craftável, inventário, grupo e estrutura simples arbitrária; eventos do servidor -> IR; UI básica de inspeção. Um reservatório não exige armadura. Testar em dedicated server e cliente, incluindo receitas e sync. Sem baixar/rodar jogo nesta entrega.

## M3 — Fabric 1.21.1

Adapter separado Java 21 (core continua --release 17), registro, networking, componentes de dados/save e renderer próprios. Não reaproveitar classes Forge. Mesmos testes core e cenários de conservação/save/ACL nos dois loaders; build real obrigatório antes de chamar scaffold funcional.

## M4 — linguagem expressiva e edição/hot-swap

Parser próprio -> IR versionada, condicionais/expressões checked e capacidades requeridas; mensagens de erro por posição, simulação/preview, autorização do dono e migração explícita. Ações com efeitos de mundo só entram quando suportam plano/validação/transação. Testar programas grandes/malformados, orçamento e compatibilidade. Evitar dezenas de interfaces vazias e execução Java livre.

## M5 — IA local multi-turn e índice

Cliente HTTPS padrão para endereço exato; conversa/ferramentas permitidas e auditadas; worker fora do tick; proposta validada, sem autoridade direta. Implementar cache por hash e todas versões, leitura seletiva e invalidação. Contratos atuais deverão ser refinados com erros, revisão, limites de bytes e respostas tipadas. Testar TLS válido/inválido, redirects negados, prompt injection, quota e indisponibilidade. Nenhuma credencial procurada automaticamente.

## M6 — primeiro integrador real

Escolher API pública/licenciada de mod concreto e versão suportada. Adapter auditado preservando combustível/estado/requisitos, simulação real/energia via API, posse única e rollback comprovado. Sem capacidade conhecida -> sem absorção funcional. Critérios: comparar inventários/energia antes e depois; falhas de revisão/commit não duplicam nem apagam recursos. Só depois expandir catálogo de integrações.

## M7 — VFX e customização ampla

Extração seletiva auditada de algoritmos visuais, meshes customizadas/preview, limites de assets, efeitos cliente desacoplados. Armaduras, ferramentas e construções pelo mesmo modelo de definição; bracelete/corpo/reator das referências são presets opcionais. Agregação e LOD/instancing/partículas orçadas, nunca 500 bilhões de entidades. Medir tick, tráfego, heap/GC e GPU antes de declarar desempenho.

## Bloqueios/decisões futuras

JDK 17 e 21 estão instalados, portanto core não depende de instalação. Gradle não é necessário aqui; wrapper não foi copiado. Faltam dependências/versões exatas dos loaders, ambiente de teste Minecraft autorizado, formato final da linguagem, API do LLM/modelo, certificado TLS confiável, integrador real e revisão de licença/atribuição visual. Esses pontos são próximos marcos, não falhas escondidas do harness atual.
