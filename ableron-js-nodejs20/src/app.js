const express = require('express')
const { Ableron } = require('ableron')
const bodyParser = require('body-parser')
const app = express()
const port = 8080

const ableron = new Ableron('ableron test')

app.use(bodyParser.text({ type: 'text/*' }))

app.post('/verify', (req, res) => {
  res.setHeader('Content-Type', 'text/html; charset=utf-8')
  res.send(req.body)
})

app.listen(port, () => {
  console.log(`Example app listening on port ${port}`)
})
