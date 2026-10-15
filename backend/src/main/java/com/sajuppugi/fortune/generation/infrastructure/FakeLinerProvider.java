package com.sajuppugi.fortune.generation.infrastructure;

import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedSection;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.generation.application.SuneungGuideFactory;
import com.sajuppugi.fortune.generation.port.LinerProvider;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.liner.adapter", havingValue = "fake", matchIfMissing = true)
public class FakeLinerProvider implements LinerProvider {
    @Override
    public String name() {
        return "fake-liner";
    }

    @Override
    public LinerResponse generate(LinerRequest request) {
        var sections = request.allowedSections().stream()
                .map(section -> new GeneratedSection(section,
                        "계산된 명리 정보에 근거한 " + section.name().toLowerCase() + " 테스트 문장입니다.",
                        evidence(section)))
                .toList();
        return new LinerResponse(sections, List.of(), request.fortuneType() == FortuneType.SUNEUNG
                ? SuneungGuideFactory.fromSections(sections) : null);
    }

    private List<String> evidence(com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey section) {
        return switch (section) {
            case MISSING_ELEMENT -> List.of("fiveElements.counts.WOOD");
            case GOOD_PERIOD -> List.of("luck.annual[0].year");
            case MATCH_STRENGTH, MATCH_CONFLICT, COMMUNICATION, RELATIONSHIP_TIP ->
                    List.of("dayMaster.hanja", "counterpart.dayMaster.hanja");
            case TIMING -> List.of("luck.annual[0].year");
            case SPECIAL_STARS, BALANCING_SPECIAL_STARS, SINSAL_POPULAR, SINSAL_MOVEMENT,
                    SINSAL_INTUITION, SINSAL_INTENSITY -> List.of("specialStars.ruleVersion");
            case SINSAL_TIMING -> List.of("specialStars.ruleVersion");
            default -> List.of("dayMaster.hanja");
        };
    }
}
