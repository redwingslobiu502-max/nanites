// Custom player-defined API. Edit this file and reload: no Java build needed.
var engineering = {
    charge: function(ctx, amount, capacity) {
        var value = Number(ctx.memory.readSystem("energy", "charge", 0));
        ctx.memory.writeSystem("energy", "charge", Math.max(0, Math.min(capacity, value + amount)));
    },
    battery: function(ctx, initial, capacity) {
        return Math.max(0, Math.min(capacity, Number(ctx.memory.get("charge", initial))));
    }
};
