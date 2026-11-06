package dev.jevmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.jevmod.core.Json;
import dev.jevmod.core.def.BrainDef;
import dev.jevmod.core.def.ParamDef;
import dev.jevmod.core.def.PowerDef;
import dev.jevmod.core.jev.EvalResult;
import dev.jevmod.core.jev.QuestionPlan;
import dev.jevmod.game.BrainInstance;
import dev.jevmod.game.JevRuntime;
import dev.jevmod.game.PowerState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

/**
 * {@code /jev}: everything the player needs to drive a brain by hand - attach one, force a think, flip a power,
 * override a parameter, and see what the AI actually answered.
 */
public final class JevCommands {
    private JevCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, JevRuntime runtime) {
        SuggestionProvider<CommandSourceStack> brains = (ctx, b) -> SharedSuggestionProvider.suggest(brainIds(runtime), b);
        SuggestionProvider<CommandSourceStack> powers = (ctx, b) -> SharedSuggestionProvider.suggest(runtime.defs().powers.keySet(), b);
        SuggestionProvider<CommandSourceStack> attachedPowers = (ctx, b) -> {
            BrainInstance brain = attached(ctx.getSource());
            return SharedSuggestionProvider.suggest(brain == null ? List.of() : brain.states().keySet(), b);
        };
        SuggestionProvider<CommandSourceStack> attachedParams = (ctx, b) -> {
            BrainInstance brain = attached(ctx.getSource());
            if (brain == null) {
                return SharedSuggestionProvider.suggest(List.of(), b);
            }
            String power = ctx.getArgument("power", String.class);
            PowerState s = brain.state(power);
            return SharedSuggestionProvider.suggest(s == null ? List.of() : s.definition().params.keySet(), b);
        };

        dispatcher.register(Commands.literal("jev")
                .executes(ctx -> help(ctx.getSource()))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource(), runtime)))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("errors").executes(ctx -> errors(ctx.getSource(), runtime)))
                .then(Commands.literal("provider").executes(ctx -> provider(ctx.getSource(), runtime)))
                .then(Commands.literal("reload")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> reload(ctx.getSource(), runtime)))
                .then(Commands.literal("attach")
                        .then(Commands.argument("brain", StringArgumentType.word())
                                .suggests(brains)
                                .executes(ctx -> attach(ctx.getSource(), runtime, StringArgumentType.getString(ctx, "brain")))))
                .then(Commands.literal("detach").executes(ctx -> detach(ctx.getSource(), runtime)))
                .then(Commands.literal("think").executes(ctx -> think(ctx.getSource())))
                .then(Commands.literal("last").executes(ctx -> last(ctx.getSource())))
                .then(Commands.literal("on")
                        .then(Commands.argument("power", StringArgumentType.word())
                                .suggests(attachedPowers)
                                .executes(ctx -> power(ctx.getSource(), StringArgumentType.getString(ctx, "power"), true))))
                .then(Commands.literal("off")
                        .then(Commands.argument("power", StringArgumentType.word())
                                .suggests(attachedPowers)
                                .executes(ctx -> power(ctx.getSource(), StringArgumentType.getString(ctx, "power"), false))))
                .then(Commands.literal("param")
                        .then(Commands.argument("power", StringArgumentType.word())
                                .suggests(attachedPowers)
                                .then(Commands.argument("param", StringArgumentType.word())
                                        .suggests(attachedParams)
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> param(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "power"),
                                                        StringArgumentType.getString(ctx, "param"),
                                                        StringArgumentType.getString(ctx, "value")))))))
                .then(Commands.literal("say")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    ServerPlayer player = src.getPlayer();
                                    if (player == null) {
                                        return err(src, "run this as a player");
                                    }
                                    String text = StringArgumentType.getString(ctx, "text");
                                    runtime.injectChat(player, text);
                                    return ok(src, "chat injected: " + text);
                                })))
                .then(Commands.literal("demo").executes(ctx -> demo(ctx.getSource(), runtime))));
    }

    // ---- handlers ----------------------------------------------------------------------------------------------

    private static int help(CommandSourceStack src) {
        return ok(src, """
                jevmod - neural sandbox
                  /jev list              brains and powers in config/jevmod
                  /jev attach <brain>    give a brain to yourself
                  /jev detach            remove it
                  /jev status            what the brain is doing
                  /jev think             force one think now
                  /jev on|off <power>    flip a power by hand
                  /jev param <power> <param> <value>
                  /jev say <text>        inject a chat line (the brain treats it as yours)
                  /jev last              the last questions and answers
                  /jev errors            broken definition files
                  /jev provider          which AI is answering
                  /jev reload            re-read config/jevmod
                  /jev demo              attach the demo brain and think once""");
    }

    private static int list(CommandSourceStack src, JevRuntime runtime) {
        StringBuilder sb = new StringBuilder("brains:");
        for (BrainDef b : runtime.defs().brains.values()) {
            sb.append("\n  ").append(b.id).append(" - ").append(b.name)
                    .append("  powers=").append(runtime.defs().powersOf(b).size())
                    .append(", every ").append(b.thinkEveryTicks).append(" ticks");
        }
        sb.append("\npowers:");
        for (PowerDef p : runtime.defs().powers.values()) {
            sb.append("\n  ").append(p.id).append(" - ").append(p.name)
                    .append(" [").append(p.kind.name().toLowerCase(java.util.Locale.ROOT)).append("]")
                    .append(p.params.isEmpty() ? "" : " params=" + p.params.keySet());
        }
        return ok(src, sb.toString());
    }

    private static int status(CommandSourceStack src) {
        BrainInstance brain = attached(src);
        if (brain == null) {
            return err(src, "no brain attached; try /jev attach jarvis");
        }
        StringBuilder sb = new StringBuilder("brain '").append(brain.brain().id).append("' on ")
                .append(brain.host().getName().getString());
        sb.append("\n  provider: ").append(brain.providerName() == null ? "not resolved yet" : brain.providerName());
        sb.append("\n  thinks: ").append(brain.thinkCount());
        EvalResult last = brain.lastResult();
        if (last != null) {
            sb.append("\n  last call: ").append(last.model).append(", ").append(last.latencyMs).append(" ms, ")
                    .append(last.inputTokens).append(" input tokens");
        }
        if (brain.lastError() != null) {
            sb.append("\n  last error: ").append(brain.lastError());
        }
        sb.append("\npowers:");
        for (PowerState s : brain.states().values()) {
            sb.append("\n  ").append(s.definition().id).append(": ")
                    .append(s.broken() ? "BROKEN" : s.active() ? "on" : "off")
                    .append("  params=").append(s.params());
        }
        return ok(src, sb.toString());
    }

    private static int errors(CommandSourceStack src, JevRuntime runtime) {
        if (runtime.defs().errors.isEmpty()) {
            return ok(src, "no definition errors");
        }
        return ok(src, "definition errors:\n  " + String.join("\n  ", runtime.defs().errors));
    }

    private static int provider(CommandSourceStack src, JevRuntime runtime) {
        String name = runtime.config().provider;
        String model = Json.str(Json.obj(runtime.config().raw, name), "model", "(unknown)");
        String keyEnv = Json.str(Json.obj(runtime.config().raw, name), "api_key_env", "(none)");
        boolean hasKey = System.getenv(keyEnv) != null && !System.getenv(keyEnv).isBlank();
        return ok(src, "provider: " + name + "\nmodel: " + model + "\nkey: " + keyEnv
                + (hasKey ? " (set)" : " (NOT SET - the brain cannot think)"));
    }

    private static int reload(CommandSourceStack src, JevRuntime runtime) {
        List<String> problems = runtime.reload();
        if (problems.isEmpty()) {
            return ok(src, "reloaded: " + runtime.defs().brains.size() + " brain(s), "
                    + runtime.defs().powers.size() + " power(s), no problems");
        }
        return ok(src, "reloaded with " + problems.size() + " problem(s):\n  " + String.join("\n  ", problems));
    }

    private static int attach(CommandSourceStack src, JevRuntime runtime, String id) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            return err(src, "run this as a player");
        }
        BrainDef def = runtime.defs().brains.get(id);
        if (def == null) {
            return err(src, "unknown brain '" + id + "'; try /jev list");
        }
        runtime.attach(player, def);
        return ok(src, "attached brain '" + id + "' (" + runtime.defs().powersOf(def).size() + " powers). "
                + "It thinks every " + def.thinkEveryTicks + " ticks; force one with /jev think");
    }

    private static int detach(CommandSourceStack src, JevRuntime runtime) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            return err(src, "run this as a player");
        }
        return runtime.detach(player) ? ok(src, "brain detached") : err(src, "no brain attached");
    }

    private static int think(CommandSourceStack src) {
        BrainInstance brain = attached(src);
        if (brain == null) {
            return err(src, "no brain attached; try /jev attach jarvis");
        }
        brain.think();
        return ok(src, "asked '" + brain.brain().id + "' to think");
    }

    private static int last(CommandSourceStack src) {
        BrainInstance brain = attached(src);
        if (brain == null) {
            return err(src, "no brain attached");
        }
        if (brain.lastResult() == null) {
            return err(src, "no answer yet");
        }
        StringBuilder sb = new StringBuilder("answers:");
        for (Map.Entry<String, dev.jevmod.core.jev.Answer> e : brain.lastResult().answers.entrySet()) {
            sb.append("\n  ").append(e.getKey()).append(" -> ").append(e.getValue().toJson());
        }
        sb.append("\ndecisions:");
        for (QuestionPlan.Decision d : brain.lastDecisions()) {
            sb.append("\n  ").append(d.summary());
        }
        sb.append("\nfull request/response: config/jevmod/last_think.json");
        return ok(src, sb.toString());
    }

    private static int power(CommandSourceStack src, String powerId, boolean on) {
        BrainInstance brain = attached(src);
        if (brain == null) {
            return err(src, "no brain attached");
        }
        String error = brain.setPower(powerId, on);
        return error == null ? ok(src, powerId + (on ? " on" : " off")) : err(src, error);
    }

    private static int param(CommandSourceStack src, String powerId, String paramId, String value) {
        BrainInstance brain = attached(src);
        if (brain == null) {
            return err(src, "no brain attached");
        }
        PowerState s = brain.state(powerId);
        if (s == null) {
            return err(src, "unknown power '" + powerId + "'");
        }
        ParamDef def = s.definition().params.get(paramId);
        if (def == null) {
            return err(src, "power '" + powerId + "' has no parameter '" + paramId + "'");
        }
        String error = brain.setParam(powerId, paramId, value);
        return error == null
                ? ok(src, powerId + "." + paramId + " = " + s.params().get(paramId))
                : err(src, error + "\n" + describe(def));
    }

    private static int demo(CommandSourceStack src, JevRuntime runtime) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            return err(src, "run this as a player");
        }
        BrainDef def = runtime.defs().brains.get("jarvis");
        if (def == null) {
            def = runtime.defs().brains.values().stream().findFirst().orElse(null);
        }
        if (def == null) {
            return err(src, "no brains loaded; check /jev errors");
        }
        BrainInstance brain = runtime.attach(player, def);
        brain.say("online. powers: " + brain.states().keySet()
                + ". Type in chat, or use /jev on laser, /jev think, /jev last.");
        brain.think();
        return ok(src, "demo running with brain '" + def.id + "'");
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private static BrainInstance attached(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        return player == null ? null : dev.jevmod.JevMod.runtime().brain(player);
    }

    private static List<String> brainIds(JevRuntime runtime) {
        return List.copyOf(runtime.defs().brains.keySet());
    }

    private static String describe(ParamDef def) {
        return switch (def.type) {
            case BOOL -> "expects true or false";
            case CHOICE -> "expects one of " + def.options.keySet();
            default -> "expects a number in " + def.min + ".." + def.max;
        };
    }

    private static int ok(CommandSourceStack src, String message) {
        src.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int err(CommandSourceStack src, String message) {
        src.sendFailure(Component.literal(message));
        return 0;
    }
}
