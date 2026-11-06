package dev.nanites.core;

import java.util.*;

/** Nanite Script 1: data-only AST; no reflection, Java callbacks, IO or ambient authority. */
public final class ScriptLanguage {
    private ScriptLanguage() {}
    public static final int MAX_SOURCE = 65536, MAX_NODES = 4096, MAX_DEPTH = 32;
    public record Site(String file, int line) {
        IllegalArgumentException error(String text) { return new IllegalArgumentException(file + ":" + line + ": " + text); }
    }
    public record Budget(int limit) {
        public Budget { if (limit < 1 || limit > 65536) throw new IllegalArgumentException("Invalid budget"); }
    }
    static final class Fuel {
        int left;
        Fuel(Budget budget) { left = budget.limit(); }
        void use(Site site) { if (--left < 0) throw site.error("evaluation budget exhausted"); }
    }
    public sealed interface Expr permits Number, Variable, Binary, Call { Site site(); }
    public record Number(long value, Site site) implements Expr {}
    public record Variable(String name, Site site) implements Expr {}
    public record Binary(String op, Expr left, Expr right, Site site) implements Expr {}
    public record Call(String name, List<Expr> args, Site site) implements Expr {
        public Call { args = List.copyOf(args); }
    }
    public sealed interface Statement permits Add, Move, Emit, If, Repeat { Site site(); }
    public record Add(String field, Expr value, Site site) implements Statement {}
    public record Move(String from, String to, Expr value, Site site) implements Statement {}
    public record Emit(String capability, String target, Expr value, Site site) implements Statement {}
    public record If(Expr condition, List<Statement> yes, List<Statement> no, Site site) implements Statement {
        public If { yes = List.copyOf(yes); no = List.copyOf(no); }
    }
    public record Repeat(Expr count, List<Statement> body, Site site) implements Statement {
        public Repeat { body = List.copyOf(body); }
    }
    public record Function(List<String> parameters, Expr body) {
        public Function { parameters = List.copyOf(parameters); }
    }
    public record Handler(String event, String group, List<Statement> body, Site site) {
        public Handler { body = List.copyOf(body); }
    }
    public record Module(String file, String source, String id, RuleEngine.Schema schema,
                         Map<String, Function> functions, List<Handler> handlers) {
        public Module { functions = Map.copyOf(functions); handlers = List.copyOf(handlers); }
        public RuleEngine.Program program() {
            return new RuleEngine.Program(id, schema, handlers.stream().map(h ->
                    new RuleEngine.Rule(h.event(), h.group(), List.of(new RuleEngine.ScriptAction(this, h)))).toList());
        }
    }
    public static Module compile(String file, String source) { return new Parser(file, source).parse(); }
    static List<RuleEngine.Action> evaluate(Module module, Handler handler, Map<String, Long> state,
                                            Map<String, Long> event, Fuel fuel) {
        List<RuleEngine.Action> output = new ArrayList<>();
        Map<String, Long> localState = new HashMap<>(state);
        run(module, handler.body(), localState, event, fuel, output, 0);
        return List.copyOf(output);
    }
    private static void run(Module m, List<Statement> body, Map<String, Long> state, Map<String, Long> event,
                            Fuel fuel, List<RuleEngine.Action> out, int depth) {
        for (Statement s : body) {
            fuel.use(s.site()); if (depth > MAX_DEPTH) throw s.site().error("nesting exceeded");
            if (s instanceof Add a) {
                long delta = value(m, a.value(), state, event, Map.of(), fuel, 0);
                long next = Math.addExact(state.get(a.field()), delta); Ledger.nonnegative(next);
                state.put(a.field(), next); out.add(new RuleEngine.AddState(a.field(), delta));
            } else if (s instanceof Move a) out.add(new RuleEngine.Transfer(a.from(), a.to(), value(m, a.value(), state, event, Map.of(), fuel, 0)));
            else if (s instanceof Emit a) out.add(new RuleEngine.WorldAction(a.capability(), a.target(), value(m, a.value(), state, event, Map.of(), fuel, 0)));
            else if (s instanceof If a) run(m, value(m, a.condition(), state, event, Map.of(), fuel, 0) != 0 ? a.yes() : a.no(), state, event, fuel, out, depth + 1);
            else if (s instanceof Repeat a) {
                long n = value(m, a.count(), state, event, Map.of(), fuel, 0);
                if (n < 0 || n > 256) throw a.site().error("repeat count must be 0..256");
                for (long i = 0; i < n; i++) run(m, a.body(), state, event, fuel, out, depth + 1);
            }
            if (out.size() > 4096) throw s.site().error("action output exceeded");
        }
    }
    private static long value(Module m, Expr e, Map<String, Long> state, Map<String, Long> event,
                              Map<String, Long> args, Fuel fuel, int depth) {
        fuel.use(e.site()); if (depth > MAX_DEPTH) throw e.site().error("expression/call depth exceeded");
        try {
            if (e instanceof Number n) return n.value();
            if (e instanceof Variable v) {
                Long n = v.name().startsWith("state.") ? state.get(v.name().substring(6)) :
                        v.name().startsWith("event.") ? event.get(v.name().substring(6)) : args.get(v.name());
                if (n == null) throw e.site().error("missing value " + v.name()); return n;
            }
            if (e instanceof Call c) {
                Function f = m.functions().get(c.name());
                Map<String, Long> next = new HashMap<>();
                for (int i = 0; i < c.args().size(); i++) next.put(f.parameters().get(i), value(m, c.args().get(i), state, event, args, fuel, depth + 1));
                return value(m, f.body(), state, event, next, fuel, depth + 1);
            }
            Binary b = (Binary)e;
            long x = value(m, b.left(), state, event, args, fuel, depth + 1);
            if (b.op().equals("&&") && x == 0) return 0;
            if (b.op().equals("||") && x != 0) return 1;
            long y = value(m, b.right(), state, event, args, fuel, depth + 1);
            return switch (b.op()) {
                case "+" -> Math.addExact(x, y); case "-" -> Math.subtractExact(x, y); case "*" -> Math.multiplyExact(x, y);
                case "/" -> { if (x == Long.MIN_VALUE && y == -1) throw new ArithmeticException("division overflow"); yield x / y; }
                case "%" -> x % y; case ">" -> x > y ? 1 : 0; case "<" -> x < y ? 1 : 0;
                case ">=" -> x >= y ? 1 : 0; case "<=" -> x <= y ? 1 : 0; case "==" -> x == y ? 1 : 0;
                case "!=" -> x != y ? 1 : 0; case "&&", "||" -> y != 0 ? 1 : 0;
                default -> throw e.site().error("unknown operator");
            };
        } catch (ArithmeticException ex) { throw e.site().error("checked arithmetic: " + ex.getMessage()); }
    }
    static void validate(Module m, Handler h, Ledger ledger, Map<String, RuleEngine.WorldSpec> world) {
        validateBody(m, h.body(), ledger, world);
    }
    private static void validateBody(Module m, List<Statement> body, Ledger ledger, Map<String, RuleEngine.WorldSpec> world) {
        for (Statement s : body) {
            if (s instanceof Add a && !m.schema().defaults().containsKey(a.field())) throw s.site().error("unknown state");
            if (s instanceof Move a) { ledger.reservoir(a.from()); ledger.reservoir(a.to()); }
            if (s instanceof Emit a) {
                RuleEngine.WorldSpec spec = world.get(a.capability());
                if (spec == null || !spec.targets().contains(a.target())) throw s.site().error("world capability/target not approved");
            }
            if (s instanceof If a) { validateBody(m, a.yes(), ledger, world); validateBody(m, a.no(), ledger, world); }
            if (s instanceof Repeat a) validateBody(m, a.body(), ledger, world);
        }
    }
    private record Token(String text, Site site) {}
    private static final class Parser {
        final String file, source;
        final List<Token> tokens = new ArrayList<>(); int pos, nodes, nesting;
        final Map<String, Long> defaults = new LinkedHashMap<>();
        final Map<String, Function> functions = new LinkedHashMap<>();
        Parser(String file, String source) {
            this.file = Objects.requireNonNull(file); this.source = Objects.requireNonNull(source);
            if (file.length() > 256 || source.length() > MAX_SOURCE) throw new IllegalArgumentException("source size exceeded");
            int line = 1;
            for (int i = 0; i < source.length();) {
                char c = source.charAt(i);
                if (c == '\n') { line++; i++; continue; }
                if (Character.isWhitespace(c)) { i++; continue; }
                if (c == '#') { while (i < source.length() && source.charAt(i) != '\n') i++; continue; }
                int start = i; Site site = new Site(file, line);
                if (Character.isLetter(c) || c == '_') {
                    while (i < source.length() && (Character.isLetterOrDigit(source.charAt(i)) || "_.:".indexOf(source.charAt(i)) >= 0)) i++;
                } else if (Character.isDigit(c)) { while (i < source.length() && Character.isDigit(source.charAt(i))) i++; }
                else {
                    i++;
                    if (i < source.length() && Set.of("==", "!=", ">=", "<=", "&&", "||").contains(source.substring(start, i + 1))) i++;
                    else if ("{}();,=+-*/%<>".indexOf(c) < 0) throw site.error("unexpected character " + c);
                }
                tokens.add(new Token(source.substring(start, i), site));
                if (tokens.size() > 16384) throw site.error("token budget exceeded");
            }
            tokens.add(new Token("EOF", new Site(file, line)));
        }
        Token peek() { return tokens.get(pos); }
        boolean take(String s) { if (!peek().text().equals(s)) return false; pos++; return true; }
        void need(String s) { if (!take(s)) throw peek().site().error("expected " + s + ", got " + peek().text()); }
        String id() { Token t = peek(); if (!t.text().matches("[a-zA-Z_][a-zA-Z0-9_.:]{0,95}") || t.text().equals("EOF")) throw t.site().error("expected identifier"); pos++; return t.text(); }
        void node() { if (++nodes > MAX_NODES) throw peek().site().error("AST budget exceeded"); }
        Module parse() {
            need("program"); String id = id(); need("version"); String version = id(); need(";");
            while (take("state")) {
                String name = id(); need("="); Token t = peek(); pos++;
                long n; try { n = Long.parseLong(t.text()); Ledger.nonnegative(n); } catch (RuntimeException e) { throw t.site().error("invalid state default"); }
                if (defaults.putIfAbsent(name, n) != null) throw t.site().error("duplicate state"); need(";");
            }
            while (take("fn")) {
                String name = id(); need("("); List<String> args = new ArrayList<>();
                if (!take(")")) { do { String a = id(); if (args.contains(a) || args.size() >= 16) throw peek().site().error("function arguments"); args.add(a); } while (take(",")); need(")"); }
                need("="); Expr e = expr(0); need(";");
                if (functions.size() >= 64 || functions.putIfAbsent(name, new Function(args, e)) != null) throw e.site().error("duplicate/too many functions");
            }
            List<Handler> handlers = new ArrayList<>();
            while (take("on")) { Site site = peek().site(); String event = id(); need("group"); String group = id(); handlers.add(new Handler(event, group, block(), site)); }
            need("EOF"); if (pos != tokens.size()) throw tokens.get(pos - 1).site().error("trailing source");
            Module m = new Module(file, source, id, new RuleEngine.Schema(version, defaults), functions, handlers);
            for (Function f : functions.values()) checkExpr(f.body(), new HashSet<>(f.parameters()));
            for (Handler h : handlers) checkBody(h.body());
            return m;
        }
        void checkExpr(Expr e, Set<String> parameters) { checkExpr(e, parameters, 0); }
        void checkExpr(Expr e, Set<String> parameters, int depth) {
            if (depth > MAX_DEPTH) throw e.site().error("AST expression depth exceeded");
            if (e instanceof Variable v && !(parameters.contains(v.name()) || v.name().startsWith("event.") ||
                    v.name().startsWith("state.") && defaults.containsKey(v.name().substring(6)))) throw e.site().error("unknown variable");
            if (e instanceof Call c) {
                Function f = functions.get(c.name());
                if (f == null || f.parameters().size() != c.args().size()) throw e.site().error("unknown function/arity");
                c.args().forEach(a -> checkExpr(a, parameters, depth + 1));
            }
            if (e instanceof Binary b) { checkExpr(b.left(), parameters, depth + 1); checkExpr(b.right(), parameters, depth + 1); }
        }
        void checkBody(List<Statement> body) {
            for (Statement s : body) {
                if (s instanceof Add a) checkExpr(a.value(), Set.of());
                if (s instanceof Move a) checkExpr(a.value(), Set.of());
                if (s instanceof Emit a) checkExpr(a.value(), Set.of());
                if (s instanceof If a) { checkExpr(a.condition(), Set.of()); checkBody(a.yes()); checkBody(a.no()); }
                if (s instanceof Repeat a) { checkExpr(a.count(), Set.of()); checkBody(a.body()); }
            }
        }
        List<Statement> block() {
            if (++nesting > MAX_DEPTH) throw peek().site().error("nesting exceeded");
            need("{"); List<Statement> body = new ArrayList<>();
            while (!take("}")) {
                node(); Token t = peek(); pos++;
                switch (t.text()) {
                    case "add" -> { String f = id(); need("="); Expr e = expr(0); need(";"); body.add(new Add(f, e, t.site())); }
                    case "transfer" -> { String from = id(), to = id(); need("="); Expr e = expr(0); need(";"); body.add(new Move(from, to, e, t.site())); }
                    case "emit" -> { String cap = id(), target = id(); need("="); Expr e = expr(0); need(";"); body.add(new Emit(cap, target, e, t.site())); }
                    case "if" -> { need("("); Expr e = expr(0); need(")"); List<Statement> yes = block(), no = take("else") ? block() : List.of(); body.add(new If(e, yes, no, t.site())); }
                    case "repeat" -> { need("("); Expr e = expr(0); need(")"); body.add(new Repeat(e, block(), t.site())); }
                    default -> throw t.site().error("unknown statement " + t.text());
                }
            }
            nesting--; return body;
        }
        int precedence(String s) { return switch(s) { case "||" -> 1; case "&&" -> 2; case "==", "!=" -> 3; case ">", "<", ">=", "<=" -> 4; case "+", "-" -> 5; case "*", "/", "%" -> 6; default -> -1; }; }
        Expr expr(int min) {
            if (++nesting > MAX_DEPTH) throw peek().site().error("expression nesting exceeded");
            node(); Token t = peek(); Expr left;
            if (take("-")) left = new Binary("-", new Number(0, t.site()), expr(7), t.site());
            else if (take("(")) { left = expr(0); need(")"); }
            else if (t.text().matches("[0-9]+")) { pos++; try { left = new Number(Long.parseLong(t.text()), t.site()); } catch (NumberFormatException e) { throw t.site().error("integer overflow"); } }
            else {
                String name = id();
                if (take("(")) {
                    List<Expr> args = new ArrayList<>(); if (!take(")")) { do { args.add(expr(0)); } while (take(",")); need(")"); }
                    left = new Call(name, args, t.site());
                } else left = new Variable(name, t.site());
            }
            while (precedence(peek().text()) >= min) {
                Token op = peek(); pos++; Expr right = expr(precedence(op.text()) + 1); node(); left = new Binary(op.text(), left, right, op.site());
            }
            nesting--; return left;
        }
    }
}
