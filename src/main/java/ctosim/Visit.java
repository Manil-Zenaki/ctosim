package ctosim;

import java.time.LocalDateTime;

/** One interview of the fieldwork plan: who, when, where, and which survey id. See specs/13-fieldwork-plan.md. */
public record Visit(int enumerator, LocalDateTime start, double latitude, double longitude, String id) {
}
