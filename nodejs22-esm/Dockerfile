FROM node:22-alpine
COPY . /opt/app
WORKDIR /opt/app
RUN npm install

ENTRYPOINT ["npm", "run", "start"]
