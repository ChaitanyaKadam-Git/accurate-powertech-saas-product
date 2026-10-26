FROM node:20 AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

FROM nginx:alpine
# nginx image substitutes ${BACKEND_URL} at start-up
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/quote-ui/browser /usr/share/nginx/html
EXPOSE 80
