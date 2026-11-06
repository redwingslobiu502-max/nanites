package dev.nanites.core;

import java.util.*;

/** Evidence is supplied by trusted adapters, never inferred from names or decompiled text. */
public final class CapabilityCatalog {
    public enum Verification { DECLARED, VERIFIED }
    public record Origin(String source, String adapter, String version, String evidence, Verification verification) {
        public Origin {
            source = Ledger.id(source); adapter = Ledger.id(adapter); version = Ledger.id(version);
            Objects.requireNonNull(verification); Objects.requireNonNull(evidence);
            if (evidence.isBlank() || evidence.length() > 1024) throw new IllegalArgumentException("Evidence required");
        }
    }
    public record Property(String key, Set<String> values) {
        public Property {
            key = Ledger.id(key); values = Set.copyOf(values); values.forEach(Ledger::id);
            if (values.isEmpty()) throw new IllegalArgumentException("No allowed values");
        }
    }
    public record Fact(String value, Origin origin) {
        public Fact { value = Ledger.id(value); Objects.requireNonNull(origin); }
    }
    public record Conflict(String left, String leftValue, String right, String rightValue) {
        public Conflict { left = Ledger.id(left); right = Ledger.id(right); leftValue = Ledger.id(leftValue); rightValue = Ledger.id(rightValue); }
    }
    public record Capability(String key, Map<String, String> requirements, Origin origin) {
        public Capability {
            key = Ledger.id(key); requirements = Map.copyOf(requirements); Objects.requireNonNull(origin);
            requirements.forEach((k, v) -> { Ledger.id(k); Ledger.id(v); });
        }
    }
    /** Definition independent of equipment slots/render models: armor, bridge, tool or any structure. */
    public record Definition(String id, Map<String, Fact> properties, Set<String> capabilities) {
        public Definition {
            id = Ledger.id(id); properties = Map.copyOf(properties); capabilities = Set.copyOf(capabilities);
            properties.keySet().forEach(Ledger::id); capabilities.forEach(Ledger::id);
        }
    }
    private final Map<String, Property> properties;
    private final Map<String, Capability> capabilities;
    private final Set<String> trustedAdapterVersions;
    private final List<Conflict> conflicts;

    public CapabilityCatalog(Collection<Property> properties, Collection<Capability> capabilities,
                             List<Conflict> conflicts, Set<String> trustedAdapterVersions) {
        this.trustedAdapterVersions = Set.copyOf(trustedAdapterVersions);
        Map<String, Property> p = new LinkedHashMap<>();
        for (Property item : properties) if (p.putIfAbsent(item.key(), item) != null) throw new IllegalArgumentException("Duplicate property");
        this.properties = Map.copyOf(p);
        this.conflicts = List.copyOf(conflicts);
        for (Conflict c : this.conflicts) { validateValue(c.left(), c.leftValue()); validateValue(c.right(), c.rightValue()); }
        Map<String, Capability> caps = new LinkedHashMap<>();
        for (Capability item : capabilities) {
            item.requirements().forEach(this::validateValue);
            if (caps.putIfAbsent(item.key(), item) != null) throw new IllegalArgumentException("Duplicate capability");
        }
        this.capabilities = Map.copyOf(caps);
    }
    public void validate(Definition definition) {
        definition.properties().forEach((key, fact) -> validateValue(key, fact.value()));
        for (Conflict c : conflicts)
            if (matches(definition, c.left(), c.leftValue()) && matches(definition, c.right(), c.rightValue()))
                throw new IllegalArgumentException("Conflicting properties: " + c.left() + ", " + c.right());
        for (String cap : definition.capabilities()) requireVerified(definition, cap);
    }
    public boolean verified(Origin origin) {
        return origin.verification() == Verification.VERIFIED && trustedAdapterVersions.contains(origin.adapter() + "@" + origin.version());
    }
    public void requireVerified(Definition definition, String key) {
        Capability capability = capabilities.get(key);
        if (capability == null || !definition.capabilities().contains(key) || !verified(capability.origin()))
            throw new IllegalArgumentException("Capability unavailable or unverified: " + key);
        for (var requirement : capability.requirements().entrySet()) {
            Fact fact = definition.properties().get(requirement.getKey());
            if (fact == null || !fact.value().equals(requirement.getValue()) || !verified(fact.origin()))
                throw new IllegalArgumentException("Unmet verified property: " + requirement.getKey());
        }
    }
    private boolean matches(Definition definition, String property, String value) {
        Fact fact = definition.properties().get(property); return fact != null && fact.value().equals(value);
    }
    private void validateValue(String key, String value) {
        Property property = properties.get(key);
        if (property == null || !property.values().contains(value)) throw new IllegalArgumentException("Unknown property/value: " + key);
    }
}
