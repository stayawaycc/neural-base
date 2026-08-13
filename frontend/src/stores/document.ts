import { ref } from 'vue'
import { defineStore } from 'pinia'
import * as kbApi from '@/api/kb'
import * as documentApi from '@/api/document'
import type { DocumentItem, Kb, SearchResult } from '@/types'

export const useDocumentStore = defineStore('document', () => {
  const kbs = ref<Kb[]>([])
  const currentKbId = ref<number | null>(null)
  const documents = ref<DocumentItem[]>([])
  const searchResults = ref<SearchResult[] | null>(null)

  let pollTimer: number | null = null

  async function loadKbs() {
    kbs.value = await kbApi.listKbs()
  }

  async function loadDocuments() {
    documents.value = await documentApi.listDocuments(currentKbId.value ?? undefined)
  }

  async function selectKb(id: number | null) {
    currentKbId.value = id
    searchResults.value = null
    await loadDocuments()
  }

  async function upload(file: File, onProgress?: (pct: number) => void) {
    await documentApi.uploadDocument(file, currentKbId.value ?? undefined, onProgress)
    await loadDocuments()
    startPolling()
  }

  async function remove(id: number) {
    await documentApi.deleteDocument(id)
    await loadDocuments()
  }

  async function search(query: string) {
    searchResults.value = await documentApi.searchDocuments(
      query,
      currentKbId.value ?? undefined,
    )
  }

  async function createKb(payload: { name: string; description?: string; color?: string }) {
    await kbApi.createKb(payload)
    await loadKbs()
  }

  async function removeKb(id: number) {
    await kbApi.deleteKb(id)
    if (currentKbId.value === id) selectKb(null)
    await loadKbs()
  }

  function startPolling() {
    stopPolling()
    pollTimer = window.setInterval(() => {
      void (async () => {
        await loadDocuments()
        const pending = documents.value.some(
          (d) => d.status === 'PENDING' || d.status === 'PROCESSING',
        )
        if (!pending) stopPolling()
      })()
    }, 2000)
  }

  function stopPolling() {
    if (pollTimer != null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  return {
    kbs,
    currentKbId,
    documents,
    searchResults,
    loadKbs,
    loadDocuments,
    selectKb,
    upload,
    remove,
    search,
    createKb,
    removeKb,
    startPolling,
    stopPolling,
  }
})
