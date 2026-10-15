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
    private static final String CONTENT_VERSION = "daily-2026.10-v3";
    private static final String[] LUCKY_COLORS = {"코랄", "하늘색", "올리브", "보랏빛", "아이보리", "네이비", "민트", "와인색"};
    private static final String[] LUCKY_ITEMS = {"작은 메모장", "물병", "손목시계", "이어폰 케이스", "필기구", "손수건", "책갈피", "파우치"};
    private static final String[] LUCKY_PLACES = {"햇볕 드는 창가", "조용한 산책로", "익숙한 카페", "정돈된 책상", "가까운 공원", "동네 서점", "편안한 휴식 공간", "사람이 많지 않은 길"};
    private static final String[] TODAY_ACTIONS = {"할 일을 한 가지씩 적고 가장 작은 것부터 시작해 보세요.",
            "중요한 답은 서두르지 말고 한 번 더 확인해 보세요.", "잠깐의 정리가 흐름을 가볍게 만들 수 있어요.",
            "익숙한 사람에게 먼저 안부를 건네 보세요.", "쉬는 시간을 미리 정해 두고 집중해 보세요.",
            "비교보다 지금 할 수 있는 한 걸음에 집중해 보세요.", "미뤄 둔 작은 연락이나 정리를 마쳐 보세요.",
            "몸의 신호를 살피며 평소의 리듬을 지켜 보세요."};
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
        Scores scores = new Scores(
                score(seed, "OVERALL"), score(seed, "LOVE"), score(seed, "WEALTH"),
                score(seed, "STUDY_WORK"), score(seed, "CONDITION"));
        return new DailyFortune(date, personId, scores, content(seed, facts, scores), extras(userId, date),
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

    private DailyContent content(String seed, CalculationFacts facts, Scores scores) {
        int index = score(seed, "DAILY_CONTENT") % LUCKY_COLORS.length;
        String oneLineFortune = switch (facts.dayMaster().element()) {
            case WOOD -> "차분히 방향을 정하면 새로운 흐름을 시작하기 좋은 하루예요.";
            case FIRE -> "의욕을 한곳에 모으면 반가운 진전을 만들 수 있는 하루예요.";
            case EARTH -> "익숙한 리듬을 지키는 것이 오늘의 안정적인 선택이 될 수 있어요.";
            case METAL -> "기준을 정리하고 우선순위를 세우면 마음이 가벼워질 수 있어요.";
            case WATER -> "여유를 남겨 두고 상황을 살피면 좋은 선택이 보일 수 있어요.";
        };
        if (scores.overall() >= 70) oneLineFortune = "작은 자신감을 행동으로 옮기기 좋은 하루예요. " + oneLineFortune;
        else if (scores.overall() <= 30) oneLineFortune = "속도를 조금 늦추고 기본을 챙기면 충분한 하루예요. " + oneLineFortune;
        return new DailyContent(oneLineFortune, LUCKY_COLORS[index], LUCKY_ITEMS[index],
                LUCKY_PLACES[index], TODAY_ACTIONS[index]);
    }

    private LuckyExtras extras(UUID userId, LocalDate date) {
        String seed = userId + "|" + date + "|" + CONTENT_VERSION;
        DailyFortuneCatalog.Guardian guardian = DailyFortuneCatalog.GUARDIANS.get(select(seed, "GUARDIAN", DailyFortuneCatalog.GUARDIANS.size()));
        DailyFortuneCatalog.Entry mission = DailyFortuneCatalog.MISSIONS.get(select(seed, "MISSION", DailyFortuneCatalog.MISSIONS.size()));
        DailyFortuneCatalog.Entry song = DailyFortuneCatalog.SONGS.get(select(seed, "SONG", DailyFortuneCatalog.SONGS.size()));
        String initials = DailyFortuneCatalog.INITIALS.get(select(seed, "INITIAL", DailyFortuneCatalog.INITIALS.size()));
        return new LuckyExtras(new GuardianAnimal(guardian.key(), guardian.animal(), guardian.name(), guardian.line()),
                new LuckyMission(mission.key(), mission.value()), initials,
                new LuckySong(song.key(), song.value(), song.detail()));
    }

    private int select(String seed, String category, int size) { return score(seed, category) % size; }

    public record DailyFortune(LocalDate date, UUID personId, Scores scores, DailyContent content, LuckyExtras luckyExtras,
                               String calculationVersion, String contentVersion) {
        public DailyFortune(LocalDate date, UUID personId, Scores scores, DailyContent content,
                            String calculationVersion, String contentVersion) {
            this(date, personId, scores, content, null, calculationVersion, contentVersion);
        }
    }

    public record Scores(int overall, int love, int wealth, int studyWork, int condition) {}

    public record DailyContent(String oneLineFortune, String luckyColor, String luckyItem,
                               String luckyPlace, String todayAction) {}

    /** Additive response contract. Values are populated with the PD catalog in daily-2026.10-v3. */
    public record LuckyExtras(GuardianAnimal guardianAnimal, LuckyMission luckyMission,
                              String luckyInitial, LuckySong luckySong) {}
    public record GuardianAnimal(String key, String animal, String name, String line) {}
    public record LuckyMission(String key, String text) {}
    public record LuckySong(String key, String title, String artist) {}
}
