CREATE TABLE video_projects (
    id UUID PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    topic VARCHAR(500) NOT NULL,
    style_prompt VARCHAR(1000) NOT NULL,
    aspect_ratio VARCHAR(5) NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(100),
    provider_name VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_video_projects_aspect_ratio CHECK (aspect_ratio IN ('16:9', '9:16')),
    CONSTRAINT chk_video_projects_status CHECK (status IN ('DRAFT', 'QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE video_scenes (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES video_projects(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    prompt VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_job_id VARCHAR(255),
    preview_uri VARCHAR(2048),
    error_code VARCHAR(100),
    CONSTRAINT uq_video_scenes_project_sequence UNIQUE (project_id, sequence_number),
    CONSTRAINT chk_video_scenes_sequence CHECK (sequence_number > 0),
    CONSTRAINT chk_video_scenes_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_video_projects_status ON video_projects(status);
CREATE INDEX idx_video_projects_created_at ON video_projects(created_at DESC);
CREATE INDEX idx_video_scenes_project_id ON video_scenes(project_id);
