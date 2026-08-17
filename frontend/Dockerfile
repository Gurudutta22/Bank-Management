# =====================================================================================
# Frontend image - multi-stage.
#
# Node builds the bundle; nginx serves it. The final image contains no Node, no
# node_modules and no source - just static files and a web server.
# =====================================================================================

# ------------------------------------------------------------------ build
FROM node:22-alpine AS build
WORKDIR /build

# `npm ci` (not `npm install`) installs exactly what package-lock.json pins, so the image
# is reproducible and a transitive dependency cannot silently change between builds.
COPY package.json package-lock.json* ./
RUN npm ci

COPY . .
RUN npm run build

# ---------------------------------------------------------------- runtime
FROM nginx:1.27-alpine AS runtime

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=20s --timeout=4s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
