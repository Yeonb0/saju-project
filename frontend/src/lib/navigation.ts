import type { RoutePath } from "./screens";

// 근거: FUNCTIONAL_SPEC 1장(사이드 메뉴) · 2장(/fortune/[type] 의 type), Q-23 k 해결, HOME-04 와이어(Figma 195:647).
// TODO(PD 문구): 메뉴 · 운세 이름은 팀 문서의 운세 이름 임시값

// /fortune/[type] 의 type — 이 5개만 허용한다
export const FORTUNE_SLUGS = [
  "love",
  "wealth",
  "overall",
  "sinsal",
  "compatibility",
] as const;
export type FortuneSlug = (typeof FORTUNE_SLUGS)[number];
export type FortunePath = `/fortune/${FortuneSlug}`;

export const FORTUNE_LABELS: Record<FortuneSlug, string> = {
  love: "애정운",
  wealth: "재물운",
  overall: "종합운",
  sinsal: "신살",
  compatibility: "궁합",
};

export type MenuLink = { label: string; href: RoutePath | FortunePath };
export type MenuGroup = { label: string; items: readonly MenuLink[] };
export type MenuEntry = MenuLink | MenuGroup;

const fortuneLink = (slug: FortuneSlug): MenuLink => ({
  label: FORTUNE_LABELS[slug],
  href: `/fortune/${slug}`,
});

// 사이드 메뉴 항목. 순서는 HOME-04 와이어 그대로.
export const MENU: readonly MenuEntry[] = [
  { label: "홈", href: "/" },
  { label: "마이페이지", href: "/me" },
  { label: "내 부적 창고", href: "/vault" },
  { label: "오늘의 운세", href: "/today" },
  {
    label: "유료 운세",
    items: [
      fortuneLink("love"),
      fortuneLink("wealth"),
      fortuneLink("overall"),
      fortuneLink("sinsal"),
      fortuneLink("compatibility"),
    ],
  },
  {
    label: "이벤트 운세",
    items: [{ label: "수능운", href: "/suneung" }],
  },
];

export const isMenuGroup = (entry: MenuEntry): entry is MenuGroup =>
  "items" in entry;
