"""Probe native OpenRouter Jev Decisions, or structured chat output for other models.

Usage: python tools/openrouter_probe.py <model> [<model> ...]   (needs OPENROUTER_API_KEY)
Prints the routed model, latency, the raw answer and the cost of each call.
"""
import json
import os
import sys
import time
import urllib.request

SCHEMA = {"type": "object", "additionalProperties": False,
          "required": ["laser_on", "laser_color", "laser_intensity"],
          "properties": {
              "laser_on": {"type": "object", "additionalProperties": False, "required": ["yes"],
                           "properties": {"yes": {"type": "number"}}},
              "laser_color": {"type": "object", "additionalProperties": False, "required": ["choice", "confidence"],
                              "properties": {"choice": {"type": "string", "enum": ["red", "green", "blue"]},
                                             "confidence": {"type": "number"}}},
              "laser_intensity": {"type": "object", "additionalProperties": False, "required": ["level", "confidence"],
                                  "properties": {"level": {"type": "integer", "minimum": 0, "maximum": 4},
                                                 "confidence": {"type": "number"}}}}}
SYSTEM = "You are a System One classifier. Read STATE and answer every QUESTION. Reply with JSON only."
USER = json.dumps({"state": {"nearby_hostiles": [{"type": "zombie", "distance": 4}],
                             "chat": ["liga o laser vermelho no maximo"]},
                   "questions": {"laser_on": "Should the laser be on? yes = probability from 0 to 1",
                                 "laser_color": "Which colour should the laser be?",
                                 "laser_intensity": "How strong? levels 0=off .. 4=maximum"}})


def main():
    for model in sys.argv[1:]:
        body = {"model": model, "max_tokens": 300, "temperature": 0,
                "provider": {"require_parameters": True},
                "messages": [{"role": "system", "content": SYSTEM}, {"role": "user", "content": USER}],
                "response_format": {"type": "json_schema",
                                    "json_schema": {"name": "answers", "strict": True, "schema": SCHEMA}}}
        decisions = model.startswith(("typesafe/jev-", "~typesafe/jev-")) and model not in ("typesafe/jev-router", "~typesafe/jev-router")
        if decisions:
            body = {"model": model, "state": json.loads(USER)["state"], "questions": {
                "laser_on": {"type": "noul", "instructions": "Does the host's latest chat explicitly request turning the laser on?"},
                "laser_color": {"type": "choice", "instructions": "Which laser colour does the host request?",
                                "criteria": {"red": "vermelho", "green": "verde", "blue": "azul"}},
                "laser_intensity": {"type": "score", "instructions": "How strong does the host request the laser to be?",
                                    "criteria": ["off", "low", "medium", "high", "maximum"]}}}
        endpoint = "https://openrouter.ai/api/alpha/decisions" if decisions else "https://openrouter.ai/api/v1/chat/completions"
        req = urllib.request.Request(endpoint, json.dumps(body).encode(),
                                     {"Authorization": "Bearer " + os.environ["OPENROUTER_API_KEY"],
                                      "Content-Type": "application/json"})
        t = time.time()
        try:
            r = json.load(urllib.request.urlopen(req, timeout=60))
            answer = json.dumps(r.get("answers", {})) if decisions else r["choices"][0]["message"]["content"]
            print(model, "->", r.get("model"), r.get("provider"), round(time.time() - t, 2), "s",
                  answer[:800], "cost", r.get("usage", {}).get("cost"))
        except Exception as e:  # noqa: BLE001 - a probe reports every failure and moves on
            print(model, "ERR", e, getattr(e, "read", lambda: b"")()[:300])


if __name__ == "__main__":
    main()
