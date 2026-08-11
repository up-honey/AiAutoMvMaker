import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { createProject, listProjects, listProviders, resolveAssetUrl, startGeneration, startRender, uploadAsset } from './api'
import type { AssetKind, CreateVideoProjectInput, MediaAsset, ProjectStatus, RenderPreset, VideoProject } from './types'
import './styles.css'

const STATUS_LABEL: Record<ProjectStatus, string> = {
  DRAFT: '초안',
  QUEUED: '대기 중',
  PROCESSING: '생성 중',
  COMPLETED: '완료',
  FAILED: '실패',
}

const VISUAL_ACCEPT = 'image/jpeg,image/png,image/webp,image/gif,video/mp4,video/webm,video/quicktime'
const AUDIO_ACCEPT = 'audio/mpeg,audio/wav,audio/x-wav,audio/ogg,audio/mp4,audio/aac,.mp3,.wav,.ogg,.m4a,.aac'

const RENDER_PRESETS: Array<{ value: RenderPreset; label: string; description: string }> = [
  { value: 'ROMANTIC', label: '로맨틱 웨딩', description: '따뜻하고 부드러운 색감' },
  { value: 'FAIRYTALE_PARK', label: '동화 테마파크', description: '화사한 색과 꿈같은 분위기' },
  { value: 'CINEMATIC', label: '시네마틱', description: '대비가 깊은 영화 색감' },
  { value: 'CLEAN', label: '클린', description: '원본에 가까운 자연스러운 톤' },
]

function formatFileSize(bytes: number) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function visualKind(file: File): AssetKind {
  return file.type.startsWith('image/') || /\.(png|jpe?g|webp|gif)$/i.test(file.name) ? 'IMAGE' : 'VIDEO'
}

function App() {
  const [projects, setProjects] = useState<VideoProject[]>([])
  const [providers, setProviders] = useState<string[]>(['mock'])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [title, setTitle] = useState('')
  const [topic, setTopic] = useState('')
  const [aspectRatio, setAspectRatio] = useState<'16:9' | '9:16'>('9:16')
  const [styleChoice, setStyleChoice] = useState<RenderPreset | 'CUSTOM'>('ROMANTIC')
  const [customStyle, setCustomStyle] = useState('')
  const [imagesPerSecond, setImagesPerSecond] = useState(2)
  const [visualFiles, setVisualFiles] = useState<File[]>([])
  const [soundtrackMode, setSoundtrackMode] = useState<'AI' | 'UPLOAD'>('AI')
  const [aiSoundtrackPrompt, setAiSoundtrackPrompt] = useState('')
  const [soundtrack, setSoundtrack] = useState<File | null>(null)
  const [submitStatus, setSubmitStatus] = useState<string | null>(null)
  const [openSettings, setOpenSettings] = useState<'VIDEO' | 'OUTPUT' | null>(null)

  const scenePrompts = useMemo(
    () => topic.trim() ? [topic.trim()] : [],
    [topic],
  )
  const imageFileCount = useMemo(
    () => visualFiles.filter((file) => visualKind(file) === 'IMAGE').length,
    [visualFiles],
  )

  const renderPreset: RenderPreset = styleChoice === 'CUSTOM' ? 'CLEAN' : styleChoice
  const selectedStyleLabel = styleChoice === 'CUSTOM'
    ? '직접 입력'
    : RENDER_PRESETS.find((preset) => preset.value === styleChoice)?.label ?? ''
  const selectedStyleDescription = styleChoice === 'CUSTOM'
    ? '직접 입력한 분위기는 AI 생성 프롬프트에 반영되며, 최종 합성은 자연스러운 원본 톤을 사용합니다.'
    : RENDER_PRESETS.find((preset) => preset.value === styleChoice)?.description ?? ''

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
    void listProviders().then((availableProviders) => {
      if (availableProviders.length > 0) setProviders(availableProviders)
    }).catch(() => undefined)
    const timer = window.setInterval(() => void refresh(true), 1500)
    return () => window.clearInterval(timer)
  }, [refresh])

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedId) ?? projects[0] ?? null,
    [projects, selectedId],
  )

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (scenePrompts.length === 0) {
      setError('영상 프롬프트를 입력하세요.')
      return
    }
    if (styleChoice === 'CUSTOM' && !customStyle.trim()) {
      setError('직접 입력할 영상 분위기를 작성해 주세요.')
      return
    }

    const scenePositions = new Map<number, number>()
    const selectedVisuals = visualFiles.map((file, fileIndex) => {
      const index = Math.min(
        scenePrompts.length - 1,
        Math.floor((fileIndex * scenePrompts.length) / Math.max(visualFiles.length, 1)),
      )
      const position = scenePositions.get(index) ?? 0
      scenePositions.set(index, position + 1)
      return { index, position, file }
    })
    if (selectedVisuals.length > 300) {
      setError('이미지와 동영상은 프로젝트당 최대 300개까지 사용할 수 있습니다.')
      return
    }
    const oversizedVisual = selectedVisuals.find(({ file }) => (
      visualKind(file) === 'IMAGE' ? file.size > 15 * 1024 * 1024 : file.size > 200 * 1024 * 1024
    ))
    if (oversizedVisual) {
      setError(`${oversizedVisual.file.name}: 이미지는 15MB, 동영상은 200MB 이하만 사용할 수 있습니다.`)
      return
    }
    const selectedSoundtrack = soundtrackMode === 'UPLOAD' ? soundtrack : null
    if (selectedSoundtrack && selectedSoundtrack.size > 30 * 1024 * 1024) {
      setError(`${selectedSoundtrack.name}: 배경음악은 30MB 이하만 사용할 수 있습니다.`)
      return
    }

    const input: CreateVideoProjectInput = {
      title,
      topic,
      stylePrompt: [
        topic,
        `영상 분위기: ${styleChoice === 'CUSTOM'
          ? customStyle.trim()
          : `${RENDER_PRESETS.find((preset) => preset.value === styleChoice)?.label} · ${selectedStyleDescription}`}`,
        soundtrackMode === 'AI'
          ? `배경음악: ${aiSoundtrackPrompt.trim() || '영상 분위기에 어울리도록 AI가 생성합니다.'}`
          : null,
      ].filter(Boolean).join('\n'),
      aspectRatio,
      renderPreset,
      scenePrompts,
    }

    setSubmitting(true)
    setSubmitStatus('프로젝트 저장 중')
    let createdProjectId: string | null = null
    try {
      const created = await createProject(input)
      createdProjectId = created.id
      setSelectedId(created.id)

      const uploadCount = selectedVisuals.length + (selectedSoundtrack ? 1 : 0)
      let uploaded = 0
      let nextUpload = 0
      const uploadWorkers = Array.from({ length: Math.min(4, selectedVisuals.length) }, async () => {
        while (nextUpload < selectedVisuals.length) {
          const job = selectedVisuals[nextUpload]
          nextUpload += 1
          await uploadAsset(
            created.id,
            visualKind(job.file),
            job.file,
            created.scenes[job.index].id,
            job.position,
            visualKind(job.file) === 'IMAGE' ? Math.round(1000 / imagesPerSecond) : undefined,
          )
          uploaded += 1
          setSubmitStatus(`미디어 업로드 ${uploaded} / ${uploadCount}`)
        }
      })
      await Promise.all(uploadWorkers)
      if (selectedSoundtrack) {
        setSubmitStatus(`미디어 업로드 ${uploaded + 1} / ${uploadCount}`)
        await uploadAsset(created.id, 'AUDIO', selectedSoundtrack)
      }

      setVisualFiles([])
      setSoundtrack(null)
      await refresh(true)
      setError(null)
    } catch (requestError) {
      const message = requestError instanceof Error ? requestError.message : '프로젝트 생성에 실패했습니다.'
      setError(createdProjectId
        ? `프로젝트는 초안으로 저장됐지만 일부 미디어를 업로드하지 못했습니다. ${message}`
        : message)
    } finally {
      setSubmitting(false)
      setSubmitStatus(null)
    }
  }

  async function handleGenerate(projectId: string, provider: string, confirmPaid: boolean) {
    setSubmitting(true)
    try {
      await startGeneration(projectId, provider, confirmPaid)
      await refresh(true)
      setError(null)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '영상 생성을 시작하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleRender(projectId: string) {
    setSubmitting(true)
    try {
      await startRender(projectId)
      await refresh(true)
      setError(null)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '최종 렌더링을 시작하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="app-shell" id="top">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="AI 영상 만들기 홈">
          <span className="brand-mark">▶</span>
          <span>
            <strong>AI 영상 만들기</strong>
            <small>사진·동영상·음악으로 원하는 영상을 완성하세요</small>
          </span>
        </a>
        <div className="runtime-badge"><span /> 안전한 Mock 제작 · 유료 생성은 확인 후 진행</div>
      </header>

      {error && <div className="error-banner" role="alert">{error}</div>}

      <section className="workspace">
        <form className="creator-panel" onSubmit={handleCreate}>
          <div className="section-heading">
            <div>
              <p className="step-number">01</p>
              <h2>새 영상 설계</h2>
            </div>
            <span>간편 제작</span>
          </div>

          <label className="title-field">
            프로젝트 제목 <small>프로젝트 목록과 완성 영상의 이름으로 사용됩니다.</small>
            <input
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="영상 제목을 입력해 주세요"
              maxLength={100}
              required
            />
          </label>

          <label className="video-prompt-field">
            영상 프롬프트 <small>주제와 원하는 분위기를 함께 적어주세요.</small>
            <textarea
              value={topic}
              onChange={(event) => setTopic(event.target.value)}
              placeholder="예: 커플의 연애부터 결혼식까지 이어지는 따뜻한 웨딩 영상. 화사한 동화 테마파크 분위기와 감동적인 음악으로 만들어 주세요."
              rows={4}
              maxLength={500}
              required
            />
          </label>

          <section className="media-builder upload-field" aria-labelledby="media-builder-title">
            <div className="media-builder-heading">
              <div>
                <strong id="media-builder-title">이미지·동영상 업로드</strong>
                <small>여러 파일을 한 번에 선택하면 선택한 순서대로 영상에 배치됩니다.</small>
              </div>
              <span>{visualFiles.length}개 파일</span>
            </div>

            <label className={visualFiles.length ? 'visual-upload-zone selected' : 'visual-upload-zone'}>
              <input
                key={visualFiles.length === 0 ? 'empty-visuals' : 'selected-visuals'}
                type="file"
                accept={VISUAL_ACCEPT}
                multiple
                disabled={submitting}
                onChange={(event) => {
                  const nextFiles = Array.from(event.target.files ?? [])
                  if (nextFiles.length) setVisualFiles(nextFiles)
                  event.target.value = ''
                }}
              />
              <span className="visual-upload-icon" aria-hidden="true">＋</span>
              <span className="visual-upload-copy">
                <strong>
                  {visualFiles.length
                    ? `${visualFiles.length}개 파일이 선택되었습니다`
                    : '이미지, 동영상을 업로드하세요'}
                </strong>
                <small>
                  {visualFiles.length
                    ? `${visualFiles.slice(0, 3).map((file) => file.name).join(' · ')}${visualFiles.length > 3 ? ` 외 ${visualFiles.length - 3}개` : ''}`
                    : 'JPG, PNG, WebP, GIF, MP4, WebM · 최대 300개'}
                </small>
              </span>
            </label>
          </section>

          <div className="settings-popovers">
            <div className={`settings-popover video-settings${openSettings === 'VIDEO' ? ' open' : ''}`}>
              <button
                className="settings-popover-trigger"
                type="button"
                aria-expanded={openSettings === 'VIDEO'}
                aria-controls="video-settings-panel"
                onClick={() => setOpenSettings((current) => current === 'VIDEO' ? null : 'VIDEO')}
              >
                <span>
                  <strong>영상 상세 설정</strong>
                  <small>영상 분위기, 사진 전환 속도와 배경음악을 조정하세요.</small>
                </span>
                <span className="settings-popover-value">
                  {selectedStyleLabel}{imageFileCount > 0 ? ` · 초당 ${imagesPerSecond}장` : ''} · {soundtrackMode === 'AI' ? 'AI 음악' : '직접 음악'}
                </span>
              </button>

              {openSettings === 'VIDEO' && (
                <div id="video-settings-panel" className="settings-popover-panel video-settings-panel" role="group" aria-label="영상 상세 설정">
                  <fieldset className="mood-field">
                    <legend>영상 분위기 <small>전체 영상의 색감과 연출 방향을 선택합니다.</small></legend>
                    <select
                      className="mood-select"
                      aria-label="영상 분위기"
                      value={styleChoice}
                      onChange={(event) => setStyleChoice(event.target.value as RenderPreset | 'CUSTOM')}
                    >
                      {RENDER_PRESETS.map((preset) => (
                        <option value={preset.value} key={preset.value}>{preset.label}</option>
                      ))}
                      <option value="CUSTOM">직접 입력</option>
                    </select>
                    <p className="mood-description">{selectedStyleDescription}</p>
                    {styleChoice === 'CUSTOM' && (
                      <label className="custom-style-field">
                        원하는 분위기 직접 입력
                        <textarea
                          value={customStyle}
                          onChange={(event) => setCustomStyle(event.target.value)}
                          placeholder="예: 파스텔 색감의 동화 같은 분위기, 따뜻한 햇살과 부드러운 카메라 움직임"
                          rows={3}
                          maxLength={300}
                        />
                      </label>
                    )}
                  </fieldset>

                  {imageFileCount > 0 && (
                    <label className="speed-field">
                      사진 전환 속도 <small>선택한 이미지 {imageFileCount}장에 적용됩니다. 숫자가 클수록 빠르게 전환됩니다.</small>
                      <div className="speed-control">
                        <input
                          type="range"
                          min="0.5"
                          max="5"
                          step="0.5"
                          value={imagesPerSecond}
                          onChange={(event) => setImagesPerSecond(Number(event.target.value))}
                        />
                        <strong>초당 {imagesPerSecond}장</strong>
                      </div>
                    </label>
                  )}

                  <fieldset className="soundtrack-choice">
                    <legend>배경음악 <small>AI 생성 또는 가지고 있는 음악 파일 중 하나를 선택합니다.</small></legend>
                    <div className="audio-mode-options">
                      <label className={soundtrackMode === 'AI' ? 'audio-mode active' : 'audio-mode'}>
                        <input
                          type="radio"
                          name="soundtrackMode"
                          value="AI"
                          checked={soundtrackMode === 'AI'}
                          onChange={() => {
                            setSoundtrackMode('AI')
                            setSoundtrack(null)
                          }}
                        />
                        <span>
                          <strong>AI로 만들기</strong>
                          <small>원하는 음악을 글로 요청</small>
                        </span>
                      </label>
                      <label className={soundtrackMode === 'UPLOAD' ? 'audio-mode active' : 'audio-mode'}>
                        <input
                          type="radio"
                          name="soundtrackMode"
                          value="UPLOAD"
                          checked={soundtrackMode === 'UPLOAD'}
                          onChange={() => setSoundtrackMode('UPLOAD')}
                        />
                        <span>
                          <strong>직접 업로드</strong>
                          <small>가지고 있는 음악 파일 사용</small>
                        </span>
                      </label>
                    </div>

                    {soundtrackMode === 'AI' && (
                      <label className="ai-soundtrack-prompt">
                        AI 배경음악 요청 <small>원하는 분위기, 악기나 템포를 입력해 주세요.</small>
                        <input
                          value={aiSoundtrackPrompt}
                          onChange={(event) => setAiSoundtrackPrompt(event.target.value)}
                          placeholder="예: 따뜻한 피아노와 스트링이 어우러진 감동적인 웨딩 음악"
                          maxLength={300}
                        />
                      </label>
                    )}

                    {soundtrackMode === 'UPLOAD' && (
                      <div className={soundtrack ? 'soundtrack-upload selected' : 'soundtrack-upload'}>
                        <span className="soundtrack-icon">♪</span>
                        <div className="upload-copy">
                          <strong>{soundtrack?.name ?? '배경음악 파일을 선택해 주세요'}</strong>
                          <small>{soundtrack ? formatFileSize(soundtrack.size) : 'MP3, WAV, M4A, OGG · 최대 30MB'}</small>
                        </div>
                        <label className="file-picker">
                          {soundtrack ? '변경' : '파일 선택'}
                          <input
                            key={soundtrack?.name ?? 'empty-soundtrack'}
                            type="file"
                            accept={AUDIO_ACCEPT}
                            disabled={submitting}
                            onChange={(event) => setSoundtrack(event.target.files?.[0] ?? null)}
                          />
                        </label>
                        {soundtrack && (
                          <button
                            className="remove-file"
                            type="button"
                            aria-label={`${soundtrack.name} 제거`}
                            onClick={() => setSoundtrack(null)}
                          >×</button>
                        )}
                      </div>
                    )}
                  </fieldset>
                </div>
              )}
            </div>

            <div className={`settings-popover output-settings${openSettings === 'OUTPUT' ? ' open' : ''}`}>
              <button
                className="settings-popover-trigger"
                type="button"
                aria-expanded={openSettings === 'OUTPUT'}
                aria-controls="output-settings-panel"
                onClick={() => setOpenSettings((current) => current === 'OUTPUT' ? null : 'OUTPUT')}
              >
                <span>
                  <strong>출력 설정</strong>
                  <small>해상도, 길이, 화면 비율과 출력 개수를 확인하세요.</small>
                </span>
                <span className="settings-popover-value">720P · 자동 길이 · {aspectRatio} · 1개 출력</span>
              </button>

              {openSettings === 'OUTPUT' && (
                <div id="output-settings-panel" className="settings-popover-panel output-settings-panel" role="group" aria-label="출력 설정">
              <div className="setting-block">
                <div className="setting-heading">
                  <strong>해상도</strong>
                  <small>최종 MP4의 화면 크기입니다. 현재 안정적인 720P 렌더를 지원합니다.</small>
                </div>
                <div className="setting-segments" aria-label="해상도">
                  <button className="setting-segment active" type="button" aria-pressed="true">720P</button>
                  <button className="setting-segment" type="button" disabled title="1080P 렌더링은 준비 중입니다.">1080P · 준비 중</button>
                </div>
              </div>

              <div className="setting-block">
                <div className="setting-heading">
                  <strong>영상 길이</strong>
                  <small>업로드한 사진 노출 시간과 동영상 재생 시간을 합산해 자동 결정됩니다.</small>
                </div>
                <div className="fixed-setting-value">소스 길이에 맞춤</div>
              </div>

              <fieldset className="setting-block ratio-setting">
                <legend className="setting-heading">
                  <strong>화면 비율</strong>
                  <small>쇼츠·릴스는 세로형, 유튜브와 TV는 가로형이 적합합니다.</small>
                </legend>
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
                      {ratio} {ratio === '9:16' ? '세로형' : '가로형'}
                    </label>
                  ))}
                </div>
              </fieldset>

              <div className="setting-block">
                <div className="setting-heading">
                  <strong>출력 개수</strong>
                  <small>한 번의 렌더링에서 생성할 최종 영상 수입니다.</small>
                </div>
                <div className="setting-segments output-count-options" aria-label="출력 개수">
                  <button className="setting-segment active" type="button" aria-pressed="true">1개</button>
                  {[2, 3, 4].map((count) => (
                    <button className="setting-segment" type="button" disabled title="다중 출력은 준비 중입니다." key={count}>{count}개</button>
                  ))}
                </div>
              </div>

              <p className="settings-note">현재 제공되는 옵션만 활성화했습니다. 준비 중인 옵션은 렌더 파이프라인 지원 후 사용할 수 있습니다.</p>
                </div>
              )}
            </div>
          </div>

          <button className="primary-button" type="submit" disabled={submitting}>
            {submitting ? (submitStatus ?? '처리 중…') : 'AI 영상 만들기'}
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

          <div className={loading || projects.length === 0 ? 'monitor-content' : 'monitor-content has-projects'}>
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
                  key={selectedProject.id}
                  project={selectedProject}
                  providers={providers}
                  submitting={submitting}
                  onGenerate={handleGenerate}
                  onRender={handleRender}
                />
              )}
              </>
            )}
          </div>
        </section>
      </section>
    </main>
  )
}

interface ProjectDetailProps {
  project: VideoProject
  providers: string[]
  submitting: boolean
  onGenerate: (projectId: string, provider: string, confirmPaid: boolean) => Promise<void>
  onRender: (projectId: string) => Promise<void>
}

function ProjectDetail({ project, providers, submitting, onGenerate, onRender }: ProjectDetailProps) {
  const [generationProvider, setGenerationProvider] = useState(
    project.providerName && providers.includes(project.providerName) ? project.providerName : 'mock',
  )
  const [paidConfirmed, setPaidConfirmed] = useState(false)
  const completed = project.scenes.filter((scene) => scene.status === 'COMPLETED').length
  const progress = project.scenes.length === 0 ? 0 : Math.round((completed / project.scenes.length) * 100)
  const startable = project.status === 'DRAFT' || project.status === 'FAILED'
  const soundtrack = project.assets.filter((asset) => asset.kind === 'AUDIO').at(-1) ?? null
  const visualCount = project.assets.filter((asset) => asset.kind !== 'AUDIO').length
  const generatedCount = project.scenes.filter(
    (scene) => scene.status === 'COMPLETED' && scene.previewUri?.startsWith('/'),
  ).length
  const paidProvider = generationProvider !== 'mock'
  const providerLabel = generationProvider === 'gemini' ? 'Google Gemini Omni Flash' : '안전한 Mock'

  useEffect(() => {
    if (project.providerName && providers.includes(project.providerName)) {
      setGenerationProvider(project.providerName)
    }
  }, [project.providerName, providers])

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

      <div className="asset-summary">
        <div>
          <span>입력 미디어</span>
          <strong>{visualCount}개 타임라인 소스 · {RENDER_PRESETS.find((preset) => preset.value === project.renderPreset)?.label}</strong>
        </div>
        {soundtrack ? (
          <div className="soundtrack-player">
            <div>
              <span>배경음악</span>
              <strong>{soundtrack.originalFilename}</strong>
            </div>
            <audio controls preload="metadata" src={resolveAssetUrl(soundtrack.contentUrl)} />
          </div>
        ) : (
          <div className="no-soundtrack">배경음악 없음</div>
        )}
      </div>

      {project.render?.status === 'COMPLETED' && project.render.contentUrl && (
        <div className="final-render">
          <div className="final-render-heading">
            <div>
              <span>FINAL VIDEO</span>
              <strong>최종 영상이 준비됐습니다.</strong>
            </div>
            <a href={resolveAssetUrl(project.render.contentUrl)} download={`fire-${project.id}.mp4`}>다운로드</a>
          </div>
          <video controls preload="metadata" src={resolveAssetUrl(project.render.contentUrl)} />
        </div>
      )}

      {project.render && project.render.status !== 'COMPLETED' && (
        <div className={`render-state render-${project.render.status.toLowerCase()}`}>
          <span>{project.render.status === 'FAILED' ? '렌더 실패' : '최종 영상 렌더링 중'}</span>
          <strong>{project.render.errorCode ?? '사진·동영상·음악을 하나의 MP4로 합성하고 있습니다.'}</strong>
        </div>
      )}

      <div className="scene-list">
        {project.scenes.map((scene) => {
          const sourceAssets = project.assets
            .filter((asset) => asset.sceneId === scene.id && asset.kind !== 'AUDIO')
            .sort((left, right) => left.timelinePosition - right.timelinePosition)
          return (
            <div className="scene-card" key={scene.id}>
              <span className="scene-sequence">{String(scene.sequence).padStart(2, '0')}</span>
              {sourceAssets.length ? (
                <div className="scene-media-strip">
                  {sourceAssets.slice(0, 3).map((asset) => <MediaThumbnail asset={asset} key={asset.id} />)}
                  {sourceAssets.length > 3 && <span className="more-thumbnails">+{sourceAssets.length - 3}</span>}
                </div>
              ) : <span className="media-thumbnail empty">＋</span>}
              <div className="scene-copy">
                <p>{scene.prompt}</p>
                <small>
                  {sourceAssets.length
                    ? `${sourceAssets.length}개 소스 · ${sourceAssets.filter((asset) => asset.kind === 'IMAGE').length}개 사진`
                    : scene.providerJobId ?? scene.errorCode ?? '프롬프트만 사용'}
                </small>
                {scene.providerModel && (
                  <small className="generation-audit">
                    {scene.providerModel}
                    {scene.estimatedCostUsd !== null ? ` · 영상 출력 예상 $${scene.estimatedCostUsd.toFixed(2)}` : ''}
                  </small>
                )}
              </div>
              <span className={`scene-status scene-${scene.status.toLowerCase()}`}>{scene.status}</span>
              {scene.status === 'COMPLETED' && scene.previewUri?.startsWith('/') && (
                <video
                  className="generated-scene-video"
                  controls
                  preload="metadata"
                  src={resolveAssetUrl(scene.previewUri)}
                />
              )}
            </div>
          )
        })}
      </div>

      <button
        className="render-button"
        type="button"
        disabled={
          (visualCount === 0 && generatedCount === 0)
          || submitting
          || project.render?.status === 'QUEUED'
          || project.render?.status === 'PROCESSING'
          || project.render?.status === 'COMPLETED'
          || project.status === 'QUEUED'
          || project.status === 'PROCESSING'
        }
        onClick={() => void onRender(project.id)}
      >
        {project.render?.status === 'COMPLETED'
          ? '최종 MP4 렌더링 완료'
          : project.render?.status === 'FAILED'
            ? '최종 렌더링 다시 시도'
            : project.render
              ? '최종 영상 렌더링 중…'
              : generatedCount > 0
                ? `${generatedCount}개 AI 장면으로 최종 영상 만들기`
                : visualCount > 0
                  ? `${visualCount}개 소스로 최종 영상 만들기`
                  : '렌더링할 이미지나 동영상을 추가하세요'}
      </button>

      <div className="generation-controls">
        <label>
          장면 생성 공급자
          <select
            value={generationProvider}
            disabled={!startable || submitting}
            onChange={(event) => {
              setGenerationProvider(event.target.value)
              setPaidConfirmed(false)
            }}
          >
            {providers.map((provider) => (
              <option value={provider} key={provider}>
                {provider === 'gemini' ? 'Google Gemini Omni Flash (유료)' : provider}
              </option>
            ))}
          </select>
        </label>
        {paidProvider && (
          <label className="paid-confirmation">
            <input
              type="checkbox"
              checked={paidConfirmed}
              disabled={!startable || submitting}
              onChange={(event) => setPaidConfirmed(event.target.checked)}
            />
            <span>장면별 유료 Gemini API 호출에 동의합니다.</span>
          </label>
        )}
        <button
          className="generate-button"
          type="button"
          disabled={!startable || submitting || (paidProvider && !paidConfirmed)}
          onClick={() => void onGenerate(project.id, generationProvider, paidConfirmed)}
        >
          {project.status === 'FAILED'
            ? `${providerLabel}로 실패 장면 다시 생성`
            : project.status === 'COMPLETED'
              ? `${project.providerName ?? providerLabel} 영상 생성 완료`
              : startable
                ? `${providerLabel} 영상 생성 시작`
                : '생성 작업 진행 중'}
        </button>
      </div>
    </article>
  )
}

function MediaThumbnail({ asset }: { asset: MediaAsset | null }) {
  if (!asset) return <span className="media-thumbnail empty">＋</span>
  if (asset.kind === 'IMAGE') {
    return <img className="media-thumbnail" src={resolveAssetUrl(asset.contentUrl)} alt="" />
  }
  return (
    <video className="media-thumbnail" muted preload="metadata">
      <source src={resolveAssetUrl(asset.contentUrl)} type={asset.contentType} />
    </video>
  )
}

export default App
