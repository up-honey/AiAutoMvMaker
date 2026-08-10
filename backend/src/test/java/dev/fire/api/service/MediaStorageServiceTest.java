package dev.fire.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.util.List;

import dev.fire.api.domain.AssetKind;
import dev.fire.api.domain.VideoProject;
import dev.fire.api.domain.VideoScene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fire-media;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "fire.media.storage-root=target/test-media-storage"
})
class MediaStorageServiceTest {

    @Autowired
    private MediaStorageService mediaStorage;

    @Autowired
    private VideoProjectStore projectStore;

    @Autowired
    private MediaAssetStore assetStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM media_assets");
        jdbcTemplate.update("DELETE FROM video_scenes");
        jdbcTemplate.update("DELETE FROM video_projects");
    }

    @Test
    void storesVerifiedMediaWithAHashAndGeneratedStorageKey() throws Exception {
        var project = projectStore.save(project("Media demo"));
        var png = new MockMultipartFile(
                "file",
                "../customer/photo.png",
                "application/octet-stream",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3});

        var stored = mediaStorage.store(
                project.getId(),
                project.getScenes().getFirst().getId(),
                AssetKind.IMAGE,
                png);

        assertThat(stored.originalFilename()).isEqualTo("photo.png");
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.sha256()).hasSize(64);
        assertThat(stored.storageKey()).doesNotContain("customer", "photo.png", "..");
        assertThat(assetStore.findForScene(project.getId(), project.getScenes().getFirst().getId()))
                .extracting(dev.fire.api.domain.MediaAsset::id)
                .containsExactly(stored.id());
        assertThat(Files.readAllBytes(mediaStorage.load(project.getId(), stored.id()).path()))
                .isEqualTo(png.getBytes());
    }

    @Test
    void rejectsContentThatDoesNotMatchTheRequestedMediaKind() {
        var project = projectStore.save(project("Signature demo"));
        var fakeImage = new MockMultipartFile(
                "file",
                "fake.png",
                "image/png",
                "not really an image".getBytes());

        assertThatThrownBy(() -> mediaStorage.store(
                project.getId(),
                project.getScenes().getFirst().getId(),
                AssetKind.IMAGE,
                fakeImage))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("file signature");
        assertThat(assetStore.findByProject(project.getId())).isEmpty();
    }

    @Test
    void enforcesSceneOwnershipAndLocksInputsAfterGenerationStarts() {
        var project = projectStore.save(project("Owner"));
        var anotherProject = projectStore.save(project("Other"));
        var image = new MockMultipartFile(
                "file",
                "pixel.png",
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1});

        assertThatThrownBy(() -> mediaStorage.store(
                project.getId(),
                anotherProject.getScenes().getFirst().getId(),
                AssetKind.IMAGE,
                image))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("does not belong");

        projectStore.queueForGeneration(project.getId(), "mock");
        assertThatThrownBy(() -> mediaStorage.store(
                project.getId(),
                project.getScenes().getFirst().getId(),
                AssetKind.IMAGE,
                image))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("only be added while");
    }

    @Test
    void preservesMultipleSceneSourcesInTimelineOrderAndDuration() {
        var project = projectStore.save(project("Wedding timeline"));
        var first = new MockMultipartFile(
                "file", "second.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 2});
        var second = new MockMultipartFile(
                "file", "first.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1});

        mediaStorage.store(project.getId(), project.getScenes().getFirst().getId(), AssetKind.IMAGE, 1, 500, first);
        mediaStorage.store(project.getId(), project.getScenes().getFirst().getId(), AssetKind.IMAGE, 0, 500, second);

        assertThat(assetStore.findForScene(project.getId(), project.getScenes().getFirst().getId()))
                .extracting(asset -> asset.originalFilename() + ":" + asset.durationMs())
                .containsExactly("first.png:500", "second.png:500");
    }

    private VideoProject project(String title) {
        return new VideoProject(
                title,
                "Media pipeline",
                "cinematic",
                "9:16",
                List.of(new VideoScene(1, "Opening")));
    }
}
