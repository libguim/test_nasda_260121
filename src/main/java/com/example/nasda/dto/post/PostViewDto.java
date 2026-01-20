package com.example.nasda.dto.post;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PostViewDto {
    private Integer id;
    private String title;
    private String content;
    private String category;
    private AuthorDto author;
    private List<String> images;

    // ✅ 추가: url + imageId를 같이 내려주는 객체 리스트 (꾸미기/확장용)
    private List<ImageDto> imageItems;

    private LocalDateTime createdAt;
    private boolean isOwner;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorDto {
        private String nickname;
        private Integer userId;
        private String loginId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageDto {
        private Integer id;        // PostImageEntity.imageId
        private String url;        // PostImageEntity.imageUrl
        private Integer sortOrder; // PostImageEntity.sortOrder
    }
}
