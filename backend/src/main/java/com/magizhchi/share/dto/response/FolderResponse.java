package com.magizhchi.share.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class FolderResponse {
    private Long    id;
    private String  name;
    private Long    parentId;
    private Long    conversationId;
    private Long    createdById;
    private String  createdByName;
    private Instant createdAt;
    /** Breadcrumb path from root to this folder */
    private List<BreadcrumbItem> breadcrumb;

    @Data
    @Builder
    public static class BreadcrumbItem {
        private Long   id;
        private String name;
    }
}
