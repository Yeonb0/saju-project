"use client";

// "use client" 이유: Radix Dialog 는 클라이언트 전용 라이브러리다.
// 닫기 버튼은 두지 않는다 — 화면마다 다르다 ("부적이 저장되었어요!" 팝업은 Phase 4).
import * as Dialog from "@radix-ui/react-dialog";
import type { ReactNode } from "react";

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
          // 와이어(LAYOUT-FIGMA, 248:267): 폭 308px · 회색 · 모서리 없음 · 안쪽 위 22 / 좌우 13 / 아래 18 · 글자 가운데. 좁은 화면에서는 양옆 16px 여백 안으로
          className="fixed top-1/2 left-1/2 w-[308px] max-w-[calc(100%-32px)] -translate-x-1/2 -translate-y-1/2 bg-[#d9d9d9] px-[13px] pt-[22px] pb-[18px] text-center text-neutral-900" // TODO(PD 토큰 v0): 와이어 임시값
        >
          <Dialog.Title className="text-[20px]">{title}</Dialog.Title>
          {description ? (
            <Dialog.Description>{description}</Dialog.Description>
          ) : null}
          {children}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
