<template>
  <div class="sensitive">
    <div class="sensitive__tip">
      命中的词会让树洞内容直接进入「待审核」状态，并对用户提示换个说法。
      改动立即生效，不用重启服务。
    </div>

    <van-search
      v-model="keyword"
      placeholder="搜索已有词"
      @search="reload"
      @clear="reload"
    />

    <div class="add">
      <van-field
        v-model="word"
        placeholder="新增敏感词，最多 50 字"
        maxlength="50"
        clearable
        @keyup.enter="onAdd"
      />
      <van-button size="small" type="primary" :loading="adding" @click="onAdd">添加</van-button>
    </div>

    <van-list
      v-model:loading="loading"
      v-model:error="error"
      :finished="finished"
      finished-text="没有更多了"
      error-text="加载失败，点击重新加载"
      @load="onLoad"
    >
      <van-empty v-if="finished && !items.length" description="还没有敏感词" />
      <van-cell v-for="w in items" :key="w.id" :title="w.word" :label="createdAt(w.createdAt)">
        <template #right-icon>
          <van-icon name="delete-o" class="del" @click="onDelete(w)" />
        </template>
      </van-cell>
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { listSensitiveWords, addSensitiveWord, deleteSensitiveWord } from '../../api/admin'

const keyword = ref('')
const word = ref('')
const adding = ref(false)

const items = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)

function createdAt(dt) {
  return dt ? `加入于 ${dt.replace('T', ' ').slice(0, 16)}` : ''
}

async function onLoad() {
  try {
    const data = await listSensitiveWords({
      page: page.value,
      size: 20,
      keyword: keyword.value || undefined
    })
    items.value.push(...data.records)
    if (data.records.length < 20 || items.value.length >= data.total) {
      finished.value = true
    } else {
      page.value += 1
    }
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

/** 搜索条件变了要从头拉：清空列表并把 van-list 的自动加载打开 */
function reload() {
  items.value = []
  page.value = 1
  finished.value = false
  error.value = false
  loading.value = true
  onLoad()
}

async function onAdd() {
  const value = word.value.trim()
  if (!value) {
    showToast('请输入要拦截的词')
    return
  }
  adding.value = true
  try {
    await addSensitiveWord(value)
    word.value = ''
    showToast('已添加')
    reload()
  } finally {
    adding.value = false
  }
}

async function onDelete(item) {
  try {
    await showConfirmDialog({
      title: '移除敏感词',
      message: `「${item.word}」将不再被拦截，确定移除吗？`,
      confirmButtonText: '移除'
    })
  } catch {
    return
  }
  await deleteSensitiveWord(item.id)
  items.value = items.value.filter((x) => x.id !== item.id)
  showToast('已移除')
}
</script>

<style scoped>
.sensitive__tip {
  margin: 10px 0 4px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--xinyu-accent-soft);
  font-size: 12px;
  line-height: 1.7;
  color: var(--van-text-color-2);
}
.add {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 4px 8px 16px;
}
.del {
  font-size: 18px;
  color: var(--van-danger-color);
  cursor: pointer;
}
</style>
