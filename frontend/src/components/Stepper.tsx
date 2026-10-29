import { CheckIcon } from "./Icons";

const STEPS = ["도메인 입력", "소유 인증", "점검", "결과"];

/** current: 지금 단계 (0부터) */
export function Stepper({ current }: { current: number }) {
  return (
    <ol aria-label="진행 단계" className="stepper">
      {STEPS.map((label, index) => {
        if (index < current) {
          return (
            <li key={label} className="stepper__step stepper__step--done">
              <CheckIcon size={16} />
              {label}
            </li>
          );
        }
        return (
          <li
            key={label}
            aria-current={index === current ? "step" : undefined}
            className={index === current ? "stepper__step stepper__step--current" : "stepper__step"}
          >
            {index + 1} {label}
          </li>
        );
      })}
    </ol>
  );
}
