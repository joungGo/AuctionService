package org.example.bidflow.domain.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.user.dto.*;
import org.example.bidflow.domain.user.entity.Role;
import org.example.bidflow.domain.user.entity.User;
import org.example.bidflow.domain.user.repository.UserRepository;
import org.example.bidflow.global.exception.ServiceException;
import org.example.bidflow.global.service.BaseService;
import org.example.bidflow.global.utils.JwtProvider;
import org.example.bidflow.domain.bid.repository.BidRepository;
import org.example.bidflow.domain.user.dto.UserAuctionHistoryResponse;
import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.domain.auction.repository.AuctionRepository;
import org.example.bidflow.domain.bid.entity.Bid;
import org.example.bidflow.domain.winner.entity.Winner;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService extends BaseService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final EmailService emailService;
    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;

    public UserCheckRequest getUserCheck(String userUUID) {
        long startTime = startOperation("getUserCheck", "사용자 정보 조회");
        try {
            User user = getUserByUUID(userUUID);
            
            UserCheckRequest response = UserCheckRequest.from(user);
            endOperation("getUserCheck", "사용자 정보 조회", startTime);
            log.info("[사용자 조회] 사용자 정보 조회 성공 - userUUID: {}, nickname: {}", userUUID, user.getNickname());
            return response;
        } catch (Exception e) {
            endOperation("getUserCheck", "사용자 정보 조회", startTime);
            throw e;
        }
    }


    public UserSignUpResponse signup(UserSignUpRequest request) {
        long startTime = startOperation("signup", "사용자 회원가입");
        try {
            log.info("[회원가입] 회원가입 시도 - 이메일: {}, 닉네임: {}", request.getEmail(), request.getNickname());

            // 이메일 인증 검증
            validateEmailVerification(request.getEmail());

            // 중복 사용자 검증
            validateUserUniqueness(request.getEmail(), request.getNickname());

            // 사용자 생성 및 저장
            User user = createAndSaveUser(request);

            // Redis에서 인증 정보 삭제
            emailService.deleteVerificationCode(request.getEmail());

            UserSignUpResponse response = UserSignUpResponse.from(user);
            endOperation("signup", "사용자 회원가입", startTime);
            log.info("[회원가입 성공] 새 사용자 등록 완료 - userUUID: {}, 이메일: {}, 닉네임: {}", 
                    user.getUserUUID(), request.getEmail(), request.getNickname());
            return response;
        } catch (Exception e) {
            endOperation("signup", "사용자 회원가입", startTime);
            throw e;
        }
    }

    /**
     * 이메일 인증 상태를 검증합니다.
     */
    private void validateEmailVerification(String email) {
        if (emailService.isVerificationExpired(email)) {
            log.warn("[회원가입 실패] 이메일 인증 만료 - 이메일: {}", email);
            throw new ServiceException(HttpStatus.UNAUTHORIZED.value() + "", "이메일 인증이 만료되었습니다. 다시 인증해 주세요.");
        }

        if (!emailService.isVerified(email)) {
            log.warn("[회원가입 실패] 이메일 인증 미완료 - 이메일: {}", email);
            throw new ServiceException(HttpStatus.UNAUTHORIZED.value() + "", "이메일 인증이 완료되지 않았습니다.");
        }
    }

    /**
     * 사용자 중복 여부를 검증합니다.
     */
    private void validateUserUniqueness(String email, String nickname) {
        Optional<User> existingUser = userRepository.findByEmailOrNickname(email, nickname);

        if(existingUser.isPresent()) {
            log.warn("[회원가입 실패] 중복 정보 - 이메일: {}, 닉네임: {}", email, nickname);
            throw new ServiceException(HttpStatus.CONFLICT.value() + "", "이미 사용 중인 이메일 또는 닉네임입니다.");
        }
    }

    /**
     * 사용자를 생성하고 저장합니다.
     */
    private User createAndSaveUser(UserSignUpRequest request) {
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        String userUUID = System.currentTimeMillis() + "-" + UUID.randomUUID();
        
        User user = User.builder()
                .userUUID(userUUID)
                .email(request.getEmail())
                .password(encodedPassword)
                .nickname(request.getNickname())
                .role(Role.USER)
                .build();

        return userRepository.save(user);
    }

    // UUID를 기반으로 유저 검증
    public User getUserByUUID(String userUUID) {
        User user = userRepository.findByUserUUID(userUUID)
                .orElseThrow(() -> {
                    log.error("[사용자 검증 실패] 존재하지 않는 사용자 - userUUID: {}", userUUID);
                    return new ServiceException("404", "해당 사용자를 찾을 수 없습니다. 사용자 ID를 다시 확인해주세요.");
                });
        
        return user;
    }

    public UserSignInResponse login(UserSignInRequest request) {
        long startTime = startOperation("login", "사용자 로그인");
        try {
            log.info("[로그인] 로그인 시도 - 이메일: {}", request.getEmail());

            // 이메일을 기준으로 사용자를 찾음
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> {
                        log.warn("[로그인 실패] 존재하지 않는 이메일 - 이메일: {}", request.getEmail());
                        return new ServiceException(HttpStatus.UNAUTHORIZED.value() + "", "이메일 또는 비밀번호가 일치하지 않습니다.");
                    });

            // 비밀번호 검증
            validatePassword(request.getPassword(), user.getPassword(), request.getEmail());

            // JWT 토큰 생성
            String token = generateJwtToken(user);

            UserSignInResponse response = UserSignInResponse.from(user, token);
            endOperation("login", "사용자 로그인", startTime);
            log.info("[로그인 성공] JWT 토큰 발급 완료 - userUUID: {}, 닉네임: {}", user.getUserUUID(), user.getNickname());
            return response;
        } catch (Exception e) {
            endOperation("login", "사용자 로그인", startTime);
            throw e;
        }
    }

    /**
     * 비밀번호를 검증합니다.
     */
    private void validatePassword(String inputPassword, String storedPassword, String email) {
        if (!passwordEncoder.matches(inputPassword, storedPassword)) {
            log.warn("[로그인 실패] 비밀번호 불일치 - 이메일: {}", email);
            throw new ServiceException(HttpStatus.UNAUTHORIZED.value() + "", "이메일 또는 비밀번호가 일치하지 않습니다.");
        }
        log.debug("[로그인] 비밀번호 검증 성공 - 이메일: {}", email);
    }

    /**
     * JWT 토큰을 생성합니다.
     */
    private String generateJwtToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userUUID", user.getUserUUID());
        claims.put("nickname", user.getNickname());
        claims.put("role", "ROLE_" + user.getRole());

        return jwtProvider.generateToken(claims, user.getEmail());
    }

    public UserPutRequest updateUser(String userUUID, UserPutRequest request) {
        long startTime = startOperation("updateUser", "사용자 정보 수정");
        try {
            log.info("[사용자 정보 수정] 정보 수정 시도 - userUUID: {}", userUUID);
            
            User user = getUserByUUID(userUUID);

            // ✅ null이 아닌 값만 업데이트
            if (request.getNickname() != null) {
                user.setNickname(request.getNickname());
            }

            if (request.getProfileImage() != null) {
                user.setProfileImage(request.getProfileImage());
            }

            if (request.getEmail() != null) {
                user.setEmail(request.getEmail());
            }

            User savedUser = userRepository.save(user);
            UserPutRequest response = UserPutRequest.from(savedUser);
            
            endOperation("updateUser", "사용자 정보 수정", startTime);
            log.info("[사용자 정보 수정 성공] 정보 수정 완료 - userUUID: {}", userUUID);
            return response;
        } catch (Exception e) {
            endOperation("updateUser", "사용자 정보 수정", startTime);
            throw e;
        }
    }

    /**
     * 사용자가 입찰한 경매 ID 목록(distinct) 반환
     */
    public List<Long> getAuctionIdsUserBidOn(String userUUID) {
        User user = getUserByUUID(userUUID);
        return bidRepository.findDistinctAuctionIdsByUser(user);
    }

    /**
     * 사용자가 입찰한 경매 목록(상태, 내 입찰 정보 포함) 반환
     */
    public List<UserAuctionHistoryResponse> getUserAuctionHistory(String userUUID) {
        User user = getUserByUUID(userUUID);
        List<Long> auctionIds = bidRepository.findDistinctAuctionIdsByUser(user);
        List<Auction> auctions = auctionRepository.findAllById(auctionIds);
        return auctions.stream().map(auction -> {
            // 내 마지막 입찰가
            List<Bid> myBids = bidRepository.findByAuctionAndUserOrderByBidTimeDesc(auction, user);
            Integer myLastBidAmount = myBids.isEmpty() ? null : myBids.get(0).getAmount();
            Integer myHighestBidAmount = myBids.stream().map(Bid::getAmount).max(Integer::compareTo).orElse(null);
            // 경매 최고 입찰가
            Integer auctionHighestBidAmount = bidRepository.findMaxAmountByAuction(auction).orElse(null);
            // 상태 판별
            String status;
            boolean isWinner = false;
            if (auction.getStatus().name().equals("ONGOING")) {
                status = "진행중";
            } else if (auction.getStatus().name().equals("FINISHED")) {
                Winner winner = auction.getWinner();
                if (winner != null && winner.getUser() != null && winner.getUser().getUserUUID().equals(userUUID)) {
                    status = "낙찰";
                    isWinner = true;
                } else {
                    status = "패찰";
                }
            } else {
                status = auction.getStatus().name();
            }
            return UserAuctionHistoryResponse.builder()
                    .auctionId(auction.getAuctionId())
                    .productName(auction.getProduct().getProductName())
                    .imageUrl(auction.getProduct().getImageUrl())
                    .status(status)
                    .myLastBidAmount(myLastBidAmount)
                    .myHighestBidAmount(myHighestBidAmount)
                    .auctionHighestBidAmount(auctionHighestBidAmount)
                    .endTime(auction.getEndTime())
                    .isWinner(isWinner)
                    .build();
        }).collect(Collectors.toList());
    }
}

