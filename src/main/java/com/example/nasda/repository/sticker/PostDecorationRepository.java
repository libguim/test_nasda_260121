package com.example.nasda.repository.sticker;

import com.example.nasda.domain.PostDecorationEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PostDecorationRepository extends JpaRepository<PostDecorationEntity, Integer> {

    // ✅ 도배 방지용 카운트: 유저가 특정 이미지에 붙인 스티커 개수
    long countByUser_UserIdAndPostImage_ImageId(Integer userId, Integer imageId);

    // ✅ 목록 조회: 이미지별 스티커 리스트 (Sticker 정보 Fetch Join으로 성능 최적화)
    @EntityGraph(attributePaths = {"sticker"})
    List<PostDecorationEntity> findByPostImage_ImageId(Integer imageId);

    // ✅ 게시글 전체 조회용: PostImage를 거쳐 PostId로 조회
    List<PostDecorationEntity> findByPostImage_Post_PostId(Integer postId);

    // ✅ 특정 스티커 하나만 위치와 크기를 수정하는 기능
    @Modifying
    @Transactional
    @Query("UPDATE PostDecorationEntity d SET d.posX = :x, d.posY = :y, d.scale = :s, d.rotation = :r WHERE d.decorationId = :id")
    void updateSingleSticker(@Param("id") Integer id, @Param("x") float x, @Param("y") float y, @Param("s") float s, @Param("r") float r);

    // ✅ 벌크 삭제: 수정/저장 시 기존 데이터를 효율적으로 삭제 (데드락 방지용)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW) // 💡 새로운 트랜잭션에서 즉시 커밋
    @Query("DELETE FROM PostDecorationEntity d WHERE d.user.userId = :userId AND d.postImage.imageId = :imageId")
    void deleteByUserAndImageBulk(@Param("userId") Integer userId, @Param("imageId") Integer imageId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW) // 💡 게시글 주인 삭제도 즉시 커밋
    @Query("DELETE FROM PostDecorationEntity d WHERE d.postImage.imageId = :imageId")
    void deleteByPostImageImageId(@Param("imageId") Integer imageId);

    // ✅ 게시글 삭제 시 연쇄 삭제용: PostImage를 거쳐 PostId 기준으로 삭제
    @Modifying
    @Transactional
    void deleteByPostImage_Post_PostId(Integer postId);

}