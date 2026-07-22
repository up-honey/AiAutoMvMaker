import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { createProject, listProjects, startGeneration } from './api'
import type { CreateVideoProjectInput, ProjectStatus, VideoProject } from './types'
import './styles.css'

const DEFAULT_SCENES = [
  '비 온 뒤 네온이 반사되는 서울 골목, 천천히 전진하는 카메라',
  '한강을 따라 움직이며 도시의 불빛을 보여주는 넓은 장면',
  '도시 야경 위로 FIRE 로고가 나타나는 엔딩 타이틀',
].join('\n')

const STATUS_LABEL: Record<ProjectStatus, string> = {
  DRAFT: '초안',
  QUEUED: '대기 중',
  PROCESSING: '생성 중',
  COMPLETED: '완료',
  FAILED: '실패',
}

function App() {
  const [projects, setProjects] = useState<VideoProject[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [title, setTitle] = useState('퇴근 후 30초 여행')
  const [topic, setTopic] = useState('서울의 밤 산책')
  const [stylePrompt, setStylePrompt] = useState('cinematic, warm neon, realistic, smooth camera')
  const [aspectRatio, setAspectRatio] = useState<'16:9' | '9:16'>('9:16')
  const [sceneText, setSceneText] = useState(DEFAULT_SCENES)

  const refresh = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true)
    try {
      const nextProjects = await listProjects()
      setProjects(nextProjects)
      setSelectedId((current) => current ?? nextProjects[0]?.id ?? null)
      setError(null)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '프로젝트를 불러오지 못했습니다.')
    } finally {
      if (!quiet) setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
    const timer = window.setInterval(() => void refresh(true), 1500)
    return () => window.clearInterval(timer)
  }, [refresh])

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedId) ?? projects[0] ?? null,
    [projects, selectedId],
  )

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const scenePrompts = sceneText
      .split('\n')
      .map((scene) => scene.trim())
      .filter(Boolean)

    if (scenePrompts.length === 0) {
      setError('장면 프롬프트를 한 개 이상 입력하세요.')
      return
    }

    const input: CreateVideoProjectInput = {
      title,
      topic,
      stylePrompt,
      aspectRatio,
      scenePrompts,
    }

    setSubmitting(true)
    try {
      const created = await createProject(input)
      setSelectedId(created.id)
      await refresh(true)
      setError(null)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '프로젝트 생성에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleGenerate(projectId: string) {
    setSubmitting(true)
    try {
      await startGeneration(projectId)
      await refresh(true)
      setError(null)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '영상 생성을 시작하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="FIRE Studio 홈">
          <span className="brand-mark">F</span>
          <span>
            <strong>FIRE</strong>
            <small>Frame Intelligence Rendering Engine</small>
          </span>
        </a>
        <div className="runtime-badge"><span /> Mock provider · 비용 0원</div>
      </header>

      <section className="hero" id="top">
        <div>
          <p className="eyebrow">AI VIDEO ORCHESTRATION</p>
          <h1>아이디어를 장면으로,<br />장면을 하나의 영상으로.</h1>
          <p className="hero-copy">
            장면 생성 작업의 대기·진행·실패를 추적하는 영상 제작 파이프라인 베이스입니다.
            현재는 실제 과금 없이 전체 흐름을 검증합니다.
          </p>
        </div>
        <div className="hero-metric">
          <span>PIPELINE</span>
          <strong>{projects.length}</strong>
          <small>projects created</small>
        </div>
      </section>

      {error && <div className="error-banner" role="alert">{error}</div>}

      <section className="workspace">
        <form className="creator-panel" onSubmit={handleCreate}>
          <div className="section-heading">
            <div>
              <p className="step-number">01</p>
              <h2>새 영상 설계</h2>
            </div>
            <span>최대 12장면</span>
          </div>

          <label>
            프로젝트 제목
            <input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={100} required />
          </label>

          <label>
            영상 주제
            <textarea value={topic} onChange={(event) => setTopic(event.target.value)} rows={2} maxLength={500} required />
          </label>

          <label>
            공통 스타일
            <textarea value={stylePrompt} onChange={(event) => setStylePrompt(event.target.value)} rows={2} maxLength={1000} required />
          </label>

          <fieldset>
            <legend>화면 비율</legend>
            <div className="ratio-options">
              {(['9:16', '16:9'] as const).map((ratio) => (
                <label className={aspectRatio === ratio ? 'ratio active' : 'ratio'} key={ratio}>
                  <input
                    type="radio"
                    name="aspectRatio"
                    value={ratio}
                    checked={aspectRatio === ratio}
                    onChange={() => setAspectRatio(ratio)}
                  />
                  <span className={`ratio-icon ratio-${ratio.replace(':', '-')}`} />
                  {ratio} {ratio === '9:16' ? 'Shorts' : 'Wide'}
                </label>
              ))}
            </div>
          </fieldset>

          <label>
            장면 프롬프트 <small>한 줄이 한 장면입니다.</small>
            <textarea
              className="scene-input"
              value={sceneText}
              onChange={(event) => setSceneText(event.target.value)}
              rows={7}
              required
            />
          </label>

          <button className="primary-button" type="submit" disabled={submitting}>
            {submitting ? '처리 중…' : '프로젝트 만들기'}
            <span>→</span>
          </button>
        </form>

        <section className="monitor-panel">
          <div className="section-heading">
            <div>
              <p className="step-number">02</p>
              <h2>생성 모니터</h2>
            </div>
            <button className="text-button" onClick={() => void refresh()} type="button">새로고침</button>
          </div>

          {loading ? (
            <div className="empty-state">프로젝트를 불러오는 중입니다.</div>
          ) : projects.length === 0 ? (
            <div className="empty-state">
              <span className="empty-icon">＋</span>
              <strong>첫 프로젝트를 만들어 보세요.</strong>
              <p>왼쪽 설계를 저장하면 장면별 작업 상태가 여기에 표시됩니다.</p>
            </div>
          ) : (
            <>
              <div className="project-tabs" aria-label="프로젝트 선택">
                {projects.map((project) => (
                  <button
                    className={selectedProject?.id === project.id ? 'project-tab active' : 'project-tab'}
                    key={project.id}
                    onClick={() => setSelectedId(project.id)}
                    type="button"
                  >
                    <span>{project.title}</span>
                    <small>{STATUS_LABEL[project.status]}</small>
                  </button>
                ))}
              </div>

              {selectedProject && (
                <ProjectDetail
                  project={selectedProject}
                  submitting={submitting}
                  onGenerate={handleGenerate}
                />
              )}
            </>
          )}
        </section>
      </section>
    </main>
  )
}

interface ProjectDetailProps {
  project: VideoProject
  submitting: boolean
  onGenerate: (projectId: string) => Promise<void>
}

function ProjectDetail({ project, submitting, onGenerate }: ProjectDetailProps) {
  const completed = project.scenes.filter((scene) => scene.status === 'COMPLETED').length
  const progress = project.scenes.length === 0 ? 0 : Math.round((completed / project.scenes.length) * 100)
  const startable = project.status === 'DRAFT' || project.status === 'FAILED'

  return (
    <article className="project-detail">
      <div className="project-summary">
        <div>
          <div className="status-line">
            <span className={`status status-${project.status.toLowerCase()}`}>{STATUS_LABEL[project.status]}</span>
            <span>{project.aspectRatio}</span>
          </div>
          <h3>{project.title}</h3>
          <p>{project.topic}</p>
        </div>
        <div className="progress-ring" style={{ '--progress': `${progress * 3.6}deg` } as React.CSSProperties}>
          <span>{progress}%</span>
        </div>
      </div>

      <div className="progress-track"><span style={{ width: `${progress}%` }} /></div>
      <p className="progress-copy">완료 {completed} / 전체 {project.scenes.length} 장면</p>

      <div className="scene-list">
        {project.scenes.map((scene) => (
          <div className="scene-card" key={scene.id}>
            <span className="scene-sequence">{String(scene.sequence).padStart(2, '0')}</span>
            <div>
              <p>{scene.prompt}</p>
              <small>{scene.providerJobId ?? scene.errorCode ?? '작업 대기 중'}</small>
            </div>
            <span className={`scene-status scene-${scene.status.toLowerCase()}`}>{scene.status}</span>
          </div>
        ))}
      </div>

      <button
        className="generate-button"
        type="button"
        disabled={!startable || submitting}
        onClick={() => void onGenerate(project.id)}
      >
        {project.status === 'FAILED'
          ? '실패 장면 다시 생성'
          : project.status === 'COMPLETED'
            ? 'Mock 영상 생성 완료'
            : startable
              ? 'Mock 영상 생성 시작'
              : '생성 작업 진행 중'}
      </button>
    </article>
  )
}

export default App
