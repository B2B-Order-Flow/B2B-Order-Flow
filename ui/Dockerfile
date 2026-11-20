# Интерфейс и точка входа: сборка Vite, раздача статики и прокси в шлюз через Nginx
# (docs/system/nfr.md, разделы 6-8). Контекст сборки: папка ui/.

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.30-alpine
# Шаблон: при старте контейнера envsubst подставляет RATE_API и RATE_LOGIN
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
# Страница ошибки копируется последней, чтобы файл из dist её не затёр
COPY nginx/50x.html /usr/share/nginx/html/50x.html
EXPOSE 8080
