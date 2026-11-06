function run(ctx, params) {
    ctx.host.push(ctx.host.look().scale(params.speed));
    ctx.vfx.ring({pos: ctx.host.pos(), color: params.color, radius: 3, duration: 12});
    ctx.world.sound("minecraft:entity.enderman.teleport", ctx.host.pos(), 0.5, 1.5);
}
