import type { ReactNode } from "react";

type FieldProps = {
  id: string;
  "aria-invalid": true | undefined;
  "aria-describedby": string | undefined;
};

type FormFieldProps = {
  id: string;
  label: ReactNode;
  hint?: ReactNode;
  error?: ReactNode;
  // 바깥 <div> 에 붙는다 — 배치는 쓰는 쪽이 정한다 (LAYOUT-FIGMA)
  className?: string;
  // React Hook Form 의 register() 결과와 함께 펼쳐 쓸 수 있다: <input {...field} {...register("name")} />
  children: (field: FieldProps) => ReactNode;
};

export function FormField({
  id,
  label,
  hint,
  error,
  className,
  children,
}: FormFieldProps) {
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy =
    [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ") ||
    undefined;

  return (
    <div className={className}>
      {/* TODO(PD 토큰 v0): 와이어 임시값 (LAYOUT-FIGMA) — 20px, 줄 높이 24px */}
      <label
        htmlFor={id}
        className="block text-[20px] font-semibold leading-[24px]"
      >
        {label}
      </label>
      {/* FRONTEND.md 2장 — frame-*.svg 를 border-image 로 입힐 자리. SVG 는 PD 전달 후 */}
      <div data-frame="input" className="mt-[10px]">
        {children({
          id,
          "aria-invalid": error ? true : undefined,
          "aria-describedby": describedBy,
        })}
      </div>
      {hint ? <p id={hintId}>{hint}</p> : null}
      {error ? (
        <p id={errorId} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
