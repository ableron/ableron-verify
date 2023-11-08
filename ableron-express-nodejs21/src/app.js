import express from 'express'
import bodyParser from 'body-parser'
import { createAbleronMiddleware } from '@ableron/express'

const app = express()
const port = 8080

app.use(bodyParser.text({ type: 'text/*', limit: '5MB' }))
app.use(createAbleronMiddleware())

app.post('/verify', (req, res) => {
  res
    .setHeader('Cache-Control', 'max-age=600')
    .send(req.body)
})

app.listen(port, () => {
  console.log(`Listening on port ${port}`)
})
