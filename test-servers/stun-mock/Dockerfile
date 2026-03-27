FROM node:20-alpine
WORKDIR /app
COPY package.json ./
RUN npm install --production
COPY server.js ./
EXPOSE 3478/udp
EXPOSE 8087
CMD ["node", "server.js"]
