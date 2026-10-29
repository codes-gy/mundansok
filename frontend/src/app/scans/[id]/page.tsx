import type { Metadata } from "next";
import { Suspense } from "react";
import { ScanView } from "@/components/ScanView";
import { SiteHeader } from "@/components/SiteHeader";

export const metadata: Metadata = {
  title: "점검 결과 · 문단속",
};

export default function ScanPage() {
  return (
    <Suspense fallback={<SiteHeader />}>
      <ScanView />
    </Suspense>
  );
}
