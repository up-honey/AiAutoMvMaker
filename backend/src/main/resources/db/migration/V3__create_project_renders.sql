ALTER TABLE video_projects
    ADD COLUMN render_preset VARCHAR(32) NOT NULL DEFAULT 'CLEAN';

ALTER TABLE video_projects
    ADD CONSTRAINT chk_video_projects_render_preset
    CHECK (render_preset IN ('CLEAN', 'ROMANTIC', 'FAIRYTALE_PARK', 'CINEMATIC'));

ALTER TABLE media_assets
    ADD COLUMN timeline_position INTEGER NOT NULL DEFAULT 0;

ALTER TABLE media_assets
    ADD COLUMN duration_ms INTEGER;

ALTER TABLE media_assets
    ADD CONSTRAINT chk_media_assets_position CHECK (timeline_position >= 0);

ALTER TABLE media_assets
    ADD CONSTRAINT chk_media_assets_duration CHECK (duration_ms IS NULL OR duration_ms >= 100);

CREATE TABLE project_renders (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL UNIQUE REFERENCES video_projects(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    output_storage_key VARCHAR(512),
    error_code VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_project_renders_status CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_project_renders_status ON project_renders(status);
