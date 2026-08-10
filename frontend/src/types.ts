export type ProjectStatus = 'DRAFT' | 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
export type SceneStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
export type AssetKind = 'IMAGE' | 'VIDEO' | 'AUDIO'
export type RenderPreset = 'CLEAN' | 'ROMANTIC' | 'FAIRYTALE_PARK' | 'CINEMATIC'
export type RenderStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface VideoScene {
  id: string
  sequence: number
  prompt: string
  status: SceneStatus
  providerJobId: string | null
  previewUri: string | null
  errorCode: string | null
  providerModel: string | null
  estimatedCostUsd: number | null
  submittedAt: string | null
  completedAt: string | null
}

export interface VideoProject {
  id: string
  title: string
  topic: string
  stylePrompt: string
  aspectRatio: '16:9' | '9:16'
  renderPreset: RenderPreset
  status: ProjectStatus
  errorCode: string | null
  providerName: string | null
  createdAt: string
  updatedAt: string
  scenes: VideoScene[]
  assets: MediaAsset[]
  render: ProjectRender | null
}

export interface MediaAsset {
  id: string
  sceneId: string | null
  kind: AssetKind
  originalFilename: string
  contentType: string
  sizeBytes: number
  sha256: string
  contentUrl: string
  timelinePosition: number
  durationMs: number | null
  createdAt: string
}

export interface ProjectRender {
  id: string
  status: RenderStatus
  errorCode: string | null
  contentUrl: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateVideoProjectInput {
  title: string
  topic: string
  stylePrompt: string
  aspectRatio: '16:9' | '9:16'
  renderPreset: RenderPreset
  scenePrompts: string[]
}
