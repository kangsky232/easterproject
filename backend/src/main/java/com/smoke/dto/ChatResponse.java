package com.smoke.dto;

import java.util.List;

public record ChatResponse(
        String answer,
        String source,
        String model,
        String riskLevel,
        String summary,
        List<String> immediateActions,
        List<String> verificationSteps,
        List<String> escalationConditions,
        String safetyNotice,
        List<KnowledgeSource> sources
) {
    public ChatResponse {
        immediateActions = immutable(immediateActions);
        verificationSteps = immutable(verificationSteps);
        escalationConditions = immutable(escalationConditions);
        sources = immutable(sources);
    }

    public static ChatResponse plain(String answer, String source, String riskLevel) {
        return new ChatResponse(
                answer,
                source,
                "",
                riskLevel,
                answer,
                List.of(),
                List.of(),
                List.of(),
                "如存在实际火情或人身风险，请立即疏散并拨打 119。",
                List.of()
        );
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record KnowledgeSource(String id, String title) {
    }
}
