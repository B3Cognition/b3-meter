FROM node:20-alpine
WORKDIR /app
COPY package.json ./
RUN npm install --production
COPY broker.js ./
EXPOSE 1883 1884
CMD ["node", "broker.js"]
