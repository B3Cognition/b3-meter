FROM node:20-alpine
WORKDIR /app
COPY package.json ./
RUN npm install --production
COPY greeter.proto ./
COPY server.js ./
EXPOSE 50051 8085
CMD ["node", "server.js"]
