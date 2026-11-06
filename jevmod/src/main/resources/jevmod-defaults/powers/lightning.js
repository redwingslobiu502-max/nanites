function run(ctx, params) {
    var hit = ctx.world.raycast(ctx.host.eyePos(), ctx.host.look(), params.range);
    ctx.vfx.beam({id: "strike", to: hit.pos, color: params.color, width: 0.2, style: "arc", duration: 10});
    ctx.world.lightning(hit.pos, true);
    ctx.vfx.ring({pos: hit.pos, color: params.color, radius: 2, duration: 10});
    if (hit.entity && hit.entity.isHostile()) hit.entity.damage(params.damage);
}
