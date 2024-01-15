import Fastify from 'fastify';
import ableron from '@ableron/fastify';
const app = Fastify({
  bodyLimit: 5 * 1024 * 1024,
  logger: true
});

app.register(ableron, {
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

app.listen({ port: 8080, host: '0.0.0.0' })
