package ctosim;

/** Where points are drawn. See specs/04-simulation-config.md, Amendment 3. */
public record Area(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) { }
