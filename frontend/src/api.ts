import type { CreateVideoProjectInput, VideoProject } from './types'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

interface ApiErrorPayload {
  message?: string
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })

  if (!response.ok) {
    const payload = (await response.json().catch(() => ({}))) as ApiErrorPayload
    throw new Error(payload.message ?? `Request failed with status ${response.status}`)
  }

  return response.json() as Promise<T>
}

export function listProjects(): Promise<VideoProject[]> {
  return request<VideoProject[]>('/api/projects')
}

export function createProject(input: CreateVideoProjectInput): Promise<VideoProject> {
  return request<VideoProject>('/api/projects', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function startGeneration(projectId: string): Promise<VideoProject> {
  return request<VideoProject>(`/api/projects/${projectId}/generate?provider=mock`, {
    method: 'POST',
  })
}
