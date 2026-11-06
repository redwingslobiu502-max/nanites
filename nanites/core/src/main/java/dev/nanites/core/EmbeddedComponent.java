package dev.nanites.core;

import java.util.*;

/** Immutable component state, including fuel and integration requirements. No generated energy. */
public record EmbeddedComponent(String id, String owner, String type, CapabilityCatalog.Origin origin,
                                String fuelType, long fuel, Map<String, Long> state,
                                Set<String> requiredCapabilities) {
    public EmbeddedComponent {
        id = Ledger.id(id); owner = Ledger.id(owner); type = Ledger.id(type); fuelType = Ledger.id(fuelType);
        Objects.requireNonNull(origin); Ledger.nonnegative(fuel);
        state = Map.copyOf(state); requiredCapabilities = Set.copyOf(requiredCapabilities);
        state.forEach((k, v) -> { Ledger.id(k); Ledger.nonnegative(v); }); requiredCapabilities.forEach(Ledger::id);
    }
    public void validateHost(CapabilityCatalog catalog, CapabilityCatalog.Definition host) {
        catalog.validate(host);
        if (!catalog.verified(origin)) throw new IllegalArgumentException("Component adapter not verified");
        if (!owner.equals(host.id())) throw new IllegalArgumentException("Wrong component owner");
        requiredCapabilities.forEach(cap -> catalog.requireVerified(host, cap));
    }
    /** Pure relocation. Real world extraction/commit remains the responsibility of a loader adapter. */
    public EmbeddedComponent relocate(CapabilityCatalog catalog, CapabilityCatalog.Definition target) {
        EmbeddedComponent next = new EmbeddedComponent(id, target.id(), type, origin, fuelType, fuel, state, requiredCapabilities);
        next.validateHost(catalog, target); return next;
    }
    /** Pure bookkeeping debit, NOT reactor simulation or energy production. */
    public EmbeddedComponent debitFuel(long amount) {
        Ledger.nonnegative(amount);
        if (amount > fuel) throw new IllegalArgumentException("Insufficient fuel");
        return new EmbeddedComponent(id, owner, type, origin, fuelType, Math.subtractExact(fuel, amount), state, requiredCapabilities);
    }
}
