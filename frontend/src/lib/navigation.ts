import type { RoutePath } from "./screens";

export type MenuLink = { label: string; href: RoutePath };
export type MenuGroup = { label: string; items: readonly MenuLink[] };
export type MenuEntry = MenuLink | MenuGroup;

// 사이드 메뉴 항목. 순서는 Figma node 52:186 그대로.
export const MENU: readonly MenuEntry[] = [
  { label: "홈", href: "/" },
  { label: "마이페이지", href: "/me" },
  { label: "내 부적 창고", href: "/vault" },
  { label: "오늘의 운세", href: "/today" },
  {
    label: "유료 운세",
    items: [
      // TODO(D-10): 애정운 — 출시 포함 여부와 /fortune/[type] 의 type 값 미정
      { label: "수능운", href: "/suneung" },
      // TODO(D-10): 취업운 — 출시 포함 여부 미정, 라우트 없음
    ],
  },
];

export const isMenuGroup = (entry: MenuEntry): entry is MenuGroup =>
  "items" in entry;
