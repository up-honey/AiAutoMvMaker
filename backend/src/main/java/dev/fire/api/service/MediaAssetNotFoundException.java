package dev.fire.api.service;

import java.util.UUID;

public class MediaAssetNotFoundException extends RuntimeException {

    public MediaAssetNotFoundException(UUID assetId) {
        super("Media asset not found: " + assetId);
    }
}
