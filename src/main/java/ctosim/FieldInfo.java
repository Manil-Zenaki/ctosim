package ctosim;

/** One field of a {@link FieldCatalog}. See specs/08-surveycto-exporter.md. */
public record FieldInfo(String name, String type, String xpath, int row) {
}
