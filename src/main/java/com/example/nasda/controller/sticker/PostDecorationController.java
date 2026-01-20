package com.example.nasda.controller.sticker;

import com.example.nasda.dto.sticker.PostDecorationRequestDTO;
import com.example.nasda.dto.sticker.PostDecorationResponseDTO;
import com.example.nasda.repository.sticker.PostDecorationRepository;
import com.example.nasda.service.AuthUserService;
import com.example.nasda.service.sticker.PostDecorationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Log4j2
@RestController
@RequestMapping("/api/decorations")
@RequiredArgsConstructor
public class PostDecorationController {

    private final PostDecorationService postDecorationService;
    private final AuthUserService authUserService; // 반드시 필드 선언!
    private final PostDecorationRepository postDecorationRepository;

    @PostMapping("")
    public ResponseEntity<List<PostDecorationResponseDTO>> saveDecorations(@RequestBody PostDecorationRequestDTO requestDTO) {
        // 클라이언트가 보낸 userId 대신, 서버 세션에 저장된 안전한 ID를 꺼냅니다.
        Integer currentUserId = authUserService.getCurrentUserIdOrNull();

        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // DTO의 userId를 실제 로그인한 사용자 ID로 강제 교체 (보안 위조 방지)
        // (DTO에 @Setter가 없다면 새로운 빌더로 교체하거나 필드를 직접 수정)
         requestDTO.setUserId(currentUserId);

        return ResponseEntity.ok(postDecorationService.saveDecorations(requestDTO));
    }

    @PutMapping("/{decorationId}")
    public ResponseEntity<?> update(@PathVariable Integer decorationId, @RequestBody PostDecorationRequestDTO.DecorationItem updateDTO) {
        Integer currentUserId = authUserService.getCurrentUserIdOrNull();
        if (currentUserId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        postDecorationService.updateDecoration(decorationId, updateDTO, currentUserId);
        return ResponseEntity.ok("수정 완료");
    }

    @DeleteMapping("/{decorationId}")
    public ResponseEntity<String> deleteIndividualSticker(
            @PathVariable("decorationId") Integer decorationId) {

        // 현재 로그인한 사용자의 ID를 가져옵니다 (권한 체크용)
        Integer currentUserId = authUserService.getCurrentUserIdOrNull();

        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        // 서비스 계층의 개별 삭제 로직 호출 (이전에 만든 deleteDecoration 메서드)
        postDecorationService.deleteDecoration(decorationId, currentUserId);

        return ResponseEntity.ok("스티커가 삭제되었습니다. ✨");
    }

    // '내 스티커 모두 지우기' 기능에서 사용
    @DeleteMapping("/user/{userId}/image/{imageId}")
    public ResponseEntity<String> deleteUserStickers(
            @PathVariable("userId") Integer userId,
            @PathVariable("imageId") Integer imageId) {
        postDecorationRepository.deleteByUserAndImageBulk(userId, imageId);
        return ResponseEntity.ok("성공적으로 삭제되었습니다.");
    }

    @GetMapping("/post/{postId}")
    public ResponseEntity<List<PostDecorationResponseDTO>> getByPostId(@PathVariable Integer postId) {
        return ResponseEntity.ok(postDecorationService.getDecorationsByPostId(postId));
    }

}