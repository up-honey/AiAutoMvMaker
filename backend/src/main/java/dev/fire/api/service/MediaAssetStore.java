package dev.fire.api.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.fire.api.domain.AssetKind;
import dev.fire.api.domain.MediaAsset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MediaAssetStore {

    private static final String ASSET_COLUMNS = """
            SELECT id, project_id, scene_id, kind, original_filename, content_type,
                   size_bytes, sha256, storage_key, timeline_position, duration_ms, created_at
            FROM media_assets
            """;

    private final JdbcTemplate jdbcTemplate;

    public MediaAssetStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public MediaAsset save(MediaAsset asset) {
        jdbcTemplate.update("""
                        INSERT INTO media_assets (
                            id, project_id, scene_id, kind, original_filename, content_type,
                            size_bytes, sha256, storage_key, timeline_position, duration_ms, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                asset.id(),
                asset.projectId(),
                asset.sceneId(),
                asset.kind().name(),
                asset.originalFilename(),
                asset.contentType(),
                asset.sizeBytes(),
                asset.sha256(),
                asset.storageKey(),
                asset.timelinePosition(),
                asset.durationMs(),
                Timestamp.from(asset.createdAt()));
        return asset;
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> findByProject(UUID projectId) {
        return jdbcTemplate.query("""
                        SELECT asset.id, asset.project_id, asset.scene_id, asset.kind,
                               asset.original_filename, asset.content_type, asset.size_bytes,
                               asset.sha256, asset.storage_key, asset.timeline_position,
                               asset.duration_ms, asset.created_at
                        FROM media_assets asset
                        LEFT JOIN video_scenes scene ON scene.id = asset.scene_id
                        WHERE asset.project_id = ?
                        ORDER BY CASE WHEN asset.kind = 'AUDIO' THEN 1 ELSE 0 END,
                                 scene.sequence_number, asset.timeline_position, asset.created_at, asset.id
                        """,
                this::mapRow,
                projectId);
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> findForScene(UUID projectId, UUID sceneId) {
        return jdbcTemplate.query(
                        ASSET_COLUMNS + """
                                 WHERE project_id = ? AND scene_id = ? AND kind IN ('IMAGE', 'VIDEO')
                                 ORDER BY timeline_position, created_at, id
                                """,
                        this::mapRow,
                        projectId,
                        sceneId);
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> findVisualsByProject(UUID projectId) {
        return jdbcTemplate.query("""
                        SELECT asset.id, asset.project_id, asset.scene_id, asset.kind,
                               asset.original_filename, asset.content_type, asset.size_bytes,
                               asset.sha256, asset.storage_key, asset.timeline_position,
                               asset.duration_ms, asset.created_at
                        FROM media_assets asset
                        JOIN video_scenes scene ON scene.id = asset.scene_id
                        WHERE asset.project_id = ? AND asset.kind IN ('IMAGE', 'VIDEO')
                        ORDER BY scene.sequence_number, asset.timeline_position, asset.created_at, asset.id
                        """,
                this::mapRow,
                projectId);
    }

    @Transactional(readOnly = true)
    public Optional<MediaAsset> findLatestSoundtrack(UUID projectId) {
        return jdbcTemplate.query(
                        ASSET_COLUMNS + """
                                 WHERE project_id = ? AND kind = 'AUDIO'
                                 ORDER BY created_at DESC, id DESC
                                 LIMIT 1
                                """,
                        this::mapRow,
                        projectId)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public MediaAsset getRequired(UUID projectId, UUID assetId) {
        return jdbcTemplate.query(
                        ASSET_COLUMNS + " WHERE id = ? AND project_id = ?",
                        this::mapRow,
                        assetId,
                        projectId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new MediaAssetNotFoundException(assetId));
    }

    private MediaAsset mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new MediaAsset(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("project_id", UUID.class),
                resultSet.getObject("scene_id", UUID.class),
                AssetKind.valueOf(resultSet.getString("kind")),
                resultSet.getString("original_filename"),
                resultSet.getString("content_type"),
                resultSet.getLong("size_bytes"),
                resultSet.getString("sha256"),
                resultSet.getString("storage_key"),
                resultSet.getInt("timeline_position"),
                (Integer) resultSet.getObject("duration_ms"),
                resultSet.getTimestamp("created_at").toInstant());
    }
}
