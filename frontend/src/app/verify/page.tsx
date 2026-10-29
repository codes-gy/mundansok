import type { Metadata } from "next";
import { Suspense } from "react";
import { SiteHeader } from "@/components/SiteHeader";
import { VerifyView } from "@/components/VerifyView";

export const metadata: Metadata = {
  title: "도메인 소유 인증 · 문단속",
};

export default function VerifyPage() {
  return (
    <Suspense fallback={<SiteHeader />}>
      <VerifyView />
    </Suspense>
  );
}
