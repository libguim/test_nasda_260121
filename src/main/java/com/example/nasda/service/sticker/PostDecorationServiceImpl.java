package com.example.nasda.service.sticker;

import com.example.nasda.domain.*;
import com.example.nasda.dto.sticker.PostDecorationRequestDTO;
import com.example.nasda.dto.sticker.PostDecorationResponseDTO;
import com.example.nasda.repository.PostImageRepository;
import com.example.nasda.repository.sticker.PostDecorationRepository;
import com.example.nasda.repository.sticker.StickerRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostDecorationServiceImpl implements PostDecorationService {

    private final PostDecorationRepository postDecorationRepository;
    private final PostImageRepository postImageRepository;
    private final StickerRepository stickerRepository;
    private final EntityManager entityManager;

    /**
     * ✅ 스티커 일괄 저장
     * 누구나 로그인한 상태라면 타인의 게시글 이미지에 스티커를 붙일 수 있습니다.
     */
    @Override
    @Transactional
    public List<PostDecorationResponseDTO> saveDecorations(PostDecorationRequestDTO requestDTO) {
        Integer currentUserId = requestDTO.getUserId();
        Integer imageId = requestDTO.getPostImageId();

        log.info("🚀 [SAVE START] 유저: {}, 이미지: {}", currentUserId, imageId);

        // 기초 정보 로드
        PostImageEntity postImage = postImageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이미지입니다."));
        UserEntity decorator = entityManager.getReference(UserEntity.class, currentUserId);

        boolean isPostOwner = postImage.getPost().getUser().getUserId().equals(currentUserId);

        if (isPostOwner) {
            postDecorationRepository.deleteByPostImageImageId(imageId);
        } else {
            postDecorationRepository.deleteByUserAndImageBulk(currentUserId, imageId);
        }

        postDecorationRepository.flush();
        entityManager.clear();

        PostImageEntity freshPostImage = postImageRepository.findById(imageId).orElseThrow();
        UserEntity freshDecorator = entityManager.getReference(UserEntity.class, currentUserId);

        // 스티커 정보 조회 (기존 유지)
        List<Integer> stickerIds = requestDTO.getDecorations().stream()
                .map(PostDecorationRequestDTO.DecorationItem::getStickerId)
                .distinct()
                .collect(Collectors.toList());

        Map<Integer, StickerEntity> stickerMap = stickerRepository.findAllById(stickerIds).stream()
                .collect(Collectors.toMap(StickerEntity::getStickerId, s -> s));

        // 수정 또는 삽입 처리
        List<PostDecorationEntity> entitiesToSave = requestDTO.getDecorations().stream()
                .map(item -> {
                    StickerEntity sticker = stickerMap.get(item.getStickerId());
                    return PostDecorationEntity.builder()
                            .postImage(freshPostImage)
                            .user(freshDecorator)
                            .sticker(sticker)
                            .posX(item.getPosX())
                            .posY(item.getPosY())
                            .scale(item.getScale())
                            .rotation(item.getRotation())
                            .zIndex(10)
                            .build();
                })
                .collect(Collectors.toList());

        try {
            // saveAll은 신규는 Insert, 기존은 Update 쿼리를 날려 데드락을 예방합니다.
            List<PostDecorationEntity> savedEntities = postDecorationRepository.saveAll(entitiesToSave);
            postDecorationRepository.flush();
            log.info("🏁 [SAVE SUCCESS] 수정한 내역만 DB 반영 완료");
            return savedEntities.stream()
                    .map(PostDecorationResponseDTO::from)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("❌ [CRITICAL ERROR] 저장 중 예외 발생: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * ✅ 스티커 수정
     * 로직: 본인이 붙인 스티커만 수정 가능
     */
    @Override
    @Transactional
    public void updateDecoration(Integer decorationId, PostDecorationRequestDTO.DecorationItem updateDTO, Integer currentUserId) {
        PostDecorationEntity decoration = postDecorationRepository.findById(decorationId)
                .orElseThrow(() -> new IllegalArgumentException("수정할 장식이 존재하지 않습니다."));
        if (!decoration.getUser().getUserId().equals(currentUserId)) {
            throw new SecurityException("자신이 붙인 스티커만 수정할 수 있습니다.");
        }
        decoration.changePosition(updateDTO.getPosX(), updateDTO.getPosY(), updateDTO.getScale(), updateDTO.getRotation());
    }

    /**
     * ✅ 스티커 삭제
     * 로직: 본인이 붙인 스티커만 삭제 가능
     */
    @Override
    @Transactional
    public void deleteDecoration(Integer decorationId, Integer currentUserId) {
        PostDecorationEntity decoration = postDecorationRepository.findById(decorationId)
                .orElseThrow(() -> new IllegalArgumentException("삭제할 장식이 존재하지 않습니다."));

        Integer stickerAuthorId = decoration.getUser().getUserId();
        Integer postOwnerId = decoration.getPostImage().getPost().getUser().getUserId();

        // 본인 확인 (게시글 주인 권한을 추가하고 싶다면 여기에 OR 조건을 추가하세요)
        if (!stickerAuthorId.equals(currentUserId) && !postOwnerId.equals(currentUserId)) {
            throw new SecurityException("조작 권한이 없습니다.");
        }

        postDecorationRepository.delete(decoration);
    }

    /**
     * ✅ 이미지별 조회
     */
    @Override
    public List<PostDecorationResponseDTO> getDecorationsByImageId(Integer imageId) {
        return postDecorationRepository.findByPostImage_ImageId(imageId).stream()
                .map(PostDecorationResponseDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * ✅ 게시글별 전체 조회
     */
    @Override
    public List<PostDecorationResponseDTO> getDecorationsByPostId(Integer postId) {
        // Repository에서 간접 참조 메서드(findByPostImage_Post_PostId)를 사용합니다.
        return postDecorationRepository.findByPostImage_Post_PostId(postId).stream()
                .map(PostDecorationResponseDTO::from)
                .collect(Collectors.toList());
    }
}

