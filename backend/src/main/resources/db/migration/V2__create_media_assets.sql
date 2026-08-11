CREATE TABLE media_assets (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES video_projects(id) ON DELETE CASCADE,
    scene_id UUID REFERENCES video_scenes(id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_media_assets_kind CHECK (kind IN ('IMAGE', 'VIDEO', 'AUDIO')),
    CONSTRAINT chk_media_assets_size CHECK (size_bytes > 0),
    CONSTRAINT chk_media_assets_assignment CHECK (
        (kind = 'AUDIO' AND scene_id IS NULL)
        OR (kind IN ('IMAGE', 'VIDEO') AND scene_id IS NOT NULL)
    )
);

CREATE INDEX idx_media_assets_project_id ON media_assets(project_id);
CREATE INDEX idx_media_assets_scene_id ON media_assets(scene_id);
