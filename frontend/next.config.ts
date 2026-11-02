import type { NextConfig } from "next";

// The browser talks only to Next.js; /api/* is forwarded to the Spring Boot backend.
const backend = process.env.FONTIS_API_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  devIndicators: false,
  experimental: {
    // Answers can take over 30 s when the model is loaded into the GPU: don't cut the proxy before.
    proxyTimeout: 180_000,
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
