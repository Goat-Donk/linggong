import request from '@/utils/request'

// 文件上传接口（对照后端 UploadController）

// 上传图片（multipart/form-data），成功 data 为图片访问 URL（/uploads/{文件名}）
export function uploadImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/upload/image', formData)
}
