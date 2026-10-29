import Link from "next/link";
import { CheckIcon, LockIcon } from "./Icons";

type Props = {
  /** 인증된 도메인을 오른쪽에 보여 준다 */
  domain?: string | null;
  verified?: boolean;
  children?: React.ReactNode;
};

export function SiteHeader({ domain, verified, children }: Props) {
  return (
    <header className="site-header">
      <div className="site-header__inner">
        <Link href="/" className="brand">
          <LockIcon size={28} className="brand__icon" />
          문단속
        </Link>
        {domain ? (
          <span className="site-header__domain">
            {verified && <CheckIcon size={16} className="text-accent" />}
            {domain}
            {verified && " · 인증됨"}
          </span>
        ) : (
          children
        )}
      </div>
    </header>
  );
}
