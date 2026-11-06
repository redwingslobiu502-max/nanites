function onTick(ctx, params) {
    ctx.vfx.sphere({id: "shell", color: params.color, radius: params.radius, duration: 3});
    if (ctx.tick % 10 === 0) ctx.host.effect("minecraft:resistance", 0.6, params.strength);
}
function onStop(ctx, params) {
    ctx.vfx.stop("shell");
}
