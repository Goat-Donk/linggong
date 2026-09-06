import request from '@/utils/request'

// 查询所有岗位分类（data 为 JobCategory[]：id/name/sort）
export function getCategories() {
  return request.get('/job-category/list')
}
