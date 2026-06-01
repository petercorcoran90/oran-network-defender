FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
# Rendered to /etc/nginx/conf.d/default.conf at startup with envsubst.
# Only BACKEND_HOST is substituted so nginx's own $variables are left intact.
COPY nginx.conf /etc/nginx/templates/default.conf.template
ENV BACKEND_HOST=backend
ENV NGINX_ENVSUBST_FILTER=BACKEND_HOST
EXPOSE 80
