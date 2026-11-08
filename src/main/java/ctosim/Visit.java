package ctosim;

import java.time.LocalDateTime;

/**
 * One interview of the fieldwork plan: who, when, where, which survey id, and the household's visit
 * number and whether it is found. See specs/13-fieldwork-plan.md (Amendment 5).
 */
public record Visit(int enumerator, LocalDateTime start, double latitude, double longitude, String id,
        int attempt, boolean found) {

    /** A first visit that finds its household. */
    public Visit(int enumerator, LocalDateTime start, double latitude, double longitude, String id) {
        this(enumerator, start, latitude, longitude, id, 1, true);
    }
}
