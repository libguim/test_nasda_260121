package com.example.nasda.dto.sticker;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDecorationRequestDTO {
    // 1. 어떤 이미지 위에 붙일지 (전체 공통)
    private Integer postImageId;

    // 2. 누가 붙이는지 (전체 공통)
    private Integer userId;

    // 3. 붙일 스티커들의 상세 정보 리스트
    @Builder.Default
    private List<DecorationItem> decorations = new ArrayList<>();

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DecorationItem {
        private Integer decorationId;
        private Integer stickerId;
        private Float posX;
        private Float posY;

        @Builder.Default
        private Float scale = 1.0f;

        @Builder.Default
        private Float rotation = 0.0f;

        @Builder.Default
        private Integer zIndex = 1;
    }
}