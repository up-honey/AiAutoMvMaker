package dev.fire.api.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.fire.api.domain.ProjectStatus;
import dev.fire.api.domain.RenderPreset;
import dev.fire.api.domain.SceneStatus;
import dev.fire.api.domain.VideoProject;
import dev.fire.api.domain.VideoScene;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class VideoProjectStore {

    private static final String PROJECT_COLUMNS = """
            SELECT id, title, topic, style_prompt, aspect_ratio, render_preset, status, error_code,
                   provider_name, created_at, updated_at
            FROM video_projects
            """;

    private final JdbcTemplate jdbcTemplate;

    public VideoProjectStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public VideoProject save(VideoProject project) {
        var updated = jdbcTemplate.update("""
                        UPDATE video_projects
                        SET title = ?, topic = ?, style_prompt = ?, aspect_ratio = ?, render_preset = ?, status = ?,
                            error_code = ?, provider_name = ?, created_at = ?, updated_at = ?
                        WHERE id = ?
                        """,
                project.getTitle(),
                project.getTopic(),
                project.getStylePrompt(),
                project.getAspectRatio(),
                project.getRenderPreset().name(),
                project.getStatus().name(),
                project.getErrorCode(),
                project.getProviderName(),
                Timestamp.from(project.getCreatedAt()),
                Timestamp.from(project.getUpdatedAt()),
                project.getId());

        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO video_projects (
                                id, title, topic, style_prompt, aspect_ratio, render_preset, status, error_code,
                                provider_name, created_at, updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    project.getId(),
                    project.getTitle(),
                    project.getTopic(),
                    project.getStylePrompt(),
                    project.getAspectRatio(),
                    project.getRenderPreset().name(),
                    project.getStatus().name(),
                    project.getErrorCode(),
                    project.getProviderName(),
                    Timestamp.from(project.getCreatedAt()),
                    Timestamp.from(project.getUpdatedAt()));
        }

        for (var scene : project.getScenes()) {
            saveScene(project.getId(), scene);
        }
        return project;
    }

    @Transactional
    public VideoProject queueForGeneration(UUID projectId, String providerName) {
        var updated = jdbcTemplate.update("""
                        UPDATE video_projects
                        SET status = 'QUEUED', error_code = NULL, provider_name = ?, updated_at = ?
                        WHERE id = ? AND status IN ('DRAFT', 'FAILED')
                        """,
                providerName,
                Timestamp.from(Instant.now()),
                projectId);

        if (updated == 0) {
            if (!exists(projectId)) {
                throw new ProjectNotFoundException(projectId);
            }
            throw new InvalidProjectStateException();
        }
        return getRequired(projectId);
    }

    @Transactional(readOnly = true)
    public VideoProject getRequired(UUID projectId) {
        var rows = jdbcTemplate.query(PROJECT_COLUMNS + " WHERE id = ?", this::mapProjectRow, projectId);
        if (rows.isEmpty()) {
            throw new ProjectNotFoundException(projectId);
        }
        return restore(rows.getFirst());
    }

    @Transactional(readOnly = true)
    public List<VideoProject> findAll() {
        return jdbcTemplate.query(PROJECT_COLUMNS + " ORDER BY created_at DESC", this::mapProjectRow)
                .stream()
                .map(this::restore)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VideoProject> findRecoverable() {
        return jdbcTemplate.query(
                        PROJECT_COLUMNS + " WHERE status IN ('QUEUED', 'PROCESSING') ORDER BY created_at",
                        this::mapProjectRow)
                .stream()
                .map(this::restore)
                .toList();
    }

    private void saveScene(UUID projectId, VideoScene scene) {
        var updated = jdbcTemplate.update("""
                        UPDATE video_scenes
                        SET sequence_number = ?, prompt = ?, status = ?, provider_job_id = ?,
                            preview_uri = ?, error_code = ?, provider_model = ?, estimated_cost_usd = ?,
                            submitted_at = ?, completed_at = ?, provider_job_terminal = ?
                        WHERE id = ? AND project_id = ?
                        """,
                scene.getSequence(),
                scene.getPrompt(),
                scene.getStatus().name(),
                scene.getProviderJobId(),
                scene.getPreviewUri(),
                scene.getErrorCode(),
                scene.getProviderModel(),
                scene.getEstimatedCostUsd(),
                timestamp(scene.getSubmittedAt()),
                timestamp(scene.getCompletedAt()),
                scene.isProviderJobTerminal(),
                scene.getId(),
                projectId);

        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO video_scenes (
                                id, project_id, sequence_number, prompt, status,
                                provider_job_id, preview_uri, error_code, provider_model,
                                estimated_cost_usd, submitted_at, completed_at, provider_job_terminal
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    scene.getId(),
                    projectId,
                    scene.getSequence(),
                    scene.getPrompt(),
                    scene.getStatus().name(),
                    scene.getProviderJobId(),
                    scene.getPreviewUri(),
                    scene.getErrorCode(),
                    scene.getProviderModel(),
                    scene.getEstimatedCostUsd(),
                    timestamp(scene.getSubmittedAt()),
                    timestamp(scene.getCompletedAt()),
                    scene.isProviderJobTerminal());
        }
    }

    private boolean exists(UUID projectId) {
        var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_projects WHERE id = ?",
                Integer.class,
                projectId);
        return count != null && count > 0;
    }

    private ProjectRow mapProjectRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProjectRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("title"),
                resultSet.getString("topic"),
                resultSet.getString("style_prompt"),
                resultSet.getString("aspect_ratio"),
                RenderPreset.valueOf(resultSet.getString("render_preset")),
                ProjectStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("error_code"),
                resultSet.getString("provider_name"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private VideoProject restore(ProjectRow row) {
        var scenes = jdbcTemplate.query("""
                        SELECT id, sequence_number, prompt, status, provider_job_id, preview_uri, error_code,
                               provider_model, estimated_cost_usd, submitted_at, completed_at, provider_job_terminal
                        FROM video_scenes
                        WHERE project_id = ?
                        ORDER BY sequence_number
                        """,
                (resultSet, rowNumber) -> VideoScene.restore(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getInt("sequence_number"),
                        resultSet.getString("prompt"),
                        SceneStatus.valueOf(resultSet.getString("status")),
                        resultSet.getString("provider_job_id"),
                        resultSet.getString("preview_uri"),
                        resultSet.getString("error_code"),
                        resultSet.getString("provider_model"),
                        resultSet.getBigDecimal("estimated_cost_usd"),
                        instant(resultSet, "submitted_at"),
                        instant(resultSet, "completed_at"),
                        resultSet.getBoolean("provider_job_terminal")),
                row.id());

        return VideoProject.restore(
                row.id(),
                row.title(),
                row.topic(),
                row.stylePrompt(),
                row.aspectRatio(),
                row.renderPreset(),
                scenes,
                row.createdAt(),
                row.updatedAt(),
                row.status(),
                row.errorCode(),
                row.providerName());
    }

    private record ProjectRow(
            UUID id,
            String title,
            String topic,
            String stylePrompt,
            String aspectRatio,
            RenderPreset renderPreset,
            ProjectStatus status,
            String errorCode,
            String providerName,
            Instant createdAt,
            Instant updatedAt) {
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        var value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
