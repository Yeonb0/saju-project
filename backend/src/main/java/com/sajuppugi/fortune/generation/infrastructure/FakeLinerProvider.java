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
        String evidence = "dayMaster.hanja";
        var sections = request.allowedSections().stream()
                .map(section -> new GeneratedSection(section,
                        "계산된 명리 정보에 근거한 " + section.name().toLowerCase() + " 테스트 문장입니다.", List.of(evidence)))
                .toList();
        return new LinerResponse(sections, List.of(), request.fortuneType() == FortuneType.SUNEUNG
                ? SuneungGuideFactory.fromSections(sections) : null);
    }
}
