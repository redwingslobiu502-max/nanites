# JevMod — Update 2: ateliê neural e berço

Minecraft Java 1.21 / NeoForge 21.0.167. Tudo abre dentro do jogo. A edição e a geração funcionam em um mundo **single-player local**.

## Código e cérebro

Abra com **F8** (remapeável nos controles) ou `/jevui`. A interface branca/azul claro contém arquivos de `brains`, `powers`, `sensors` e `bodies`, editor JSON/JavaScript com cores, linhas, seleção, recortar/copiar/colar, desfazer e indentação. **Ctrl+S** ou **Salvar e aplicar** valida o arquivo, preserva a versão anterior em `config/jevmod/.history/` e recarrega as definições no servidor integrado. Erros de sintaxe não substituem o arquivo funcionando. Rascunhos de outros arquivos ficam na tela até salvar ou fechar; fechar com alterações pede confirmação.

O painel do cérebro mostra dois hemisférios em 3D, regiões funcionais coloridas, sensores, decisão e poderes ligados por sinapses. Arraste para girar, use a roda para zoom e o botão **Cérebro 2D/3D** para alternar. Clique nos nós para abrir o arquivo correspondente. A visualização deriva do JSON editável: representa a organização do mod; as pulsações são ilustrativas e não mostram pesos internos nem atividade real do modelo Jev.

Arquivos novos podem ser criados pelo campo de caminho, por exemplo `powers/heal.json` e `powers/heal.js`. A documentação completa da API fica em `POWERS.md`. Configuração de provedor/chaves continua no arquivo `jevmod.json`, fora do editor.

## Berço e corpo

Obtenha com `/give @s jevmod:berco` ou fabrique com este padrão: `IGI / GRG / III`, sendo I ferro, G vidro e R redstone. Deixe dois blocos de altura livres. A câmara ocupa 1×2×1 blocos e acomoda um corpo de até 1,8 bloco de altura. Clique com o botão direito em qualquer metade para configurar.

- **Humanoide / skin:** modelo clássico de jogador e skin PNG 64×64 com transparência e camada externa. Sem PNG, o mod calcula uma textura própria branca/azul.
- **Mob registrado:** ID como `minecraft:zombie` ou `outromod:criatura`. O seletor percorre entidades registradas não misc; IDs de outras entidades vivas podem ser digitados. O mod de origem precisa estar instalado e disponibilizar um renderizador. Sem PNG, usa a aparência original; PNG opcional substitui a textura.
- **Modelo OBJ:** importe um `.obj` próprio e, opcionalmente, um PNG com seus UVs. Faces poligonais e índices negativos são aceitos. O modelo é centralizado, apoiado na base e reduzido para caber na câmara.

Use **Importar PNG/OBJ** ou arraste os arquivos sobre a janela do Minecraft. Cada arquivo é copiado para `config/jevmod/assets/`, com nome SHA-256. Para importar OBJ e PNG juntos, coloque primeiro o OBJ; skins humanoides exigem 64×64. A prévia mostra o corpo 3D e a textura/UV em 2D. Arraste e use a roda para ajustar a visão.

Escolha **Tempo (s)** de 1 a 3600 e, opcionalmente, o ID de um cérebro existente. **Gerar** inicia; **Cancelar** apaga o trabalho atual; **Liberar** coloca o corpo concluído ao lado da câmara. É preciso espaço livre. A liberação consome o trabalho para evitar duplicação; não é possível mudar a receita durante a formação.

## Formação de baixo para cima

O servidor calcula `progresso = ticks decorridos / duração` e `altura = 1,8 × progresso`. Cada tick avança o trabalho; a skin é preenchida conforme a altura de cada face/linha nos UVs: pés, pernas, tronco/braços e cabeça. A renderização recorta polígonos na altura atual, interpolando coordenadas de textura e atributos. Uma lâmina azul acompanha a formação dentro do berço. O modelo aparece desde a base, sem surgir inteiro antes da conclusão.

O estado é salvo no bloco e retomado quando o mundo/chunk carrega. A tela não pausa o servidor. A duração usa ticks de jogo (20/s): lag aumenta o tempo de relógio e chunks descarregados suspendem o trabalho. No último tick, o progresso chega exatamente a 100%, a skin fica completa e a câmara aguarda liberação.

Em `config/jevmod/generated/`, cada conclusão grava a receita JSON; humanoides também gravam o PNG calculado, e OBJ grava a malha normalizada. Aparências de mobs são referências aos registros existentes: assets de Minecraft ou outros mods não são extraídos/copiados. O corpo liberado mantém sua aparência no save e pode receber poderes/decisões pelo cérebro escolhido. Use `/jev errors` para problemas nas definições.

## Limites desta versão

OBJ é uma malha estática: não importa rig/animação, MTL, FBX ou GLTF. Texturas são importadas separadamente; caminhos de materiais do OBJ são ignorados. Limites: 4 MB/arquivo, PNG até 2048×2048, 30000 vértices, 40000 triângulos. O humanoide usa braços clássicos; skin slim e formatos antigos 64×32 ainda não são suportados.

O corpo gerado é uma entidade Jev persistente com colisão de 0,6×1,8 e sem IA de movimento própria. Escolher um mob muda a aparência; não copia sua IA, estatísticas ou drops. O cérebro executa os poderes existentes no novo corpo. A update não transforma o jogador nem adiciona controle/posse, voo, novos membros ou hitboxes por espécie. Renderizadores de mods com shaders, passes especiais ou primitivas diferentes de quads/triângulos podem precisar de adaptação. Upload/edição e comandos do berço em servidor remoto ainda não estão implementados.

Nenhum gerador externo de imagens/modelos é chamado: o berço calcula a textura padrão ou monta os arquivos/modelos escolhidos durante o tempo indicado. OpenRouter continua usando `~typesafe/jev-latest` para decisões de poderes.
