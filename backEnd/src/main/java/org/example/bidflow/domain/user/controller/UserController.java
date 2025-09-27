package org.example.bidflow.domain.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.user.dto.*;
import org.example.bidflow.domain.user.entity.User;
import org.example.bidflow.domain.user.service.JwtBlacklistService;
import org.example.bidflow.domain.user.service.UserService;
import org.example.bidflow.domain.user.service.EmailService;
import org.example.bidflow.global.controller.BaseController;
import org.example.bidflow.global.dto.RsData;
import org.example.bidflow.global.utils.CookieUtil;
import org.example.bidflow.global.utils.JwtProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.example.bidflow.domain.user.dto.UserAuctionHistoryResponse;
import org.example.bidflow.domain.user.dto.FavoriteResponse;
import org.example.bidflow.domain.user.service.FavoriteService;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
@Slf4j
public class UserController extends BaseController {

    private final UserService userService;
    private final JwtBlacklistService blacklistService;
    private final EmailService emailService;
    private final CookieUtil cookieUtil;
    private final JwtProvider jwtProvider;
    private final FavoriteService favoriteService;

    // 회원가입
    @PostMapping("/signup")
    public ResponseEntity<RsData<UserSignUpResponse>> signup(@Valid @RequestBody UserSignUpRequest request) {
        long startTime = startOperation("signup", "회원가입");
        try {
            UserSignUpResponse response = userService.signup(request);
            endOperation("signup", "회원가입", startTime);
            return createdResponse("회원가입이 완료되었습니다.", response);
        } catch (Exception e) {
            endOperation("signup", "회원가입", startTime);
            throw e;
        }
    }


    @PostMapping("/login")
    public ResponseEntity<RsData<UserSignInResponse>> signin(@Valid @RequestBody UserSignInRequest request,
                                                              HttpServletResponse response) {
        long startTime = startOperation("signin", "로그인");
        try {
            // 로그인 서비스 호출
            UserSignInResponse loginResponse = userService.login(request);

            // JWT 토큰을 쿠키에 저장
            cookieUtil.addJwtCookie(response, loginResponse.getToken());

            // 쿠키 기반 인증으로 변경하여 응답에서 토큰 제거
            UserSignInResponse responseWithoutToken = UserSignInResponse.builder()
                    .userUUID(loginResponse.getUserUUID())
                    .nickname(loginResponse.getNickname())
                    .email(loginResponse.getEmail())
                    .token(null)  // 쿠키에 저장되므로 응답에 포함하지 않음
                    .build();

            log.info("[로그인 성공] 쿠키 기반 인증 완료 - userUUID: {}", loginResponse.getUserUUID());
            
            endOperation("signin", "로그인", startTime);
            return successResponse("로그인이 완료되었습니다.", responseWithoutToken);
        } catch (Exception e) {
            endOperation("signin", "로그인", startTime);
            throw e;
        }
    }

    // 인증 상태 확인 API (프론트엔드에서 사용)
    @GetMapping("/check")
    public ResponseEntity<RsData<UserSignInResponse>> checkAuthStatus(HttpServletRequest request) {
        try {
            // 쿠키에서 토큰 추출
            String token = cookieUtil.getJwtFromCookie(request);
            if (token == null) {
                log.debug("[인증 확인] 쿠키에 토큰이 없습니다.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new RsData<>("401", "인증되지 않았습니다.", null));
            }

            // 토큰 유효성 검증
            if (!jwtProvider.validateToken(token)) {
                log.debug("[인증 확인] 토큰이 유효하지 않습니다.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new RsData<>("401", "토큰이 유효하지 않습니다.", null));
            }

            // 토큰에서 사용자 정보 추출
            String userUUID = jwtProvider.parseUserUUID(token);
            User user = userService.getUserByUUID(userUUID);

            UserSignInResponse userInfo = UserSignInResponse.builder()
                    .userUUID(user.getUserUUID())
                    .nickname(user.getNickname())
                    .email(user.getEmail())
                    .token(null) // 쿠키 기반이므로 토큰 제외
                    .build();

            log.debug("[인증 확인] 인증된 사용자: {}", user.getUserUUID());
            return ResponseEntity.ok(new RsData<>("200", "인증 상태 확인 완료", userInfo));

        } catch (Exception e) {
            log.error("[인증 확인 실패] 인증 상태 확인 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new RsData<>("401", "인증 확인에 실패했습니다.", null));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            // 쿠키에서 토큰 추출
            String token = cookieUtil.getJwtFromCookie(request);
            
            // 토큰이 없는 경우 (이미 로그아웃된 상태)
            if (token == null) {
                // Authorization 헤더에서 토큰 추출 시도 (하위 호환성)
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }
            
            // 토큰이 있는 경우 블랙리스트에 추가
            if (token != null) {
                blacklistService.addToBlacklist(token);
                log.info("[로그아웃] 토큰을 블랙리스트에 추가했습니다.");
            }
            
            // 쿠키 삭제
            cookieUtil.deleteJwtCookie(response);
            log.info("[로그아웃] 쿠키 기반 로그아웃 완료");
            
            return ResponseEntity.ok(Map.of("message", "로그아웃이 완료되었습니다."));
            
        } catch (Exception e) {
            log.error("[로그아웃 실패] 로그아웃 처리 중 오류 발생: {}", e.getMessage(), e);
            
            // 오류가 발생해도 쿠키는 삭제 처리
            cookieUtil.deleteJwtCookie(response);
            
            return ResponseEntity.ok(Map.of("message", "로그아웃이 완료되었습니다."));
        }
    }

    @GetMapping("/users/{userUUID}") //특정 사용자 조회
    public ResponseEntity<RsData<UserCheckRequest>> getUser(@PathVariable("userUUID") String userUUID) {
        UserCheckRequest userCheck = userService.getUserCheck(userUUID);
        RsData<UserCheckRequest> rsData = new RsData<>("200", "사용자 조회가 완료되었습니다.", userCheck);
        return ResponseEntity.ok(rsData);
    }

   @PutMapping("/users/{userUUID}")
    public ResponseEntity<RsData<UserPutRequest>> putUser(@PathVariable("userUUID") String userUUID ,@RequestBody UserPutRequest request) {
        UserPutRequest userPut = userService.updateUser(userUUID, request);
        RsData<UserPutRequest> rsData = new RsData<>("200", "사용자 정보 수정이 완료되었습니다.", userPut);
        return ResponseEntity.ok(rsData);
    }

    @PostMapping("/send-code")
    public ResponseEntity<RsData> sendVerticationCode(@RequestBody @Valid EmailSendRequest request)
    {
        log.error("Request to send verification code failed: {}", request);
        emailService.sendVerificationCode(request.getEmail());
        RsData rsData = new RsData("200","인증코드가 전송되었습니다.");
        return ResponseEntity.ok(rsData);
    }

    @PostMapping("/vertify")
    public ResponseEntity<RsData> vertify(@RequestBody @Valid EmailVerificationRequest request) {
        boolean isValidCode = emailService.vertifyCode(request.getEmail(),request.getCode());

        return isValidCode
                ? ResponseEntity.ok(new RsData("200", "이메일 인증이 처리되었습니다."))
                : ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new RsData("400", "인증코드가 일치하지 않습니다."));
    }

    // 마이페이지 - 입찰한 경매 목록 조회
    @GetMapping("/mypage/auctions")
    public ResponseEntity<RsData<List<UserAuctionHistoryResponse>>> getUserAuctionHistory(HttpServletRequest request) {
        String token = cookieUtil.getJwtFromCookie(request);
        if (token == null || !jwtProvider.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new RsData<>("401", "인증되지 않았습니다.", null));
        }
        String userUUID = jwtProvider.parseUserUUID(token);
        List<UserAuctionHistoryResponse> history = userService.getUserAuctionHistory(userUUID);
        return ResponseEntity.ok(new RsData<>("200", "입찰한 경매 목록 조회 성공", history));
    }

    // 마이페이지 - 관심목록(찜) 조회
    @GetMapping("/mypage/favorites")
    public ResponseEntity<RsData<List<FavoriteResponse>>> getFavorites(HttpServletRequest request) {
        String token = cookieUtil.getJwtFromCookie(request);
        if (token == null || !jwtProvider.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new RsData<>("401", "인증되지 않았습니다.", null));
        }
        String userUUID = jwtProvider.parseUserUUID(token);
        List<FavoriteResponse> favorites = favoriteService.getFavoriteResponses(userUUID);
        return ResponseEntity.ok(new RsData<>("200", "관심목록 조회 성공", favorites));
    }

    // 관심 경매 등록
    @PostMapping("/favorites")
    public ResponseEntity<RsData<FavoriteResponse>> addFavorite(HttpServletRequest request, @RequestParam Long auctionId) {
        String token = cookieUtil.getJwtFromCookie(request);
        if (token == null || !jwtProvider.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new RsData<>("401", "인증되지 않았습니다.", null));
        }
        String userUUID = jwtProvider.parseUserUUID(token);
        FavoriteResponse response = FavoriteService.toResponse(favoriteService.addFavorite(userUUID, auctionId));
        return ResponseEntity.ok(new RsData<>("200", "관심 경매 등록 성공", response));
    }

    // 관심 경매 해제
    @DeleteMapping("/favorites")
    public ResponseEntity<RsData<String>> removeFavorite(HttpServletRequest request, @RequestParam Long auctionId) {
        String token = cookieUtil.getJwtFromCookie(request);
        if (token == null || !jwtProvider.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new RsData<>("401", "인증되지 않았습니다.", null));
        }
        String userUUID = jwtProvider.parseUserUUID(token);
        favoriteService.removeFavorite(userUUID, auctionId);
        return ResponseEntity.ok(new RsData<>("200", "관심 경매 해제 성공", null));
    }

}
