package dev.fire.api.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.fire.api.domain.ProjectRender;
import dev.fire.api.domain.RenderStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProjectRenderStore {

    private static final String RENDER_COLUMNS = """
            SELECT id, project_id, status, output_storage_key, error_code, created_at, updated_at
            FROM project_renders
            """;

    private final JdbcTemplate jdbcTemplate;

    public ProjectRenderStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ProjectRender save(ProjectRender render) {
        var updated = jdbcTemplate.update("""
                        UPDATE project_renders
                        SET status = ?, output_storage_key = ?, error_code = ?, updated_at = ?
                        WHERE id = ? AND project_id = ?
                        """,
                render.getStatus().name(),
                render.getOutputStorageKey(),
                render.getErrorCode(),
                Timestamp.from(render.getUpdatedAt()),
                render.getId(),
                render.getProjectId());
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO project_renders (
                                id, project_id, status, output_storage_key, error_code, created_at, updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    render.getId(),
                    render.getProjectId(),
                    render.getStatus().name(),
                    render.getOutputStorageKey(),
                    render.getErrorCode(),
                    Timestamp.from(render.getCreatedAt()),
                    Timestamp.from(render.getUpdatedAt()));
        }
        return render;
    }

    @Transactional
    public ProjectRender queue(UUID projectId) {
        var updated = jdbcTemplate.update("""
                        UPDATE project_renders
                        SET status = 'QUEUED', output_storage_key = NULL, error_code = NULL, updated_at = ?
                        WHERE project_id = ? AND status = 'FAILED'
                        """,
                Timestamp.from(Instant.now()),
                projectId);
        if (updated == 1) {
            return getRequired(projectId);
        }

        var existing = findByProject(projectId);
        if (existing.isPresent()) {
            throw new InvalidRenderStateException("A render already exists for this project");
        }
        try {
            return save(new ProjectRender(projectId));
        } catch (DataIntegrityViolationException exception) {
            throw new InvalidRenderStateException("A render is already queued for this project");
        }
    }

    @Transactional(readOnly = true)
    public Optional<ProjectRender> findByProject(UUID projectId) {
        return jdbcTemplate.query(
                        RENDER_COLUMNS + " WHERE project_id = ?",
                        this::mapRow,
                        projectId)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public ProjectRender getRequired(UUID projectId) {
        return findByProject(projectId).orElseThrow(() -> new ProjectRenderNotFoundException(projectId));
    }

    @Transactional(readOnly = true)
    public List<ProjectRender> findRecoverable() {
        return jdbcTemplate.query(
                RENDER_COLUMNS + " WHERE status IN ('QUEUED', 'PROCESSING') ORDER BY created_at",
                this::mapRow);
    }

    private ProjectRender mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return ProjectRender.restore(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("project_id", UUID.class),
                RenderStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("output_storage_key"),
                resultSet.getString("error_code"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}
