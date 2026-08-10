ALTER TABLE video_scenes
    ADD COLUMN provider_model VARCHAR(128);

ALTER TABLE video_scenes
    ADD COLUMN estimated_cost_usd DECIMAL(10, 4);

ALTER TABLE video_scenes
    ADD COLUMN submitted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE video_scenes
    ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE video_scenes
    ADD COLUMN provider_job_terminal BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE video_scenes
    ADD CONSTRAINT chk_video_scenes_estimated_cost
    CHECK (estimated_cost_usd IS NULL OR estimated_cost_usd >= 0);
