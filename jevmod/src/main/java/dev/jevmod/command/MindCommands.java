package dev.jevmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.jevmod.JevMod;
import dev.jevmod.mind.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class MindCommands {
    private MindCommands(){}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        var at=Commands.argument("pos",BlockPosArgument.blockPos())
                .then(Commands.literal("profile").then(Commands.argument("id",StringArgumentType.word()).suggests((ctx,b)->SharedSuggestionProvider.suggest(JevMod.minds().catalog().minds.keySet(),b)).executes(ctx->with(ctx,core->{core.configure(StringArgumentType.getString(ctx,"id"));return "Mente: "+core.profile();}))))
                .then(Commands.literal("systems").then(Commands.argument("ids",StringArgumentType.greedyString()).executes(ctx->with(ctx,core->{String ids=StringArgumentType.getString(ctx,"ids");core.useSystems(ids.equals("none")?List.of():Arrays.stream(ids.split("[,\\s]+")).filter(s->!s.isBlank()).toList());return "Sistemas: "+core.systems();}))))
                .then(Commands.literal("body").then(Commands.argument("entity",EntityArgument.entity()).executes(ctx->with(ctx,core->{try{var entity=EntityArgument.getEntity(ctx,"entity");if(!(entity instanceof dev.jevmod.body.GeneratedBody)||entity.level()!=core.getLevel()||entity.distanceToSqr(core.getBlockPos().getCenter())>4096)throw new IllegalArgumentException("Escolha um corpo Jev até 64 blocos");core.bindBody(entity.getUUID());return "Corpo conectado: "+entity.getUUID();}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IllegalArgumentException(e.getMessage());}}))))
                .then(Commands.literal("port").then(Commands.argument("name",StringArgumentType.word()).then(Commands.argument("target",BlockPosArgument.blockPos()).executes(ctx->with(ctx,core->{try{core.bindPort(StringArgumentType.getString(ctx,"name"),BlockPosArgument.getLoadedBlockPos(ctx,"target"));return "Porta conectada";}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IllegalArgumentException(e.getMessage());}})))))
                .then(Commands.literal("on").executes(ctx->with(ctx,core->{core.setEnabled(true);return "Mente ligada";})))
                .then(Commands.literal("off").executes(ctx->with(ctx,core->{core.setEnabled(false);return "Mente desligada";})))
                .then(Commands.literal("say").then(Commands.argument("message",StringArgumentType.greedyString()).executes(ctx->with(ctx,core->{var payload=new com.google.gson.JsonObject();payload.addProperty("text",StringArgumentType.getString(ctx,"message"));core.enqueue(new MindRuntime.Event("chat",payload));return "Mensagem enviada à Mente";}))))
                .then(Commands.literal("status").executes(ctx->with(ctx,core->"Mente "+core.profile()+" | "+core.systems()+" | Corpo "+core.bodyId()+" | Portas "+core.ports()+" | Memória "+core.memory()+" | "+core.error())));
        dispatcher.register(Commands.literal("jev").then(Commands.literal("mind").requires(source->source.hasPermission(2)).then(Commands.literal("list").executes(ctx->{ctx.getSource().sendSuccess(()->Component.literal("Mentes: "+JevMod.minds().catalog().minds.keySet()+" | Sistemas: "+JevMod.minds().catalog().systems.keySet()+" | APIs: "+MindExtensions.services()+" | Erros: "+JevMod.minds().catalog().errors),false);return 1;})).then(Commands.literal("at").then(at))));
    }
    private static int with(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,java.util.function.Function<MindCoreEntity,String> action){
        try{var pos=BlockPosArgument.getLoadedBlockPos(ctx,"pos");if(!(ctx.getSource().getLevel().getBlockEntity(pos) instanceof MindCoreEntity core))throw new IllegalArgumentException("Coloque um Núcleo da Mente nesse bloco");if(ctx.getSource().getEntity()!=null)core.claim(ctx.getSource().getEntity().getUUID());String message=action.apply(core);ctx.getSource().sendSuccess(()->Component.literal(message),false);return 1;}
        catch(Exception e){ctx.getSource().sendFailure(Component.literal(e.getMessage()));return 0;}
    }
}
