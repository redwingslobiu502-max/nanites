# Editable JevMod content

See [POWERS.md](POWERS.md) for the full Portuguese power guide, API reference and body/transformation limits.

Brains live in brains/*.json; powers are powers/*.json plus their JavaScript file; sensors live in sensors/*.js.
Use /jev reload after editing, /jev errors for validation and /jev last to inspect the AI decisions.
Existing files are preserved on startup; missing bundled examples are recreated.

Toggle hooks: onStart(ctx, params), onTick(ctx, params), onChange(ctx, params, changed, old), onStop(ctx, params).
Instant hook: run(ctx, params). Script sensors define sense() and can access global ctx.
Parameters support bool, choice and range. Set ai=false on a parameter to keep it under manual control.
Powers can use ctx.host, ctx.world and ctx.vfx. Scripts have an instruction budget and no general Java access.

API keys belong in environment variables, never in these files. The global provider is in jevmod.json.
The default is openrouter with ~typesafe/jev-latest and OPENROUTER_API_KEY, through OpenRouter's Decisions API. For an older Gemini configuration, keep provider openrouter, change openrouter.model to ~typesafe/jev-latest, check per-brain overrides and run /jev reload; the mod preserves existing configuration files.
The default examples target hostile mobs only. Shield resistance lasts up to 0.6 seconds after it stops.

Update 2: F8 ou `/jevui` abre o ateliê dentro do Minecraft. `/give @s jevmod:berco` fornece a câmara de corpo. Leia `UPDATE2.md` para modelos de mobs, PNG/OBJ, importação e formação temporizada.

Update 3 / Imagination: `/give @s jevmod:mind_core` fornece o Núcleo da Mente independente. Abra com botão direito para escolher jarvis/friday/ultron, sistemas, UUID do corpo e portas de blocos. Leia [UPDATE3.md](UPDATE3.md) para energia, reator, defesa com pepitas, redstone/wireless e APIs próprias. Personalidades ficam em `minds/`, sistemas em `systems/` e bibliotecas JS em `apis/`; edite pelo ateliê e aplique com `/jev reload`. Addons Java podem acrescentar módulos, APIs e engines. `@jarvis pare o corpo` envia chat ao núcleo próprio mais próximo; o exemplo assistant precisa da chave OpenRouter.

Update 0.3.5: leia [UPDATE035.md](UPDATE035.md) para controle do Berço pela Mente, consultas do corpo, dano/morte persistidos, resistências/metadados, radar remoto, exclusão com histórico, enabled:false e importação JSON/JS. O exemplo body_factory vem desativado e demonstra montagem/adaptação programável.
