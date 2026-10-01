"use client";

// "use client" 이유: vaul(Drawer)은 클라이언트 전용 라이브러리다.
import type { ReactNode } from "react";
import { Drawer } from "vaul";
import { COLUMN_WIDTH } from "@/lib/layout";

type BottomSheetProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  children: ReactNode;
};

export function BottomSheet({
  open,
  onOpenChange,
  title,
  children,
}: BottomSheetProps) {
  return (
    <Drawer.Root open={open} onOpenChange={onOpenChange}>
      <Drawer.Portal>
        {/* TODO(PD 토큰 v0): 덮개 색 임시값 */}
        <Drawer.Overlay className="fixed inset-0 bg-black/40" />
        {/* 폭: 좌우 0 + 가운데 정렬 + 최대 폭 COLUMN_WIDTH (AppShell 앱 기둥과 같은 값) */}
        <Drawer.Content
          aria-describedby={undefined}
          className="fixed inset-x-0 bottom-0 mx-auto w-full rounded-t-[20px] bg-white pb-[env(safe-area-inset-bottom)] text-neutral-900" // TODO(PD 토큰 v0): 색 · 모서리 임시값
          style={{ maxWidth: COLUMN_WIDTH }}
        >
          <Drawer.Title>{title}</Drawer.Title>
          {children}
        </Drawer.Content>
      </Drawer.Portal>
    </Drawer.Root>
  );
}
