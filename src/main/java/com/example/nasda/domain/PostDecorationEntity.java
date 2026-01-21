package com.example.nasda.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "post_decorations")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
public class PostDecorationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer decorationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", nullable = false)
    private PostImageEntity postImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sticker_id", nullable = false)
    private StickerEntity sticker;

    private Float posX;
    private Float posY;

    @Builder.Default
    private Float scale = 1.0f;

    @Builder.Default
    private Float rotation = 0.0f;

    @Builder.Default
    private Integer zIndex = 1;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // PostDecorationEntity 내부에 추가
    public void changePosition(Float posX, Float posY, Float scale, Float rotation) {
        this.posX = posX;
        this.posY = posY;
        this.scale = scale;
        this.rotation = rotation;
    }

}