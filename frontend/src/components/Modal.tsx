"use client";

// "use client" 이유: Radix Dialog 는 클라이언트 전용 라이브러리다.
// 닫기 버튼은 두지 않는다 — 화면마다 다르다 (40 "부적이 저장되었어요!" 모달은 Phase 4).
import * as Dialog from "@radix-ui/react-dialog";
import type { ReactNode } from "react";
import { COLUMN_WIDTH } from "@/lib/layout";

type ModalProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  children: ReactNode;
};

export function Modal({
  open,
  onOpenChange,
  title,
  description,
  children,
}: ModalProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        {/* TODO(PD 토큰 v0): 덮개 색 임시값 */}
        <Dialog.Overlay className="fixed inset-0 bg-black/40" />
        {/* 상자: 앱 기둥(COLUMN_WIDTH) 안쪽, 양옆 16px 여백 */}
        <Dialog.Content
          // description 이 없을 때만 aria-describedby={undefined} 로 Radix 경고를 끈다
          {...(description ? {} : { "aria-describedby": undefined })}
          className="fixed top-1/2 left-1/2 w-[calc(100%-32px)] -translate-x-1/2 -translate-y-1/2 rounded-lg bg-white p-4 text-neutral-900" // TODO(PD 토큰 v0): 색 · 모서리 임시값
          style={{ maxWidth: COLUMN_WIDTH - 32 }}
        >
          <Dialog.Title>{title}</Dialog.Title>
          {description ? (
            <Dialog.Description>{description}</Dialog.Description>
          ) : null}
          {children}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
