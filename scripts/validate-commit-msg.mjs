import fs from 'node:fs'

const msgFile = process.argv[2]
const msg = fs.readFileSync(msgFile, 'utf8').trim()

const match = msg.match(/^(\w+)(?:\(([^)]+)\))?!?:\s(.+)$/)
if (!match) {
  console.error('커밋 메시지 형식 오류: type(scope): 제목')
  process.exit(1)
}

const allowed = ['feat', 'fix', 'chore', 'docs', 'test', 'refactor']
if (!allowed.includes(match[1])) {
  console.error('허용되지 않은 prefix 입니다.')
  process.exit(1)
}

const subject = match[3]
const acronyms = ['API', 'DB', 'JWT', 'CORS', 'Redis', 'Docker', 'Jenkins', 'Scouter', 'CI', 'CD', 'UI', 'UX', 'HTTP', 'HTTPS', 'SQL', 'JSON', 'OAuth', 'S3', 'MinIO']
let normalized = subject
for (const token of acronyms) normalized = normalized.replaceAll(token, '')

if (/[A-Za-z]/.test(normalized)) {
  console.error('subject에는 한국어만 사용할 수 있습니다. (허용 약어 제외)')
  process.exit(1)
}
