FROM node:20-alpine AS build

WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci

COPY . ./
ARG VITE_ECOMMERCE_AGENT_ENDPOINT=http://127.0.0.1:8087/internal/evaluation/ask
ENV VITE_ECOMMERCE_AGENT_ENDPOINT=${VITE_ECOMMERCE_AGENT_ENDPOINT}
RUN npm run build

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80
