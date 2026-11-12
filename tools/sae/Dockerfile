FROM maven:3.9.12-eclipse-temurin-8 AS build-backend
WORKDIR /src
COPY backend/ .
RUN mvn -B -ntp -pl ruoyi-admin -am -Dmaven.test.skip=true package

FROM eclipse-temurin:8-jre-jammy AS backend
ENV TZ=Asia/Shanghai JAVA_OPTS="-Xms256m -Xmx640m"
WORKDIR /app
COPY --from=build-backend /src/ruoyi-admin/target/ruoyi-admin.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

FROM node:22.19.0-bookworm-slim AS build-web-pc
WORKDIR /src
COPY frontend/pc/package.json ./
RUN npm install --no-package-lock
COPY frontend/pc/ .
RUN npm run build:prod

FROM node:22.19.0-bookworm-slim AS build-web-h5
WORKDIR /src
COPY frontend/uni-app/package*.json ./
RUN npm ci
COPY frontend/uni-app/ .
RUN UNI_INPUT_DIR="$PWD" VITE_API_BASE_URL= npx --no-install uni build -p h5 \
    && mkdir -p dist/build/h5/static \
    && cp -r static/. dist/build/h5/static/

FROM nginx:1.26-alpine AS nginx-runtime
ENV NGINX_ENVSUBST_FILTER='^GATEWAY_UPSTREAM$'

FROM nginx-runtime AS web-pc
COPY --from=build-web-pc /src/dist/ /usr/share/nginx/html/
COPY .claude/skills/yoga-cloud-deploy/config/nginx/web-pc.conf.template /etc/nginx/templates/default.conf.template

FROM nginx-runtime AS web-h5
COPY --from=build-web-h5 /src/dist/build/h5/ /usr/share/nginx/html/
COPY .claude/skills/yoga-cloud-deploy/config/nginx/web-h5.conf.template /etc/nginx/templates/default.conf.template
