# Etapa 4 — armadura viva, Forge 1.20.1 / Java 17 / Nanites 0.3.1

Implementação sobre as etapas de máquinas, assimilação e autonomia existentes. Não altera jevmod, Fabric, receitas anteriores, adapters de máquinas ou grants do runtime. Não publica nem copia artefatos para dist/instalação. Não houve execução do jogo nem conexão a modelo real.

## Comportamento real

As quatro peças modulares mantêm os atributos/durabilidade de armadura de ferro. Vestir o peitoral modular permite resposta adaptativa server-side: exposição separada a impacto, queda, fogo e projétil, 0..8, armazenada no NBT `NanitesLiving` do próprio peitoral. Equipar mais peças aumenta a resposta. Cada evento válido aprende uma exposição; a próxima ocorrência usa a experiência anterior. A cada 200 ticks vestido, a exposição decai uma unidade. Nomes, encantamentos, `NanitesModules`, durabilidade e demais NBT não são substituídos.

Redução adaptativa = min(2 HP, dano × min(0.4, peças × 0.025 + exposição × 0.025)). O módulo protege primeiro; a adaptação é calculada sobre o dano restante e acrescentada à solicitação. **O total inteiro é pago uma vez pelo mesmo saldo/teto**, sem proteção gratuita ou pagamento duplicado. O limite adaptativo atual chega a 30% com quatro peças e oito exposições. Cada HP efetivamente bloqueado custa 1.000.000 nanites no saldo real de GameService. Compartilha o teto anterior de 8 HP por janela de 20 ticks com scripts/módulos/runtime. Sem saldo não há bloqueio, embora observar um dano ainda possa ensinar. Sem peitoral modular não há adaptação; outras peças sozinhas mantêm somente sua armadura vanilla.

Fogo mantém requisitos anteriores: módulo thermal e material isolante do contrato histórico `absorb` para adaptação térmica; estoque de assimilação não é convertido silenciosamente em isolamento. Danos `BYPASSES_INVULNERABILITY` não recebem a nova proteção; `BYPASSES_ARMOR` não recebe proteção genérica, mas queda/fogo conservam seus módulos especializados. Tipos novos de mods são classificados apenas pelas tags públicas, sem alegar imunidade universal. Ordem de hurt preservada: script -> módulos/adaptação -> runtime. Mods que alteram eventos depois podem modificar o resultado final.

## Reparo real e custos

Uma chamada repara um slot `head|chest|legs|feet|mainhand|offhand` do dono vivo, na thread servidor. Pode reparar equipamento vanilla ou de mod **somente** se o item expõe durabilidade e aceita o material real via `Item.isValidRepairItem(equipment, material)`. Não interpreta receitas privadas, FE de armaduras ou capacidades não serializáveis.

1. Decodifica do estoque a identidade/NBT exatos, sem debitar, e verifica compatibilidade.
2. Reserva UMA unidade disponível da identidade, sem gastar reservas de máquinas/objetivos.
3. Debita 100.000 nanites por ponto restaurado.
4. Faz commit único da reserva e altera a durabilidade do equipamento real, preservando o resto do NBT.

Pontos = min(dano atual, max(1, durabilidade máxima / 4)). É uma regra explícita de consumo por ingrediente, não execução genérica de receitas de mods. `preview` informa custo/pontos sem reservar, pagar ou reparar. Sem material compatível ou nanites, recusa/stall; sem dano, não gasta. Falha antes do commit devolve o débito e libera a reserva. Falha depois do commit é AMBIGUOUS, nunca recibo fictício de reparo.

## IA, comandos e configuração persistente

Ferramentas `armor_status`, `armor_configure`, `armor_reload`, `armor_repair` usam os mesmos schemas/backend em GameService, com dono autenticado, marshalling de thread e invalidação de callbacks antigos. `machine_live.armor` mostra seis slots, módulos, exposição, configuração, último recibo e `pendingRecovery`. Factory goals podem observar/reparar o dono e registram intenção/recibo de reparo; não podem configurar/rearmar a política persistente por rodada autônoma. A IA manual pode configurar sem arquivos externos/restart. Adapters de máquinas continuam restritos a operações de máquinas, não ganham acesso a equipamento.

Comandos sem IA:

```text
/nanites armor status
/nanites armor_status {}
/nanites armor list
/nanites armor install impact
/nanites machine_schema armor_configure
/nanites material_stock {"offset":0,"limit":4}
/nanites armor repair {"slot":"chest","key":"HASH_REAL_DO_ESTOQUE","preview":true}
/nanites armor_repair {"slot":"chest","key":"HASH_REAL_DO_ESTOQUE","preview":false}
/nanites armor configure {"version":2,"enabled":true,"adaptive":true,"autoRepair":true,"repairKey":"HASH_REAL_DO_ESTOQUE","repairSlot":"chest","intervalTicks":100}
/nanites armor reload
```

Substitua HASH_REAL_DO_ESTOQUE pelos 64 caracteres hexadecimais retornados pelo estoque. `armor install` ainda exige módulo correspondente na mão e consome uma unidade. Reparo automático é desativado por padrão. `enabled:false` desativa módulos/adaptação/reparo automático adicionais; não remove atributos vanilla nem os outros estágios. Chamadas explícitas de reparo continuam possíveis. Configure `autoRepair:false` para parar manutenção sem parar a proteção.

Configuração por dono: `<mundo>/nanites/living-armor/<UUID>.json`, escrita por substituição atômica e validada integralmente; conteúdo diferente exige versão crescente. `armor_reload` valida antes de substituir e mantém o último bom em erro. Perfil existente corrupto no primeiro acesso não vira default silencioso; corrija o arquivo e use reload. Perfil no máximo 8 KiB, versão 1..1.000.000, intervalo 20..72000 ticks; autoRepair exige key válida. Logout limpa caches, não arquivos; restart recarrega perfil. Cooldown de manutenção reinicia no login, sem carregar chunks ou usar modelo.

Antes de reparo real grava `<UUID>.repair-pending`. Interrupção ou erro pós-commit retém o marcador e bloqueia novos reparos automáticos/manuais após reload/logout/restart. Inspecione equipamento/estoque e configure uma versão **maior** para rearmar; reload sozinho não limpa ambiguidade. Preview permanece possível. Marcador é prevenção de replay detectável, **não ACID/fsync entre arquivos do jogador, SavedData e saldo**: saves normais são independentes; uma queda antes de saves ainda pode perder/duplicar a última operação. Recibo confirma mutação na thread, não durabilidade física de disco.

## Validação offline e teste pelo usuário

Regressões novas: `LivingArmorTests` (política, economia, ledger real, reservas concorrentes, reload LKG, marcador/restart, ticks/gateway GameService) e `ForgeLivingArmorTests` (NBT binário, exposição/decay, preservação e predicado real de reparo/ItemStack; bootstrap de registries apenas, sem cliente/servidor). O fixture JavaExec marca por reflexão somente o guard privado de bootstrap para não chamar NetworkHooks/transformações do launcher; não substitui predicados, NBT ou comportamento dos itens em teste e não modifica código de produção. As suites anteriores continuam em check. Consulte [ETAPA4_ARMADURA_VIVA.md](ETAPA4_ARMADURA_VIVA.md) para comandos e resultados efetivamente observados.

No seu mundo de teste: equipe peças, financie saldo pelo reservatório/caixas existentes, assimile material compatível, compare preview/reparo/estoque, repita dano da mesma família, cheque exposição, desligue adaptive e compare, teste sem saldo, troque peitoral e relogue. Verifique thermal com isolamento, item de mod com predicado compatível/incompatível, restart e configuração inválida. Estes são passos **a realizar pelo usuário**, não validação em jogo feita pela implementação.
