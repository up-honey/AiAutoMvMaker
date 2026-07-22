export type ProjectStatus = 'DRAFT' | 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
export type SceneStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface VideoScene {
  id: string
  sequence: number
  prompt: string
  status: SceneStatus
  providerJobId: string | null
  previewUri: string | null
  errorCode: string | null
}

export interface VideoProject {
  id: string
  title: string
  topic: string
  stylePrompt: string
  aspectRatio: '16:9' | '9:16'
  status: ProjectStatus
  errorCode: string | null
  providerName: string | null
  createdAt: string
  updatedAt: string
  scenes: VideoScene[]
}

export interface CreateVideoProjectInput {
  title: string
  topic: string
  stylePrompt: string
  aspectRatio: '16:9' | '9:16'
  scenePrompts: string[]
}
