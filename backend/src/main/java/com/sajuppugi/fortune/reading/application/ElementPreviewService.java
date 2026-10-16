package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Element;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Minimal, owned-person-only projection for the pre-purchase element preview screen. */
@Service
public class ElementPreviewService {
    private static final String CONTENT_VERSION = "element-preview-2026.10-v1";
    private static final Map<Element, List<Copy>> COPIES = Map.of(
            Element.WOOD, List.of(copy("새싹처럼 자라나는 추진형이에요", "새로운 일을 시작할 때 힘이 잘 붙어요. 작은 시도를 꾸준히 이어가 보세요."), copy("곧게 뻗는 성장형이에요", "방향을 정하면 성실하게 밀고 나가는 힘이 있어요. 목표를 한 문장으로 정리해 보세요."), copy("봄바람 같은 확장형이에요", "사람과 아이디어 사이에서 가능성을 발견하는 감각이 돋보여요."), copy("푸른 잎처럼 회복력 있는 타입이에요", "잠시 속도가 늦어져도 다시 자리를 잡는 힘이 있어요."), copy("뿌리부터 단단히 다지는 계획형이에요", "차근차근 기반을 만들수록 강점이 커져요.")),
            Element.FIRE, List.of(copy("햇살처럼 주변을 밝히는 표현형이에요", "마음을 자연스럽게 드러낼 때 분위기를 이끄는 힘이 있어요."), copy("불꽃처럼 집중력이 살아나는 몰입형이에요", "흥미를 느끼는 순간 빠르게 에너지가 모여요."), copy("따뜻한 온기를 전하는 공감형이에요", "사람의 마음을 살피는 감각이 돋보여요."), copy("반짝이는 아이디어를 가진 영감형이에요", "순간의 감각에서 좋은 생각을 발견하곤 해요."), copy("무대를 즐기는 활력형이에요", "사람들 앞에서 장점이 더 잘 드러날 수 있어요.")),
            Element.EARTH, List.of(copy("흙처럼 든든한 안정형이에요", "주변이 편하게 기댈 수 있는 차분함이 있어요."), copy("중심을 잡아주는 균형형이에요", "여러 선택지 사이에서 현실적인 답을 찾는 편이에요."), copy("차곡차곡 쌓아가는 성실형이에요", "눈에 띄지 않는 준비가 나중에 큰 차이를 만들어요."), copy("포근하게 품어주는 배려형이에요", "사람과 상황을 넓게 받아들이는 힘이 있어요."), copy("현실 감각이 좋은 실속형이에요", "가능한 방법을 구체적으로 찾는 능력이 돋보여요.")),
            Element.METAL, List.of(copy("맑게 정리하는 판단형이에요", "복잡한 상황에서도 핵심을 가려내는 힘이 있어요."), copy("단단한 기준을 가진 원칙형이에요", "중요한 가치가 분명할수록 강점이 살아나요."), copy("보석처럼 섬세한 완성형이에요", "작은 차이까지 살피는 꼼꼼함이 장점이에요."), copy("군더더기 없이 선명한 집중형이에요", "필요한 일에 에너지를 모을수록 성과가 잘 보여요."), copy("약속을 지키는 신뢰형이에요", "꾸준함과 책임감이 관계에서 빛나요.")),
            Element.WATER, List.of(copy("물처럼 유연한 지혜형이에요", "흐름을 읽고 상황에 맞춰 방향을 조절하는 힘이 있어요."), copy("깊은 바다 같은 탐구형이에요", "겉보다 본질을 알고 싶어 하는 마음이 커요."), copy("잔잔한 호수 같은 관찰형이에요", "말보다 분위기와 변화를 먼저 알아차리는 편이에요."), copy("시냇물처럼 연결하는 소통형이에요", "서로 다른 생각 사이를 자연스럽게 이어 주는 힘이 있어요."), copy("비처럼 촉촉한 상상형이에요", "조용한 시간에 생각과 감각이 풍성해질 수 있어요.")));
    private final BasicSajuService basic;
    public ElementPreviewService(BasicSajuService basic) { this.basic = basic; }
    public Preview get(UUID owner, UUID personId) {
        var result = basic.calculateOwned(owner, personId); Element primary = result.dayMaster().element();
        List<Copy> choices = COPIES.get(primary); Copy copy = choices.get(index(personId + "|" + primary + "|" + result.fiveElements().counts() + "|" + CONTENT_VERSION, choices.size()));
        String hint = result.fiveElements().missing().isEmpty() ? "전체 기운의 균형은 결과에서 더 자세히 살펴볼게요."
                : "채워갈 기운과 생활 속 힌트는 결과에서 더 자세히 알려드릴게요.";
        return new Preview(result.fiveElements().counts(), primary, copy.headline(), copy.description(), hint, CONTENT_VERSION);
    }
    private static Copy copy(String headline, String description) { return new Copy(headline, description); }
    private static int index(String seed, int size) { try { return Math.floorMod(java.nio.ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8))).getInt(), size); } catch (Exception e) { throw new IllegalStateException(e); } }
    private record Copy(String headline, String description) {}
    public record Preview(Map<Element, Integer> counts, Element primaryElement, String headline, String description, String balanceHint, String contentVersion) {}
}
