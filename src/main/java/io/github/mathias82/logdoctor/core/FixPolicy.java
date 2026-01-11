package io.github.mathias82.logdoctor.core;

import java.util.EnumSet;
import java.util.Set;

public final class FixPolicy {

    private FixPolicy() {}

    public static Set<FixType> allowedFixes(IncidentCategory category) {
        return switch (category) {

            case DATABASE -> Set.of(
                    FixType.JAVA_CODE
            );
            case INFRASTRUCTURE -> Set.of(
                    FixType.NO_AUTOMATIC_FIX
            );
            case DESERIALIZATION -> EnumSet.of(
                    FixType.JAVA_CODE,
                    FixType.SPRING_CONFIG
            );
            case CONFIGURATION -> EnumSet.of(
                    FixType.SPRING_CONFIG
            );
            case MEMORY -> EnumSet.of(
                    FixType.JVM_OPTIONS,
                    FixType.JAVA_CODE
            );
            case THREADING -> EnumSet.of(
                    FixType.JAVA_CODE,
                    FixType.NO_AUTOMATIC_FIX
            );
            case BUSINESS -> EnumSet.of(
                    FixType.NO_AUTOMATIC_FIX
            );
            default -> EnumSet.of(FixType.NO_AUTOMATIC_FIX);
        };
    }
}
