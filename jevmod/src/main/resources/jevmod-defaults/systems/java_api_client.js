function onTick(ctx) {
    ctx.memory.set("answer", ctx.call("imagination_example:math", "double", { value: 21 }));
}
