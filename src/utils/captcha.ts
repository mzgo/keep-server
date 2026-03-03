// SVG 验证码生成器 - 纯服务端生成，不依赖外部库

const CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'

function randomChar(): string {
  return CHARS[Math.floor(Math.random() * CHARS.length)]
}

function randomColor(): string {
  const r = Math.floor(Math.random() * 100 + 50)
  const g = Math.floor(Math.random() * 100 + 50)
  const b = Math.floor(Math.random() * 100 + 50)
  return `rgb(${r},${g},${b})`
}

function randomLine(width: number, height: number): string {
  const x1 = Math.floor(Math.random() * width)
  const y1 = Math.floor(Math.random() * height)
  const x2 = Math.floor(Math.random() * width)
  const y2 = Math.floor(Math.random() * height)
  return `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${randomColor()}" stroke-width="1" opacity="0.5"/>`
}

export function generateCaptcha(length: number = 4): { text: string; svg: string } {
  const width = 120
  const height = 40
  let text = ''
  let chars = ''

  for (let i = 0; i < length; i++) {
    const char = randomChar()
    text += char

    const x = 15 + i * 25
    const y = 25 + Math.floor(Math.random() * 10 - 5)
    const rotate = Math.floor(Math.random() * 30 - 15)
    const fontSize = 20 + Math.floor(Math.random() * 6)

    chars += `<text x="${x}" y="${y}" fill="${randomColor()}" font-size="${fontSize}" font-family="Arial,sans-serif" font-weight="bold" transform="rotate(${rotate} ${x} ${y})">${char}</text>`
  }

  let lines = ''
  for (let i = 0; i < 4; i++) {
    lines += randomLine(width, height)
  }

  // 增加干扰点
  let dots = ''
  for (let i = 0; i < 20; i++) {
    const cx = Math.floor(Math.random() * width)
    const cy = Math.floor(Math.random() * height)
    dots += `<circle cx="${cx}" cy="${cy}" r="1" fill="${randomColor()}" opacity="0.5"/>`
  }

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
<rect width="100%" height="100%" fill="#f0f0f0"/>
${lines}${dots}${chars}
</svg>`

  return { text: text.toLowerCase(), svg }
}
