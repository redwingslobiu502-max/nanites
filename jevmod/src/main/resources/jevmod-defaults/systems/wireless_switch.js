function onStart(ctx, config) { ctx.wireless.listen(config.channel); }
function onEvent(ctx, event, data, config) {
    if (event === "redstone") ctx.wireless.send(config.channel, { power: data.power }, config.range);
    if (event === "wireless" && data.channel === config.channel) {
        ctx.redstone.output(config.output_side, data.power);
        ctx.memory.set("last_signal", data.power);
    }
}
function onStop(ctx, config) { ctx.redstone.output(config.output_side, 0); }
