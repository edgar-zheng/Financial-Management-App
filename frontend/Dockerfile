# From the repository root:
# docker build -t portfolio-frontend:local frontend
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY index.html vite.config.js ./
COPY src ./src
RUN npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine AS runtime
# Substitute only the upstream setting, preserving nginx's own variables.
ENV NGINX_ENVSUBST_FILTER=^BACKEND_URL$
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
