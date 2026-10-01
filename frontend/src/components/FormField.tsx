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
  // React Hook Form 의 register() 결과와 함께 펼쳐 쓸 수 있다: <input {...field} {...register("name")} />
  children: (field: FieldProps) => ReactNode;
};

export function FormField({
  id,
  label,
  hint,
  error,
  children,
}: FormFieldProps) {
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy =
    [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ") ||
    undefined;

  return (
    <div>
      <label htmlFor={id}>{label}</label>
      {/* FRONTEND.md 2장 — frame-*.svg 를 border-image 로 입힐 자리. SVG 는 PD 전달 후 */}
      <div data-frame="input">
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
