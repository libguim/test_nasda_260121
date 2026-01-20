package com.example.nasda.config;

import com.example.nasda.domain.UserEntity;
import com.example.nasda.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PrincipalDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        UserEntity userEntity = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("아이디를 찾을 수 없습니다: " + loginId));

        // ✅ 수정 1: 스프링 기본 User 객체 대신, 아래에 만든 CustomUser를 반환합니다.
        return new CustomUser(userEntity);
    }

    // ✅ 수정 2: 파일 하단에 이 내부 클래스를 추가합니다.
    // 기존 기능을 절대 건드리지 않으면서 userId만 추가하는 가장 안전한 방법입니다.
    public static class CustomUser extends org.springframework.security.core.userdetails.User {
        private final Integer userId;

        public CustomUser(UserEntity user) {
            super(user.getLoginId(), user.getPassword(),
                    java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
            this.userId = user.getUserId();
        }

        public Integer getUserId() { return userId; }
    }
}
//@Service
//@RequiredArgsConstructor
//public class PrincipalDetailsService implements UserDetailsService {
//
//    private final UserRepository userRepository;
//
//    @Override
//    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
//        // DB에서 사용자 조회
//        UserEntity userEntity = userRepository.findByLoginId(loginId)
//                .orElseThrow(() -> new UsernameNotFoundException("아이디를 찾을 수 없습니다: " + loginId));
//
//        // 스프링 시큐리티 전용 User 객체(UserDetails의 구현체)를 생성하여 반환
//        return User.builder()
//                .username(userEntity.getLoginId())
//                .password(userEntity.getPassword()) // 암호화된 비밀번호여야 함
//                .roles(userEntity.getRole().name()) // ROLE_USER 등의 권한
//                .build();
//    }
//}