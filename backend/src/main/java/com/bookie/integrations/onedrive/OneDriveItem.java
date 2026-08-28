package com.bookie.integrations.onedrive;

import lombok.Builder;

@Builder
public record OneDriveItem(
    String id,
    String name,
    long size,
    String lastModified,
    String created,
    String webUrl,
    String parentPath,
    boolean folder) {}
