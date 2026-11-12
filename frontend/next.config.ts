import type { NextConfig } from "next";

// The browser talks only to Next.js; /api/* is forwarded to the Spring Boot backend.
// Read at build time: the Docker image is built with FONTIS_API_URL=http://backend:8080.
const backend = process.env.FONTIS_API_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // Self-contained server for the Docker image (frontend/Dockerfile).
  output: "standalone",
  cacheComponents: true,
  partialPrefetching: true,
  devIndicators: false,
  poweredByHeader: false,
  experimental: {
    // Answers can take over 30 s when the model is loaded into the GPU: don't cut the proxy before.
    proxyTimeout: 180_000,
    // Uploads go through this proxy, which otherwise cuts request bodies at 10 MB without an error.
    // Matches the backend's spring.servlet.multipart.max-request-size.
    proxyClientMaxBodySize: "26mb",
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
