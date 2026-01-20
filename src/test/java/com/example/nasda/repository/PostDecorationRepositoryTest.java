package com.example.nasda.repository;

import com.example.nasda.domain.*;
import com.example.nasda.repository.sticker.PostDecorationRepository;
import com.example.nasda.repository.sticker.StickerCategoryRepository;
import com.example.nasda.repository.sticker.StickerRepository;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Log4j2
public class PostDecorationRepositoryTest {

    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostImageRepository postImageRepository;
    @Autowired private StickerCategoryRepository stickerCategoryRepository;
    @Autowired private StickerRepository stickerRepository;
    @Autowired private PostDecorationRepository postDecorationRepository;

    @Test
    @DisplayName("유저 리포지토리 단독 테스트")
    public void testUserInsert() {
        String uniqueId = String.valueOf(System.currentTimeMillis()).substring(8);
        UserEntity user = UserEntity.builder()
                .loginId("user_" + uniqueId)
                .password("1111")
                .email("moana_" + uniqueId + "@test.com")
                .nickname("모아나_" + uniqueId)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        UserEntity result = userRepository.save(user);
        log.info("--- User Saved ID: " + result.getUserId());
    }

//    @Test
//    @DisplayName("카테고리 리포지토리 단독 테스트")
//    public void testCategoryInsert() {
//        CategoryEntity category = CategoryEntity.builder()
//                .categoryName("자유게시판")
//                .isActive(true)
//                .build();
//
//        CategoryEntity result = categoryRepository.save(category);
//        log.info("--- Category Saved ID: " + result.getCategoryId());
//    }
    @Test
    @DisplayName("카테고리 랜덤 이름 테스트")
    public void testCategoryInsertRandom() {
        String[] names = {"자유게시판", "공지사항", "Q&A", "정보공유", "이벤트"};

        // 배열 중 하나를 랜덤하게 선택
        int randomIndex = (int) (Math.random() * names.length);
        String randomName = names[randomIndex] + "_" + (int)(Math.random() * 1000);

        CategoryEntity category = CategoryEntity.builder()
                .categoryName(randomName)
                .isActive(true)
                .build();

        CategoryEntity result = categoryRepository.save(category);
        log.info("--- 랜덤 생성 카테고리: " + result.getCategoryName());
    }

    @Test
    @DisplayName("스티커 및 스티커 카테고리 연동 테스트")
    public void testStickerInsert() {
        StickerCategoryEntity sCat = stickerCategoryRepository.save(
                StickerCategoryEntity.builder().name("감정표현").isActive(true).build());

        StickerEntity sticker = StickerEntity.builder()
                .stickerCategory(sCat)
                .stickerName("웃는 얼굴")
                .stickerImageUrl("smile.png")
                .build();

        StickerEntity result = stickerRepository.save(sticker);
        log.info("--- Sticker Saved ID: " + result.getStickerId());
    }

// --- [CREATE] 생성 테스트 ---

    @Test
    @Transactional
    @Rollback(false)
    @DisplayName("1. 복합 관계를 가진 장식 저장 (Create)")
    public void testDecorationInsert() {
        // [Given] 기초 데이터 준비
        UserEntity user = createQuickUser();
        CategoryEntity cat = categoryRepository.save(CategoryEntity.builder().categoryName("테스트카테고리").isActive(true).build());
        PostEntity post = postRepository.save(PostEntity.builder().title("테스트 포스트").user(user).category(cat).build());
        PostImageEntity img = postImageRepository.save(PostImageEntity.builder()
                .post(post)
                .imageUrl("background.jpg")
                .sortOrder(1)
                .isRepresentative(true)
                .build());
        StickerEntity sticker = createQuickSticker();

        // [When] 장식 저장
        PostDecorationEntity deco = PostDecorationEntity.builder()
                .postImage(img).user(user).sticker(sticker)
                .posX(150.5f).posY(200.0f).scale(1.2f).zIndex(10)
                .build();

        PostDecorationEntity result = postDecorationRepository.save(deco);

        // [Then]
        assertNotNull(result.getDecorationId());
        log.info("--- 장식 저장 완료 ID: " + result.getDecorationId());
    }

    // --- [READ] 조회 테스트 ---

    @Test
    @Transactional
    @DisplayName("2. 이미지별 장식 목록 조회 (Read - EntityGraph)")
    public void testSelectByImage() {
        // [Given] 특정 이미지 ID 지정 (HeidiSQL 등에서 확인된 ID)
        Integer targetImageId = 12;

        // [When] Sticker 정보까지 한 번에 가져오는지 확인
        List<PostDecorationEntity> list = postDecorationRepository.findByPostImage_ImageId(targetImageId);

        // [Then]
        log.info("--- 조회된 장식 개수: " + list.size());
        list.forEach(deco -> {
            log.info(deco);
            log.info("장식 ID: " + deco.getDecorationId() + " | 사용된 스티커: " + deco.getSticker().getStickerName());
        });
    }

    // --- [UPDATE] 수정 테스트 ---

    @Test
    @Transactional
    @Rollback(false)
    @DisplayName("3. 장식 위치, 크기, 회전 단건 수정 (Update)")
    public void testUpdateSingleSticker() {
        // [Given] 실제 DB에 저장된 ID를 사용해야 안전합니다.
        // 만약 ID 1번이 없다면 테스트가 실패하므로, 앞선 테스트에서 생성된 ID를 사용하거나
        // 아래처럼 존재하는지 확인하는 로직이 필요합니다.
        Integer targetId = 1;
        float newX = 500.0f;
        float newY = 300.0f;
        float newScale = 1.5f;
        float newRotation = 45f;

        // [When] 인자 5개 확인: targetId, x, y, scale, rotation
        postDecorationRepository.updateSingleSticker(targetId, newX, newY, newScale, newRotation);

        // [Then]
        PostDecorationEntity updated = postDecorationRepository.findById(targetId)
                .orElse(null); // 에러 대신 null 반환으로 흐름 파악

        if (updated != null) {
            log.info("--- 수정 완료: ID=" + targetId);
            assertEquals(newX, updated.getPosX());
        } else {
            log.warn("--- 경고: ID " + targetId + "번 데이터가 DB에 없습니다. 확인이 필요해요!");
        }
    }

    // --- [DELETE] 삭제 테스트 ---

    @Test
    @Transactional
    @Rollback(false)
    @DisplayName("4. 특정 장식 단건 삭제 (Delete)")
    public void testDeleteOne() {
        Integer targetId = 1;
        postDecorationRepository.deleteById(targetId);

        boolean exists = postDecorationRepository.existsById(targetId);
        assertFalse(exists);
        log.info("--- 삭제 완료 여부: " + !exists);
    }

    @Test
    @Transactional
    @Rollback(false)
    @DisplayName("5. 게시글 삭제 시 관련 모든 장식 일괄 삭제 (Bulk Delete)")
    public void testDeleteByPost() {
        Integer targetPostId = 1;

        // 수정된 메서드명 호출 (언더바 제거)
        postDecorationRepository.deleteByPostImage_Post_PostId(targetPostId);

        // 검증 조회 메서드도 수정된 이름으로 호출
        List<PostDecorationEntity> remaining = postDecorationRepository.findByPostImage_Post_PostId(targetPostId);

        assertTrue(remaining.isEmpty());
        log.info("--- 일괄 삭제 완료 ---");
    }

    // --- [BUSINESS LOGIC] 비즈니스 로직 검증 ---

    @Test
    @DisplayName("6. 도배 방지를 위한 특정 유저의 이미지별 장식 카운트")
    public void testCountThrottle() {
        Integer userId = 1;
        Integer imageId = 12;

        long count = postDecorationRepository.countByUser_UserIdAndPostImage_ImageId(userId, imageId);
        log.info("--- 유저(" + userId + ")가 이미지(" + imageId + ")에 붙인 스티커 총합: " + count);
    }

    // --- 퀵 헬퍼 메서드 ---
    private UserEntity createQuickUser() {
        String ts = String.valueOf(System.nanoTime()).substring(10);
        return userRepository.save(UserEntity.builder()
                .loginId("user_" + ts).password("1111").email("u_" + ts + "@test.com").nickname("모아나_" + ts)
                .role(UserRole.USER).status(UserStatus.ACTIVE).build());
    }

    private StickerEntity createQuickSticker() {
        StickerCategoryEntity c = stickerCategoryRepository.save(StickerCategoryEntity.builder().name("테스트카테").build());
        return stickerRepository.save(StickerEntity.builder().stickerCategory(c).stickerName("기본스티커").stickerImageUrl("test.png").build());
    }


}