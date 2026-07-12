interface ImageOptions {
  maxSize?: number
  quality?: number
}

export async function imageFileToDataUrl(file: File, options: ImageOptions = {}) {
  if (!file.type.startsWith('image/')) throw new Error('请选择图片文件')
  if (file.size > 8 * 1024 * 1024) throw new Error('图片大小不能超过 8MB')

  const maxSize = options.maxSize || 1200
  const quality = options.quality || 0.84
  const objectUrl = URL.createObjectURL(file)

  try {
    const image = await new Promise<HTMLImageElement>((resolve, reject) => {
      const element = new Image()
      element.onload = () => resolve(element)
      element.onerror = () => reject(new Error('图片读取失败'))
      element.src = objectUrl
    })
    const scale = Math.min(1, maxSize / Math.max(image.naturalWidth, image.naturalHeight))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(image.naturalWidth * scale))
    canvas.height = Math.max(1, Math.round(image.naturalHeight * scale))
    const context = canvas.getContext('2d')
    if (!context) throw new Error('当前浏览器不支持图片处理')
    context.drawImage(image, 0, 0, canvas.width, canvas.height)
    return canvas.toDataURL('image/webp', quality)
  } finally {
    URL.revokeObjectURL(objectUrl)
  }
}
