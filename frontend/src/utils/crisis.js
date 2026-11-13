/** 危机预警的目标类型文案：新增类型（如 ASSESSMENT）只需在这里加一行 */
const TARGET_LABELS = {
  DIARY: '私密日记',
  POST: '树洞帖子',
  COMMENT: '树洞评论',
  ASSESSMENT: '心理自评'
}

export function crisisTargetLabel(type) {
  return TARGET_LABELS[type] || type || ''
}
