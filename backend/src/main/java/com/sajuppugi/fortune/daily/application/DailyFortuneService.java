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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DailyFortuneService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String CONTENT_VERSION = "daily-2026.10-v4";
    private static final String[] LUCKY_COLORS = {"코랄", "하늘색", "올리브", "보랏빛", "아이보리", "네이비", "민트", "와인색"};
    private static final String[] LUCKY_ITEMS = {"작은 메모장", "물병", "손목시계", "이어폰 케이스", "필기구", "손수건", "책갈피", "파우치"};
    private static final String[] LUCKY_PLACES = {"햇볕 드는 창가", "조용한 산책로", "익숙한 카페", "정돈된 책상", "가까운 공원", "동네 서점", "편안한 휴식 공간", "사람이 많지 않은 길"};
    private static final String[] TODAY_ACTIONS = {"할 일을 한 가지씩 적고 가장 작은 것부터 시작해 보세요.",
            "중요한 답은 서두르지 말고 한 번 더 확인해 보세요.", "잠깐의 정리가 흐름을 가볍게 만들 수 있어요.",
            "익숙한 사람에게 먼저 안부를 건네 보세요.", "쉬는 시간을 미리 정해 두고 집중해 보세요.",
            "비교보다 지금 할 수 있는 한 걸음에 집중해 보세요.", "미뤄 둔 작은 연락이나 정리를 마쳐 보세요.",
            "몸의 신호를 살피며 평소의 리듬을 지켜 보세요."};
    private static final Map<CalculationFacts.Element, List<String>> ONE_LINE_FORTUNES = Map.of(
            CalculationFacts.Element.WOOD, phrases("차분히 방향을 정하면 새로운 흐름을 시작하기 좋은 하루예요.|작은 약속 하나를 지키면 하루의 흐름이 단단해져요.|새로운 생각을 가볍게 꺼내 보기 좋은 날이에요.|조금 돌아가도 괜찮아요. 내 리듬을 믿어 봐요.|처음 마음을 떠올리면 다음 걸음이 쉬워져요.|눈앞의 한 가지에 집중하면 길이 보여요.|오늘의 호기심이 내일의 기회로 이어질 수 있어요.|부드럽게 시작한 일이 생각보다 멀리 갈 수 있어요.|새싹처럼 가볍게 움직이면 기분 좋은 변화가 와요.|오늘 배운 작은 것이 내일의 자신감이 돼요.|마음을 열고 들으면 새로운 길이 보여요.|익숙하지 않은 선택도 천천히 시도해 봐요.|좋은 흐름은 작은 질문에서 시작될 수 있어요.|나를 믿는 말 한마디가 힘이 돼요.|생각을 적어 두면 다음 걸음이 또렷해져요.|지금의 성장이 충분히 멋지다는 걸 기억해요.|가벼운 인사가 반가운 연결을 만들어요.|할 수 있는 일부터 시작하면 마음이 편해져요.|오늘의 변화는 천천히 와도 괜찮아요.|내가 고른 방향을 한 번 더 믿어 봐요."),
            CalculationFacts.Element.FIRE, phrases("의욕을 한곳에 모으면 반가운 진전을 만들 수 있는 하루예요.|망설이던 말도 따뜻하게 건네기 좋은 날이에요.|작은 자신감이 주변 분위기까지 밝게 만들어요.|하고 싶은 일을 먼저 적으면 에너지가 모여요.|반가운 제안에는 가볍게 손을 들어 봐요.|오늘의 열정은 짧고 선명하게 쓰는 게 좋아요.|웃으며 시작한 일이 예상보다 잘 풀릴 수 있어요.|마음이 움직이는 쪽에 한 걸음 다가가 봐요.|기분 좋은 용기가 새로운 장면을 만들어요.|내 장점을 한 번 더 보여 주기 좋은 날이에요.|밝은 인사가 대화를 자연스럽게 열어 줘요.|지금 떠오른 생각을 작게라도 실천해 봐요.|좋아하는 일에 시간을 쓰면 에너지가 돌아와요.|오늘의 설렘을 행동으로 옮겨 봐요.|누군가를 응원하는 말이 나에게도 힘이 돼요.|힘을 너무 나누지 말고 한 곳에 모아 봐요.|재미있는 일을 먼저 하면 흐름이 살아나요.|내 목소리를 자신 있게 내도 괜찮아요.|따뜻한 웃음이 오늘의 행운 포인트예요.|마음속 불빛을 따라 한 걸음만 가 봐요."),
            CalculationFacts.Element.EARTH, phrases("익숙한 리듬을 지키는 것이 오늘의 안정적인 선택이 될 수 있어요.|급한 마음보다 차근차근 확인하는 힘이 빛나요.|내가 할 수 있는 범위를 정하면 마음이 편해져요.|오늘은 기본을 챙긴 만큼 결과가 따라와요.|작은 정리가 생각보다 큰 여유를 만들어요.|주변의 도움을 고맙게 받으면 일이 부드러워져요.|꾸준히 해 온 일을 믿고 마무리해 봐요.|느린 속도에도 나만의 단단함이 있어요.|한 가지를 끝내면 마음에 공간이 생겨요.|평소 루틴이 오늘의 든든한 편이 돼요.|서두르지 않는 선택이 더 오래 가요.|내 자리부터 정돈하면 생각도 차분해져요.|작은 성과를 스스로 칭찬해 줘요.|차근차근 쌓은 시간이 답을 보여 줄 거예요.|도움이 필요하면 편하게 손을 내밀어 봐요.|오늘 할 일을 줄이면 중요한 것이 보여요.|편안한 식사와 휴식도 좋은 준비예요.|기본을 지킨 내가 제일 믿음직스러워요.|꾸준함은 조용하지만 확실한 힘이에요.|마음이 흔들리면 익숙한 곳으로 돌아와 봐요."),
            CalculationFacts.Element.METAL, phrases("기준을 정리하고 우선순위를 세우면 마음이 가벼워질 수 있어요.|선명한 선택 하나가 오늘을 편하게 만들어요.|미뤄 둔 결정을 조용히 정리하기 좋은 날이에요.|필요한 것과 아닌 것을 나누면 길이 또렷해져요.|한 문장으로 마음을 정리해 보는 건 어때요.|작은 원칙을 지키는 모습이 신뢰를 만들어요.|집중할 대상을 고르면 속도가 자연스럽게 붙어요.|깔끔한 마무리가 다음 기회를 불러와요.|내 기준을 지키는 선택이 충분히 멋져요.|정확한 한 번의 확인이 마음을 놓이게 해요.|복잡한 일은 순서를 정하면 쉬워져요.|정리한 메모 한 장이 오늘을 도와줘요.|필요한 말은 담백하게 전해도 괜찮아요.|한 가지를 덜어 내면 시야가 넓어져요.|집중할 시간을 짧게 정해 봐요.|차분한 판단이 좋은 결과를 만들어요.|중요한 일부터 처리하면 하루가 단정해져요.|내 결정을 존중하면 마음이 또렷해져요.|정확하게 끝낸 일이 자신감을 남겨요.|오늘은 가볍고 명확하게 움직여 봐요."),
            CalculationFacts.Element.WATER, phrases("여유를 남겨 두고 상황을 살피면 좋은 선택이 보일 수 있어요.|잠깐 멈춰 보는 시간이 오히려 답을 알려줘요.|오늘은 흐름을 읽으며 유연하게 움직여 봐요.|조용한 집중이 생각보다 큰 힘을 발휘해요.|마음을 가볍게 비우면 새로운 생각이 들어와요.|서두르지 않아도 괜찮아요. 타이밍을 살펴봐요.|편안한 대화 속에서 힌트를 찾을 수 있어요.|내 속도를 지키면 하루가 부드럽게 흘러가요.|조용한 곳에서 생각을 쉬게 해 줘요.|오늘의 감각을 믿으면 길을 찾을 수 있어요.|물을 마시듯 천천히 채워 가도 괜찮아요.|급하지 않은 답이 더 잘 맞을 때가 있어요.|상대의 말을 끝까지 들으면 기회가 보여요.|비워 둔 시간에 좋은 생각이 찾아와요.|변화에 맞춰 움직이는 힘이 오늘의 장점이에요.|차분히 관찰한 뒤 선택해도 늦지 않아요.|쉬어 가는 순간도 흐름의 일부예요.|내 마음이 편한 방향을 살펴봐요.|조용한 용기가 오늘을 앞으로 이끌어요.|부드럽게 흘러가도 결국 원하는 곳에 닿아요."));
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
        List<String> candidates = ONE_LINE_FORTUNES.get(facts.dayMaster().element());
        String oneLineFortune = candidates.get(select(seed, "ONE_LINE", candidates.size()));
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
    private static List<String> phrases(String value) { return List.of(value.split("\\|")); }

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
