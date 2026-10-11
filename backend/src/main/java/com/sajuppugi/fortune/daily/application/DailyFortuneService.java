package com.sajuppugi.fortune.daily.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.calculation.application.SajuCalculationUseCase;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.reading.port.ReadingSubjectPort;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DailyFortuneService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String CONTENT_VERSION = "daily-score-2026.10-v1";
    private final ReadingSubjectPort subjects;
    private final SajuCalculationUseCase calculation;
    private final Clock clock;

    public DailyFortuneService(ReadingSubjectPort subjects, SajuCalculationUseCase calculation, Clock clock) {
        this.subjects = subjects;
        this.calculation = calculation;
        this.clock = clock;
    }

    public DailyFortune today(UUID userId, UUID personId) {
        if (userId == null || personId == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
        ReadingSubjectPort.OwnedSubject subject = subjects.getOwnedSubject(userId, personId);
        if (subject == null) throw new ApiException(ErrorCode.PERSON_NOT_FOUND);
        CalculationFacts facts;
        try {
            facts = calculation.calculate(subject.birthInput(), CalculationPolicy.CURRENT);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.UNSUPPORTED_CALENDAR_DATE);
        }
        LocalDate date = LocalDate.now(clock.withZone(KST));
        String seed = seed(date, facts);
        return new DailyFortune(date, personId, new Scores(
                score(seed, "OVERALL"), score(seed, "LOVE"), score(seed, "WEALTH"),
                score(seed, "STUDY_WORK"), score(seed, "CONDITION")),
                facts.meta().calculationVersion(), CONTENT_VERSION);
    }

    private String seed(LocalDate date, CalculationFacts facts) {
        var counts = facts.fiveElements().counts();
        return String.join("|", date.toString(), facts.dayMaster().hanja(), facts.dayMaster().element().name(),
                Integer.toString(counts.get(CalculationFacts.Element.WOOD)),
                Integer.toString(counts.get(CalculationFacts.Element.FIRE)),
                Integer.toString(counts.get(CalculationFacts.Element.EARTH)),
                Integer.toString(counts.get(CalculationFacts.Element.METAL)),
                Integer.toString(counts.get(CalculationFacts.Element.WATER)), CONTENT_VERSION);
    }

    private int score(String seed, String category) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((seed + "|" + category).getBytes(StandardCharsets.UTF_8));
            return Math.floorMod(ByteBuffer.wrap(digest).getInt(), 101);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record DailyFortune(LocalDate date, UUID personId, Scores scores,
                               String calculationVersion, String contentVersion) {}

    public record Scores(int overall, int love, int wealth, int studyWork, int condition) {}
}
