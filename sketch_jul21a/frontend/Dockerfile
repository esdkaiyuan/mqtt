# 构建阶段
FROM node:20-alpine AS build-stage

WORKDIR /app

# 复制依赖清单（含 lockfile），按锁定版本精确安装
COPY package*.json ./

# 使用 npm ci 保证构建可复现（严格按 package-lock.json 安装）
RUN npm ci

# 复制源代码
COPY . .

# 构建生产版本
RUN npm run build

# 生产阶段
FROM nginx:alpine as production-stage

# 复制构建产物
COPY --from=build-stage /app/dist /usr/share/nginx/html

# 复制nginx配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露端口
EXPOSE 3000

# 启动nginx
CMD ["nginx", "-g", "daemon off;"]
