package com.example.nasda.service;

import com.example.nasda.domain.CategoryEntity;
import com.example.nasda.domain.PostEntity;
import com.example.nasda.domain.PostImageEntity;
import com.example.nasda.domain.UserEntity;
import com.example.nasda.domain.UserRepository;
import com.example.nasda.dto.post.HomePostDto;
import com.example.nasda.dto.post.PostViewDto;
import com.example.nasda.repository.CategoryRepository;
import com.example.nasda.repository.CommentRepository;
import com.example.nasda.repository.PostImageRepository;
import com.example.nasda.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

    private final PostRepository postRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final PostImageRepository postImageRepository;
    private final CommentRepository commentRepository;

    // 🔹 게시글 단건 조회
    @Transactional(readOnly = true)
    public PostEntity get(Integer postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다."));
    }

    // ✅ (추가) postId로 이미지 객체 리스트 만들기: [id, url, sortOrder]
    @Transactional(readOnly = true)
    public List<PostViewDto.ImageDto> getImageItems(Integer postId) {
        return postImageRepository.findAllByPost_PostIdOrderBySortOrderAsc(postId)
                .stream()
                .map(img -> new PostViewDto.ImageDto(
                        img.getImageId(),
                        img.getImageUrl(),
                        img.getSortOrder()
                ))
                .toList();
    }

    // ✅ (기존) postId로 이미지 URL 리스트
    @Transactional(readOnly = true)
    public List<String> getImageUrls(Integer postId) {
        return postImageRepository.findAllByPost_PostIdOrderBySortOrderAsc(postId)
                .stream()
                .map(PostImageEntity::getImageUrl)
                .toList();
    }

    // 🔹 홈 게시글 목록 (최신 30개 + 대표 이미지 1장)
    @Transactional(readOnly = true)
    public List<HomePostDto> getHomePosts() {
        return postRepository.findTop30ByOrderByCreatedAtDesc().stream()
                .map(post -> {
                    String imageUrl = postImageRepository
                            .findFirstByPost_PostIdOrderBySortOrderAsc(post.getPostId())
                            .map(PostImageEntity::getImageUrl)
                            .orElse(null);

                    return new HomePostDto(post.getPostId(), post.getTitle(), imageUrl);
                })
                .toList();
    }

    // 🔹 게시글 생성
    public PostEntity create(Integer userId, Integer categoryId, String title, String description) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));

        CategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("카테고리 없음"));

        PostEntity post = PostEntity.builder()
                .user(user)
                .category(category)
                .title(title)
                .description(description)
                .build();

        return postRepository.save(post);
    }

    // 🔹 게시글 수정
    public void update(Integer postId, Integer userId,
                       Integer categoryId, String title, String description) {

        PostEntity post = get(postId);

        if (!post.getUser().getUserId().equals(userId)) {
            throw new IllegalStateException("수정 권한 없음");
        }

        CategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("카테고리 없음"));

        post.update(category, title, description);
    }

    // 🔥 게시글 삭제 (FK 해결 핵심)
    public void delete(Integer postId, Integer userId) {
        PostEntity post = get(postId);

        if (!post.getUser().getUserId().equals(userId)) {
            throw new IllegalStateException("삭제 권한 없음");
        }

        // ✅ 1. 이미지 삭제
        postImageRepository.deleteByPost_PostId(postId);

        // ✅ 2. 댓글 삭제
        commentRepository.deleteByPost_PostId(postId);

        // ✅ 3. 게시글 삭제
        postRepository.delete(post);
    }

    // ✅ 마이페이지: 내 게시글 전체 목록
    @Transactional(readOnly = true)
    public List<PostEntity> findByUserId(Integer userId) {
        return postRepository.findByUser_UserIdOrderByCreatedAtDesc(userId);
    }

    // ✅ 마이페이지: 내 게시글 개수
    @Transactional(readOnly = true)
    public long countMyPosts(Integer userId) {
        return postRepository.countByUser_UserId(userId);
    }

    // ✅ 마이페이지: 내 최근 게시글 목록
    @Transactional(readOnly = true)
    public List<PostViewDto> getMyRecentPosts(Integer userId, int limit) {

        // 지금은 Repository가 Top4 기반이라 limit은 참고값(추후 PageRequest로 개선 가능)
        List<PostEntity> posts = postRepository.findTop4ByUser_UserIdOrderByCreatedAtDesc(userId);

        return posts.stream()
                .map(post -> {
                    List<String> images = getImageUrls(post.getPostId());
                    List<PostViewDto.ImageDto> imageItems = getImageItems(post.getPostId());

                    return new PostViewDto(
                            post.getPostId(),
                            post.getTitle(),
                            post.getDescription(), // PostViewDto.content 에 description 매핑
                            post.getCategory().getCategoryName(),
                            new PostViewDto.AuthorDto(post.getUser().getNickname(), post.getUser().getUserId(), post.getUser().getLoginId()),
                            images,
                            imageItems,
                            post.getCreatedAt(),
                            true
                    );
                })
                .toList();
    }

    // ✅ 홈: 카테고리 + 페이징 (무한스크롤/카테고리 버튼 API용)
    @Transactional(readOnly = true)
    public Page<HomePostDto> getHomePostsByCategory(String category, Pageable pageable) {

        Page<PostEntity> page;

        // category가 null/빈값/"전체"면 전체 목록
        if (category == null || category.isBlank() || "전체".equals(category)) {
            page = postRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            page = postRepository.findByCategory_CategoryNameOrderByCreatedAtDesc(category, pageable);
        }

        return page.map(post -> {
            String imageUrl = postImageRepository
                    .findFirstByPost_PostIdOrderBySortOrderAsc(post.getPostId())
                    .map(PostImageEntity::getImageUrl)
                    .orElse(null);

            return new HomePostDto(post.getPostId(), post.getTitle(), imageUrl);
        });
    }

    // ✅ 검색 (header search)
    @Transactional(readOnly = true)
    public List<HomePostDto> searchHomePosts(String keyword, String type) {
        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) return List.of();

        String t = (type == null || type.isBlank()) ? "content" : type;

        List<PostEntity> results = switch (t) {
            case "title" -> postRepository.findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(q);
            case "author" -> postRepository.findByUser_NicknameContainingIgnoreCaseOrderByCreatedAtDesc(q);
            case "category" -> postRepository.findByCategory_CategoryNameContainingIgnoreCaseOrderByCreatedAtDesc(q);
            default -> postRepository.findByDescriptionContainingIgnoreCaseOrderByCreatedAtDesc(q);
        };

        return results.stream()
                .map(post -> {
                    String imageUrl = postImageRepository
                            .findFirstByPost_PostIdOrderBySortOrderAsc(post.getPostId())
                            .map(PostImageEntity::getImageUrl)
                            .orElse(null);

                    return new HomePostDto(post.getPostId(), post.getTitle(), imageUrl);
                })
                .toList();
    }

    // ✅ 마이페이지: 내 게시글 10개씩 페이징 조회
    @Transactional(readOnly = true)
    public Page<PostEntity> findByUserId(Integer userId, int page) {
        Pageable pageable = PageRequest.of(page, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        return postRepository.findByUser_UserId(userId, pageable);
    }
}
