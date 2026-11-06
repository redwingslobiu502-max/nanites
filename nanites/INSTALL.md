# Instalar Nanites 0.2.0 — primeira fatia Minecraft

Use instâncias separadas. **Nunca coloque os dois JARs juntos**. Faça backup de mundos antes do teste. Não instalamos nada no seu launcher/profile e não iniciamos Minecraft.

| Instância | Java | Loader/dependências | Arquivo |
|---|---|---|---|
| Minecraft 1.20.1 | 17 | Forge 47.4.28 | `dist/nanites-forge-1.20.1-0.2.0.jar` |
| Minecraft 1.21.1 | 21 | Fabric Loader 0.16.14 + Fabric API 0.116.17+1.21.1 | `dist/nanites-fabric-1.21.1-0.2.0.jar` |

1. Instale o loader correspondente pelo instalador oficial e configure o Java da instância. Fabric API é obrigatório no Fabric; **não é embutido**. Fontes: https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html e https://fabricmc.net/use/installer/ ; Maven oficial API: https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.116.17+1.21.1/ .
2. Com o jogo fechado, copie apenas o JAR correspondente de `dist` para a pasta `mods` dessa instância. Em multiplayer, instale no servidor e nos clientes. Não copie `core.jar`, Gson ou o `-dev.jar`.
3. Inicie manualmente um mundo novo para testes. Os arquivos de configuração são criados ao iniciar o servidor integrado/dedicado. Siga [TESTE_NO_JOGO.md](TESTE_NO_JOGO.md).
4. Compare SHA256 com `dist/VALIDATION.txt`. A auditoria confere descriptors, classes core, recursos, bytecode Java17/21 e nomes reobf/remap; **não é prova de execução em jogo**.

## Compilar novamente

PowerShell na pasta do projeto (JDKs já fornecidos; sem instalação global):

```powershell
cd 'D:\Downloads\nanobots mod\nanites'
# Apenas na primeira preparação, se faltar gradle/wrapper/gradle-wrapper.properties:
powershell -NoProfile -ExecutionPolicy Bypass -File tools\bootstrap.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1
```

`build.ps1` executa Forge/Java17 e depois Fabric/Java21 **sequencialmente** (compartilham outputs core/shared). Logs com timestamp em `validation-logs`. Só publica `dist` após ambos passarem e a auditoria aceitar os arquivos. Não executa `runClient`/`runServer`. Nas outras máquinas ajuste `org.gradle.java.installations.paths` e passe `-Java17`/`-Java21`. Gradle 8.8 checksum oficial; ForgeGradle 6.0.54; Loom 1.7.4; versões fixadas. `jevmod` forneceu somente o wrapper JAR de infraestrutura, não código do mod. Recursos/artwork originais. Dependências via HTTPS oficial/Maven.

## Configuração confiável do operador

`config/nanites/server.properties` da **instância/servidor**, não a pasta deste projeto. Alterações exigem restart do servidor.

```properties
ai.model=REQUIRED
ai.address=https://127.0.0.1:8081/v1
ai.token=
max.players=128
materials.insulating=minecraft:white_wool
# Opt-in opcional; use / e caminho absoluto canônico no Windows:
# decompiler.jar=D:/ferramentas/vineflower.jar
# decompiler.version=versao-real-do-jar
# decompiler.allow-mods=nanites
```

- `ai.model`: substitua REQUIRED pelo **nome exato** servido pelo seu LLM. REQUIRED impede requisições. Endpoint tem de permanecer exato. TLS/certificado/hostname verificados, sem redirects/proxy; token opcional explicitamente digitado pelo operador, sem procura por segredos. Para certificado local, configure um truststore JVM válido com SAN para 127.0.0.1; não desative TLS. O servidor LLM real não foi consultado nesta entrega.
- IA: até 2 jogadores simultâneos, 6 turns/16 chamadas, 30.000 tokens reservados, resposta até 128KiB; propostas são dados. `/nanites proposal 0` mostra o código; `/nanites apply` é aprovação **do próprio dono**, revalidada no estado atual. BaseRevision é exibida e conferida ao receber, preparar e aplicar a proposta; logout/relogin, dano, saldo ou hot-swap podem torná-la obsoleta. Scripts tick que realmente alteram estado também invalidam jobs; fique parado com o padrão ao solicitar IA. Não é replay de alterações do mundo. Nenhuma ferramenta executa Java/instala adaptadores/recarrega código.
- `max.players`: 1..128 sessões residentes simultâneas; saída normal libera a vaga e relog recupera o estado mais recente. Valor inválido usa128; valores fora da faixa são limitados. Scripts 64KiB; 4 eventos/32 ações core, no máximo 8 world actions por evento. Fuel core 16.384; VFX até 24 partículas/jogador/20ticks e 256/tick global. Estes limites não são benchmark de TPS.
- Mapa de material é uma decisão **explícita** do operador. Absorção consome 1 item registrado da mão, registra dono/revisão/evidência no mundo (máximo16). Mudar o mapa invalida evidência antiga diferente. Não certifica condutividade física nem compatibilidade de mods por chute.
- Decompiler: só JAR de mod **realmente listado pelo loader**, modId explicitamente permitido, uma classe solicitada pelo dono. O operador instala/configura manualmente ferramenta confiável (Vineflower); jogador/IA não escolhe executável, caminhos ou downloads. Exige jogador dono autenticado **e OP nível2**, além da allowlist configurada; OP sozinho não aprova modId. Worker usa o runner real do core e timeout45s; não decompila todo o pack. Resultado só recebe autoridade se dono ainda online, geração e revisão forem as mesmas; mudar código/saldo/regras durante o job descarta o resultado. Fontes previamente aprovadas continuam dados, não certificação. `/nanites decompile modId pacote/Classe.class`; depois `/nanites source list modId` e `/nanites source read modId pacote/Classe.java 0`. Próximo offset vem na resposta. Cache limitado64MiB, index por dono e chave SHA256/loader/MC/versões; dados não confiáveis, sem status VERIFIED. Execução de Vineflower configurado não foi testada no Minecraft aqui.

## Saves e limites de atomicidade

Estado: `<mundo>/nanites/<UUID>.snapshot.json`; material: `<mundo>/nanites/materials/<UUID>.json`; scripts: `config/nanites/scripts/<UUID>/<nome>.nano`; fontes/index sob `config/nanites`. Snapshots atômicos individuais versionados, revisão/dono/valores/definições reais validados. A cada100ticks e em comandos/save/logout/deposit é enfileirada uma cópia isolada; um worker único faz IO e coalesce por dono (até128 pendentes), sem parsing/gravação no tick. `/nanites save` confirma agendamento, não durabilidade imediata. Stop normal aguarda flush até10s e reporta erro se não salvar. Login carrega snapshot/material uma vez; relog usa a cópia pendente mais recente. Material é gravado no comando absorb; não é relido em cada dano.

O depósito e gasto têm commit autoritativo na thread do servidor e validação antes de alterar o saldo. **Não existe transação durável cross-Minecraft** entre playerdata, crafting, snapshot externo e efeitos. Crash, disco cheio, restauração seletiva de backup ou outro mod interferindo podem perder/desalinhar estado; não prometemos recuperação universal nem outbox durável. Desligue normalmente e faça backup conjunto. Rollback troca só código da sessão; não reembolsa recursos nem rebobina mundo. Reembolso/retirada portátil não implementados. O item reservatório é acesso ao saldo por dono no mundo, **não transporte de conteúdo por NBT**.
