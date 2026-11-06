# Sua própria API e sistemas em Java

Este é um addon NeoForge separado. O JevMod fornece as interfaces, sem conhecer a implementação do addon.

No diretório pai (`examples/jevmod`), execute `dev.cmd extensionExampleJar`. O resultado é `build/libs/imagination-example-0.1.0.jar`; use com `jevmod-0.3.5.jar`, Minecraft Java 1.21 e NeoForge 21.0.167 em seu perfil offline. A execução de desenvolvimento já inclui o source set do exemplo como um segundo mod, permitindo depurar e rodar os GameTests sem copiar JARs.

O construtor de `ImaginationExample.java` registra uma API `imagination_example:math` e um módulo `imagination_example:counter`. `systems/java_counter.json` seleciona esse módulo. `java_api_client` chama a API em JavaScript; `java_api_rules` faz a mesma chamada em JSON e salva o retorno. Ative os três no Núcleo para observar o contador e respostas 42 na memória.

Para criar seu addon, troque o código, namespace e metadata `META-INF/neoforge.mods.toml`; ajuste também o nome em `neoForge.mods` no `build.gradle` do projeto pai se alterar o modId. Você pode usar o mesmo build ou um projeto NeoForge independente com o JAR do JevMod como dependência de compilação.

Contratos públicos:

```java
MindExtensions.registerModule("meumod:reator", definition -> new MyReactor(definition.config()));
MindExtensions.registerService("meumod:energia", (ctx, operation, args) -> {
    // Use ctx.level(), ctx.core(), registries, entities and other mods' capabilities.
    // Return a Gson JsonElement; the bridge exposes it as normal JS/JSON values.
    return new JsonPrimitive(42);
});
MindExtensions.registerEngine("meumod:minha_linguagem", definition -> new MyInterpreter(definition));
```

Um `MindModule` implementa `start`, `tick`, `event`, `stop`; todos recebem `MindContext`. A periodicidade e configuração vêm de `SystemDef`. Java recebe acesso nativo ao jogo. JSON/JS chamam serviços registrados com `ctx.call(namespace, operation, args)`; bibliotecas JavaScript em `apis/` também podem criar uma API sem addon. Bibliotecas externas para uma engine própria devem ser empacotadas e carregadas pelo seu addon conforme as regras do NeoForge.
