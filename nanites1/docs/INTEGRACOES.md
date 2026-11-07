# Contratos de integração — histórico M0

> **Documento histórico da fundação anterior.** As afirmações abaixo de que linguagem, cliente, loop e cache eram apenas contratos foram substituídas pelo **lote 1 implementado**, documentado em [LOTE1.md](LOTE1.md), que é o contrato atual chamável. Preservamos esta análise de confiança e planos de integração como histórico; absorção real de reator e VFX continuam futuros. Não interpretar descrições históricas de LRU/journal como recursos já entregues.

## IA local

Endpoint exigido: **`https://127.0.0.1:8081/v1`**, exatamente. `validateAiEndpoint` aceita apenas esse URI, sem HTTP, userinfo, query, fragmento, host alternativo ou barra final. Não lemos credenciais, não chamamos servidor nem abrimos rede. `LocalAiClient` é somente contrato; não implementa transporte nem modelo.

Implementação futura: `java.net.http.HttpClient`, TLS/truststore e verificação de hostname **padrão da JVM**, redirects `NEVER`, validação do endpoint no início de cada chamada; endpoint base mantido exato e rotas de API constantes, não ferramentas definindo URLs. Certificado deverá ser válido para o IP `127.0.0.1` e cadeia confiável. Se não for, falhar com diagnóstico: jamais `trustAll`, desabilitar TLS/hostname ou fallback HTTP. Importar CA seria decisão explícita do usuário em truststore dedicado, não algo realizado nesta fase. Não há certificado validado nem teste de conexão aqui.

Timeout de conexão/resposta e limites de corpo/conversa ajudam transporte; não tornam Java arbitrário seguro. Worker fora do tick, cancelamento cooperativo e propostas de dados. Modelos/rotas/formatos precisam ser confirmados ao implementar cliente, sem assumir nome de modelo ou token.

### Agente multi-turn com ferramentas controladas

`AgentTurn` representa histórico `user/assistant/tool`, conjunto permitido e turnos restantes (1..16); máximo 64 mensagens, 32.768 caracteres/mensagem. **Não é ainda um loop de agente.** Contrato de ferramentas fechado:

| Ferramenta | Efeito planejado |
|---|---|
| READ_CATALOG | Ler apenas capacidades/dominios e evidências aprovados |
| READ_STATE | Snapshot permitido pelo dono, com revisão |
| READ_INDEX_SLICE | Fatia limitada do índice selecionado e chave cache |
| VALIDATE_PROGRAM | Compilar/validar proposta sem ativar |
| PROPOSE_PROGRAM | Proposta aguardando autorização, jamais commit direto |

Fluxo futuro: usuário -> plano -> chamadas tipadas -> validação de argumento/permissões -> resultado limitado -> próxima rodada -> candidato -> autorização do dono/política do servidor -> stage com revisão -> fronteira de tick. A cada rodada descontar orçamento de turnos/chamadas/tokens/bytes e guardar trilha de auditoria. Chamadas não listadas são negadas; nenhum shell, Java, URL arbitrária, decompilar tudo, filesystem livre ou ferramenta que marque evidência como verificada. Texto de mods/decompiler é dado não confiável contra prompt injection, não instrução do agente. A IA não pode declarar combustível, energia ou capacidade inexistente.

## Vineflower, cache e leitura incremental

Nenhum JAR de mod foi executado, indexado ou decompilado. Não instalamos Vineflower; `IncrementalIndex`/`CacheKey`/`Slice` são contratos com validação de parâmetros, não cache em disco.

Chave obrigatória: **SHA-256 do conteúdo completo do JAR + loader + versão Minecraft + versão loader + versão exata do decompiler + schema do índice**. Não cachear só por nome/timestamp. `CacheKey` é valor imutável e diferencia todos esses eixos. Persistência futura serializa de forma canônica (campos com tamanho ou codificação inequívoca), e inclui versão do adapter/mapeamentos/opções que afetarem resultados no manifesto ou revisão do schema. Mudança de qualquer eixo gera namespace novo; `invalidate(key)` remove somente aquele namespace lógico. Schema ou evidência de adapter inválidos nunca reutilizam um status verificado antigo.

Pipeline planejado:
1. Selecionar JAR autorizado, calcular hash por stream, ler manifesto e índice de entradas sem carregar classes.
2. Restringir quantidade/tamanho descomprimido/ratio, paths e formatos; negar zip-slip, zip bombs, symlinks e referências externas. Limites de `Slice` não substituem sanitização completa do worker/arquivo real.
3. Decompilar seletivamente apenas classes necessárias em processo de infraestrutura com orçamento de memória/tempo/output. O decompiler roda como ferramenta confiável; **o bytecode do mod não é executado**. Nenhuma saída vira Java executável, plugin dinâmico ou regra autorizada.
4. Gravar resultados/fingerprint temporários e publicar atomicamente o manifesto completo; crash deixa entrada inválida, não parcialmente confiável. Lock por chave, limites LRU de disco, invalidação explícita e limpeza restrita ao cache do nanites.
5. Ler por entrada+offset+limit, máximo 16.384 unidades por fatia. O contrato atual representa texto Java (offsets de caracteres); implementação deve documentar encoding/offset e tratamento de Unicode. Não mandar JAR inteiro ou todo código à IA. Retornar truncamento e cursor.
6. Mapear sugestões para adapters pré-aprovados. Informação inferida fica DECLARED; verificação requer APIs reais, testes de versão e condições de operação, não palavras como "reactor" ou "energy".

## Componentes e energia de outros mods

`ComponentAdapter.prepare`/`PreparedExtraction` descrevem protocolo futuro, **sem adapter concreto e sem caminho de absorção implementado**. IDs/versões e revisões do mundo precisam ser verificados no servidor. O plano de extração inclui combustível e sua unidade/tipo, inventários/subprodutos, estado/NBT relevante, temperatura/carga, requisitos, limites e side effects; schema/migração do snapshot explícitos.

Reator absorvido não pode perder combustível ou ganhar potência fictícia. O adapter deve comprovar equivalência operacional depois da incorporação, ou negar operação. Nunca estimar energia a partir de nanites, nome de item, mesh ou código decompilado. Mod desconhecido pode ficar indexado como documentação, mas não ganha capacidade VERIFIED.

Commit deve ocorrer na thread de tick, depois de verificar revisão e de preparar reserva de posse única. Não existe transação universal entre mods: se extração for irreversível ou adapter não conseguir restauração exata, abortar absorção nesta primeira estratégia e reportar indisponibilidade. `rollback()` só declara sucesso se comprovadamente restaura inventário/combustível/estado sem conflito; não promete desfazer física ou ticks ocorridos. Implementação futura requer testes reais de falha no prepare/commit e retomada pós-crash. Testes do core usam somente snapshots/fixtures, não validam funcionamento de um reator real.

## Visual e referência jevmod

Plano posterior de extração **seletiva**, não cópia do mod:
- `ObjMesh`: identificar algoritmo realmente independente, remover medidas hardcoded, parametrizar unidades/origem/escala; testes com malhas pequenas e limites de tamanho.
- `HeightClip`: preservar apenas recorte matemático reutilizável após validar licença/origem; tolerâncias, malhas degeneradas e coordenadas negativas.
- `SliceBuffer`: separar geração CPU e upload/render específico de cada loader. API atual depende de Minecraft/renderer, não presumir compatibilidade entre versões.
- Preview: UI e renderer próprios a partir de snapshots/meshes parametrizadas, não copiar `BodyVisual`/gameplay inteiro.

Nenhum desses arquivos foi copiado nesta fase. Antes de reutilizar, confirmar licença no arquivo/repositório e conservar avisos/atribuição; não presumir que um campo `mod_license=MIT` substitui auditoria. Não herdar IA, Minds, Rhino, gameplay, registries ou dependências do jevmod. Não há sistema de animação esquelética ou rede de assets fornecido por este núcleo.
