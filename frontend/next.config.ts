import type { NextConfig } from "next";

// 브라우저는 같은 출처의 /api 로 부르고, Next.js 가 백엔드로 넘긴다 (백엔드에 CORS 설정이 필요 없다)
const apiBaseUrl = process.env.API_BASE_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiBaseUrl}/api/:path*` }];
  },
};

export default nextConfig;
