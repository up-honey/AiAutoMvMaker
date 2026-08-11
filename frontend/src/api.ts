import type { AssetKind, CreateVideoProjectInput, MediaAsset, ProjectRender, VideoProject } from './types'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

interface ApiErrorPayload {
  message?: string
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  if (!(init?.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
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

export function listProviders(): Promise<string[]> {
  return request<{ providers: string[] }>('/api/providers').then((payload) => payload.providers)
}

export function startGeneration(
  projectId: string,
  provider: string,
  confirmPaid = false,
): Promise<VideoProject> {
  const parameters = new URLSearchParams({ provider, confirmPaid: String(confirmPaid) })
  return request<VideoProject>(`/api/projects/${projectId}/generate?${parameters}`, {
    method: 'POST',
  })
}

export function uploadAsset(
  projectId: string,
  kind: AssetKind,
  file: File,
  sceneId?: string,
  timelinePosition?: number,
  durationMs?: number,
): Promise<MediaAsset> {
  const body = new FormData()
  body.append('kind', kind)
  if (sceneId) body.append('sceneId', sceneId)
  if (timelinePosition !== undefined) body.append('timelinePosition', String(timelinePosition))
  if (durationMs !== undefined) body.append('durationMs', String(durationMs))
  body.append('file', file)
  return request<MediaAsset>(`/api/projects/${projectId}/assets`, {
    method: 'POST',
    body,
  })
}

export function startRender(projectId: string): Promise<ProjectRender> {
  return request<ProjectRender>(`/api/projects/${projectId}/render`, { method: 'POST' })
}

export function resolveAssetUrl(contentUrl: string): string {
  return contentUrl.startsWith('/') ? `${API_BASE}${contentUrl}` : contentUrl
}
