import Fastify from 'fastify';
import ableronPlugin from '@ableron/fastify';
const app = Fastify();

app.register(ableronPlugin, {
  ableron: {
    cacheVaryByRequestHeaders: ['Accept-Language'],
    logger: console
  }
});

app.post('/verify', (request, reply) => {
  reply
    .header('Cache-Control', 'max-age=600')
    .header('Content-Type', 'text/html; charset=utf-8')
    .send(request.body);
});

app.listen({ port: 8080 }, console.log);
