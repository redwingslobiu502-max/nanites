// Explicit chat is the trigger; no automatic paid inference on every tick.
function onEvent(ctx, event, data, config) {
    if (event === "chat") {
        ctx.memory.set("last_chat", data.text);
        ctx.ai.decide({ request: data.text, battery: ctx.memory.readSystem("energy", "charge", 0) }, {
            body_action: { type: "choice", instructions: "O que o proprietário quer que o corpo faça? Escolha keep se não houver pedido explícito.", criteria: { keep: null, halt: "parar/desligar o corpo", resume: "retomar/ligar o corpo" } }
        });
    }
    if (event === "decision" && data.system === ctx.system) {
        if (data.error) { ctx.say("Falha na decisão: " + data.error); return; }
        var answer = data.answers.body_action;
        if (answer && answer.confidence >= config.minimum_confidence) {
            // Program policy remains authoritative: AI cannot override an empty battery.
            if (answer.choice === "halt") { ctx.memory.set("requested_halt", true); ctx.body.halt(); }
            if (answer.choice === "resume") { ctx.memory.set("requested_halt", false); if (Number(ctx.memory.readSystem("energy", "charge", 0)) > 0) ctx.body.resume(); }
            ctx.memory.set("last_action", answer.choice);
            ctx.say("Decisão: " + answer.choice);
        }
    }
}
