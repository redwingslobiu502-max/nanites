function onTick(ctx, params) {
    var hit = ctx.world.raycast(ctx.host.eyePos(), ctx.host.look(), params.range);
    ctx.vfx.beam({id: "main", to: hit.pos, color: params.color, width: params.width, duration: 3});
    if (hit.entity && hit.entity.isHostile() && ctx.tick % 10 === 0) {
        hit.entity.damage(params.damage);
    }
}
function onStop(ctx, params) {
    ctx.vfx.stop("main");
}
