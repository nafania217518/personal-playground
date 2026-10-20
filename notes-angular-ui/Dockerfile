# Stage 1: build the Angular app
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json .npmrc ./
# npm ci can fail on a peer-dependency conflict. npm install with legacy-peer-deps is the fallback.
RUN npm ci || npm install
COPY . .
RUN npx ng build --configuration production

# Stage 2: serve with nginx
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/notes-angular-ui/browser /usr/share/nginx/html
EXPOSE 80
