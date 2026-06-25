package com.groupmatch.domain;

/**
 * Тарифный план пользователя.
 * Хранится в БД как строка (FREE/PRO/TEAM).
 */
public enum Plan {
    FREE,
    PRO,
    TEAM;

    public PlanLimits limits() {
        return switch (this) {
            case FREE -> new PlanLimits(3, 15, 50, 4, 3);
            case PRO  -> new PlanLimits(Integer.MAX_VALUE, 30, 200, 8, 10);
            case TEAM -> new PlanLimits(Integer.MAX_VALUE, 100, 500, 12, Integer.MAX_VALUE);
        };
    }

    /** Лимиты, ассоциированные с планом. */
    public record PlanLimits(
            int maxGroups,
            int maxMembersPerGroup,
            int maxSlotsPerMember,
            int maxHeatmapWeeks,
            int invitesPerHour
    ) {}
}
