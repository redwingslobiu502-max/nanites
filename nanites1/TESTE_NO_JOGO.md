# Roteiros manuais Nanites

## Atual — quatro etapas Forge 1.20.1 / Java 17

Comece pelo [teste ponta a ponta Forge: máquinas, assimilação, fábricas persistentes e armadura viva](docs/TESTE_QUATRO_ETAPAS_FORGE.md), usando o [JAR stage4 auditado e seu SHA256](docs/RELEASE_STAGE4_FORGE.md). Os passos in-game são pendentes de execução pelo usuário, não resultados da auditoria offline.

## Histórico — Nanites 0.2.0

**Jogo ainda não foi iniciado pela automação.** Compilação/auditoria não validam rendering, mixins em runtime, crafting ou compatibilidade do seu modpack. Primeiro teste em mundo novo sem outros mods, um loader por vez, seguindo [INSTALL.md](INSTALL.md). Não use mundo valioso.

## 1. Itens e depósito (sem LLM)

1. Crie mundo com cheats para diagnóstico; inicialmente modo Survival, partículas **Todas**. Abra chat com T. `/nanites` lista ajuda; `/nanites status` mostra UUID/revisão/saldo.
2. Craft caixa na bancada: `I R I / R G R / I R I`, I=ferro, R=redstone, G=ouro. Craft reservatório: `I [vazio] I / R G R / I I I`, G=vidro. São itens com modelos/texturas próprios.
3. Alternativa rápida com cheats: `/give @s nanites:reservoir 1` e `/give @s nanites:box 2`. Caixa recebe carga fixa500_000_000_000; criatividade/admin podem obter caixas como qualquer item registrado. Não é prova contra cheats de administradores.
4. Sem reservatório no inventário, botão direito **no ar** com caixa deve falhar e não consumir. Com reservatório, use caixa **na mão principal**: cada clique aceito consome **uma** caixa (inclusive no Creative). Mão secundária é recusada; cooldown10ticks impede repetição imediata. Aguarde pelo menos meio segundo entre cliques; segurar botão pode consumir mais caixas em usos sucessivos, nunca creditar stack vazio. Cada uso aceito deposita500bilhões. `/nanites status`: `storage` cresce, `spent` é consumido irreversível. Sem multiplicação pela quantidade do stack. Repita uma vez e confirme +1trilhão total, duas caixas consumidas.
5. Saldo é por jogador/mundo, não por nome de armadura. Entregar reservatório a outra pessoa **não transfere saldo**. Limite storage/spent10trilhões cada; gasto conserva storage+spent, intake é só a fronteira de caixa consumida. Retiradas não existem.

## 2. Proteção e VFX reais

1. Antes de depositar, faça uma queda moderada (ex. 6–8blocos) em Survival: saldo vazio não dá proteção nem efeitos. Evite alturas letais. Após depositar, repita com script padrão: protege até20pontosHP da queda e mostra anel de partículas end-rod ao redor do corpo. Hits incrementa.
2. Impacto (ex. ataque de um mob) usa até4HP; nenhum Java de script é executado. Consulte status antes/depois: queda solicita custo20.200.000; impacto4.100.000 nanites. Custos correspondem à **ação solicitada**, mesmo se dano era menor ou VFX for suprimido por budget.
3. Faça a comparação sem escudo: `/nanites edit program inert version v1; state hits = 0;` depois `/nanites apply`. Caia de novo. `/nanites rollback` reativa o script anterior; **não reembolsa gasto**. Rollback anterior só nesta execução do servidor, não após restart.
4. VFX usam renderer vanilla no cliente através dos packets reais do servidor: são transitórios, não armadura custom nem blocos sólidos. Em terceira pessoa (F5), ver anel/coluna é mais fácil. No máximo24partículas/jogador/segundo; ambientes com partículas mínimas podem ocultar efeitos.

## 3. Editar/importar sem UI complexa

1. `/nanites example meu_script` cria arquivo em `config/nanites/scripts/<UUID>/meu_script.nano`. O UUID aparece em status. **Em servidor remoto esta pasta está no servidor**; peça ao operador para editar o arquivo do seu dono, não a pasta do cliente.
2. Abra no editor de texto e substitua o conteúdo por `examples/game-structure.nano` deste projeto. Esse exemplo usa coluna paramétrica a cada40ticks e escudo de queda. `/nanites preview meu_script`: simula queda8HP sem gastar/aplicar VFX. `/nanites load meu_script`: valida e hot-swap. Após2segundos veja coluna; há custo300.000 por emissão.
3. `/nanites export backup`: exporta código ativo, **não saldo**. Nomes simples (letras/números/_/-), sem extensão, sem caminhos; arquivos existentes não sobrescritos por export/example. Para importar altere um arquivo do próprio UUID e use load.
4. Erro proposital: troque `emit shape.column self = 3;` por capacidade inexistente. Preview/load devem informar erro e manter código anterior; sintaxe inválida mostra arquivo/linha. Volte ao arquivo correto. Valores fora da allowlist, event desconhecido, divisão por zero, overflow e budgets falham sem executar world actions do evento.
5. Todos os exemplos de jogo preservam `state hits` e `version v1`. Renomear/remover campos não é suportado pelo editor de jogo desta fatia; core tem migrations explícitas mas comandos não expõem mapas de migração. `examples/fall.nano` do lote1 é fixture de IR **não importa diretamente no adapter**; use game-*.nano.

## 4. Material isolante rastreável

1. Segure **lã branca** na mão principal e `/nanites absorb`. Consome uma; `/nanites material` mostra registry, dono, contagem/revisão/evidência. Pedra não mapeada deve ser recusada sem consumo.
2. Importe `examples/game-insulating.nano` em arquivo do seu UUID e load. Com saldo, dano de fogo passa a ser reduzido até3HP/evento, com custo3.200.000 e coluna; sem material mapeado não há proteção. Teste com fogo pequeno, em segurança, não em lava profunda. É uma regra explícita deste mod, não inferência de física da lã.
3. `/nanites reactor` sempre informa **UNSUPPORTED** nesta versão. Não há energia produzida, extração de bloco externo, integrações energia amaldiçoada/AOT, absorção geral de reator ou combustíveis. SPI core existente permanece futuro até adapter auditado concreto.

## 5. Persistência/posse

1. `/nanites save`, saia normalmente do mundo e reabra. Status deve manter saldo/script/hits. Verifique queda novamente. Morte/relogin não transfere saldo; IA/propostas da sessão antiga são descartadas no logout.
2. Se possível, segundo jogador: saldo/arquivos independentes. Não há comando target/dono/UUID arbitrário nem acesso de OP a script alheio por padrão. Não criamos/quebramos blocos, portanto não bypassamos claims. Não tentamos instalar em perfis pessoais.
3. Não force crash para validar conservação: não alegamos transação durável entre snapshot e playerdata. Backup deve incluir **mundo e config/nanites** juntos.

## 6. IA/fontes opcionais

Sem configurar modelo, `/nanites ai proponha um escudo` deve explicar ai.model REQUIRED, **sem rede**. Após operador configurar nome exato e TLS válido no endpoint fixo, reinicie manualmente. `/nanites mods` lista mods reais do loader. `/nanites ai Proponha Nanite Script v1 preservando state hits, group owner, escudo queda e anel` inicia worker; esperar resposta não bloqueia tick. `/nanites proposal 0` exibe proposta/baseRevision; revisar e só depois `/nanites apply`. Nova validação usa estado atual; proposta antiga não altera inventário/mundo automaticamente.

Decompiler desabilitado por padrão: `/nanites decompile nanites dev/nanites/game/GameService.class` só funciona depois de permissão/configuração explícita do operador conforme INSTALL. Nunca pede executável/path ao jogador. Nenhum LLM real/decompiler real foi executado aqui. Não existe editor gráfico, chat UI dedicado, código Java baixável nem integrações arbitrárias.

## O que registrar se falhar

Loader/MC/Java exatos, JAR/hash, lista de mods, comando, mensagens e `logs/latest.log`/crash-report. Remova tokens de configuração antes de compartilhar. Se falhar ao iniciar, não tente instalar o JAR do outro loader como solução. Testes de engenharia constam em `dist/VALIDATION.txt` e `docs/LOTE2.md`; roteiro visual/runtime continua manual.
