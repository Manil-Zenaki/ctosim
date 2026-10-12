package ctosim;

/** A conditional setting of a field: applies when its condition holds. See specs/04-simulation-config.md, Amendment 3. */
public record OverrideRule(String condition, FieldOverride override) { }
