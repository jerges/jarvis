# Build stage — Angular 19 (application builder → dist/jarvis-ui/browser/)
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install --legacy-peer-deps; fi \
	&& npm install --no-save --legacy-peer-deps @angular-devkit/build-angular@^19 @angular/build@^19

COPY . .
RUN npm run build -- --configuration=production

# Runtime stage — nginx sirve los estáticos y proxea /api/ y /webhook/
FROM nginx:alpine

COPY --from=builder /app/dist/jarvis-ui/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
