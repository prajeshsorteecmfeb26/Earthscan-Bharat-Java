# ---------------------------------------------------------------------------
# React frontend: Vite build, served by nginx.
# ---------------------------------------------------------------------------

FROM node:20-alpine AS builder

WORKDIR /app

# package.json + lockfile first so `npm ci` is cached independently of source
# changes. `npm ci` rather than `npm install`: it installs exactly the lockfile
# and fails if the two disagree, so a build can never silently pick up a
# different dependency version than was tested.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Vite inlines VITE_* variables at build time, so the API URL must be present
# now — it cannot be injected into the container at runtime.
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build

# --------------------------------------------------------------------- serve
FROM nginx:1.27-alpine

RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
    CMD wget --quiet --tries=1 --spider http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
