package com.example.nasda.dto.sticker;

import com.example.nasda.domain.PostDecorationEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDecorationResponseDTO {

    private Integer decorationId;
    private Integer postImageId;

    // 스티커 정보
    private Integer stickerId;
    private String stickerImageUrl;
    private String loginId;
    private String nickname;

    // 꾸미기 속성
    private Float posX;
    private Float posY;
    private Float scale;
    private Float rotation;
    private Integer zIndex;

    // Entity -> DTO 변환 메서드 (리턴 타입도 DTO로 변경)
    public static PostDecorationResponseDTO from(PostDecorationEntity entity) {
        return PostDecorationResponseDTO.builder()
                .decorationId(entity.getDecorationId())
                .postImageId(entity.getPostImage().getImageId())
                .stickerId(entity.getSticker().getStickerId())
                .stickerImageUrl(entity.getSticker().getStickerImageUrl())
                .loginId(entity.getUser().getLoginId())
                .nickname(entity.getUser().getNickname())
                .posX(entity.getPosX())
                .posY(entity.getPosY())
                .scale(entity.getScale())
                .rotation(entity.getRotation())
                .zIndex(entity.getZIndex())
                .build();
    }
}