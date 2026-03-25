package com.keep.service;

import com.keep.dto.ChangePasswordRequest;
import com.keep.dto.LoginRequest;
import com.keep.dto.RegisterRequest;
import com.keep.dto.UpdateProfileRequest;
import com.keep.dto.UserVO;
import com.keep.entity.User;
import com.keep.exception.BizException;
import com.keep.repository.UserRepository;
import com.keep.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVITE_PREFIX = "invite:";
    private static final String EMAIL_CODE_PREFIX = "email_code:";
    private static final String PWD_RESET_PREFIX = "pwd_reset:";

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final CaptchaService captchaService;
    private final StringRedisTemplate redisTemplate;
    private final QiniuService qiniuService;
    private final JavaMailSender mailSender;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, JwtUtil jwtUtil,
                       CaptchaService captchaService, StringRedisTemplate redisTemplate,
                       QiniuService qiniuService, JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.captchaService = captchaService;
        this.redisTemplate = redisTemplate;
        this.qiniuService = qiniuService;
        this.mailSender = mailSender;
    }

    @Transactional
    public void register(RegisterRequest req) {
        if (!captchaService.verify(req.captchaId(), req.captchaCode())) {
            throw new BizException("验证码错误");
        }
        if (userRepository.existsByUsername(req.username())) {
            throw new BizException("用户名已存在");
        }

        User user = new User();
        user.setUsername(req.username());
        user.setPassword(encoder.encode(req.password()));
        user.setNickname(req.username());

        if (req.inviteToken() != null && !req.inviteToken().isBlank()) {
            String managerIdStr = redisTemplate.opsForValue().getAndDelete(INVITE_PREFIX + req.inviteToken());
            if (managerIdStr == null) {
                throw new BizException("邀请链接无效或已过期");
            }
            user.setRole("CHECKER");
            user.setManagerId(Long.parseLong(managerIdStr));
        } else {
            user.setRole("MANAGER");
        }

        if (req.email() != null && !req.email().isBlank()) {
            if (userRepository.existsByEmail(req.email())) {
                throw new BizException("邮箱已被占用");
            }
            user.setEmail(req.email());
        }

        userRepository.save(user);
    }

    public String login(LoginRequest req) {
        if (!captchaService.verify(req.captchaId(), req.captchaCode())) {
            throw new BizException("验证码错误");
        }

        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new BizException("用户名或密码错误"));
        if (!encoder.matches(req.password(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        return jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
    }

    public UserVO getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(401, "用户不存在"));
        return toUserVO(user);
    }

    @Transactional
    public void updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));

        if (req.nickname() != null) {
            user.setNickname(req.nickname());
        }
        if (req.avatarKey() != null) {
            String oldKey = user.getAvatarKey();
            user.setAvatarKey(req.avatarKey());
            userRepository.save(user);
            if (oldKey != null && !oldKey.isBlank() && !oldKey.equals(req.avatarKey())) {
                qiniuService.deleteFileAsync(oldKey);
            }
            return;
        }
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        if (!encoder.matches(req.oldPassword(), user.getPassword())) {
            throw new BizException("旧密码错误");
        }
        user.setPassword(encoder.encode(req.newPassword()));
        userRepository.save(user);
    }

    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            throw new BizException("请输入邮箱");
        }
        User user = userRepository.findByEmail(email)
                .filter(User::isEmailVerified)
                .orElseThrow(() -> new BizException("该邮箱未绑定任何账号"));

        String resetToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                PWD_RESET_PREFIX + resetToken,
                String.valueOf(user.getId()),
                Duration.ofHours(1)
        );
        sendEmail(email, "Keep 密码重置", "你的密码重置 token 为: " + resetToken + "（1小时内有效）");
    }

    @Transactional
    public void confirmPasswordReset(String resetToken, String newPassword) {
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 100) {
            throw new BizException("新密码长度需为6-100位");
        }

        String userIdStr = redisTemplate.opsForValue().getAndDelete(PWD_RESET_PREFIX + resetToken);
        if (userIdStr == null) {
            throw new BizException("重置链接无效或已过期");
        }

        User user = userRepository.findById(Long.parseLong(userIdStr))
                .orElseThrow(() -> new BizException("用户不存在"));
        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);
    }

    public void sendEmailCode(Long userId, String email) {
        if (email == null || email.isBlank()) {
            throw new BizException("请输入邮箱");
        }
        if (userRepository.existsByEmailAndIdNot(email, userId)) {
            throw new BizException("该邮箱已被其他账号绑定");
        }

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        String key = EMAIL_CODE_PREFIX + "bind:" + userId + ":" + email;
        redisTemplate.opsForValue().set(key, code, Duration.ofMinutes(10));
        sendEmail(email, "Keep 邮箱验证码", "你的验证码是: " + code + "（10分钟内有效）");
    }

    @Transactional
    public void bindEmail(Long userId, String email, String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            throw new BizException("参数不完整");
        }
        if (userRepository.existsByEmailAndIdNot(email, userId)) {
            throw new BizException("该邮箱已被其他账号绑定");
        }

        String key = EMAIL_CODE_PREFIX + "bind:" + userId + ":" + email;
        String storedCode = redisTemplate.opsForValue().getAndDelete(key);
        if (storedCode == null || !storedCode.equals(code)) {
            throw new BizException("验证码错误或已过期");
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        user.setEmail(email);
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    private UserVO toUserVO(User user) {
        String avatarUrl = null;
        if (user.getAvatarKey() != null && !user.getAvatarKey().isBlank()) {
            avatarUrl = qiniuService.getSignedUrl(user.getAvatarKey());
        }

        String managerNickname = null;
        if (user.getManagerId() != null) {
            managerNickname = userRepository.findById(user.getManagerId())
                    .map(User::getNickname)
                    .orElse(null);
        }

        return new UserVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                avatarUrl,
                user.getEmail(),
                user.isEmailVerified(),
                user.getRole(),
                user.getManagerId(),
                managerNickname
        );
    }

    private void sendEmail(String to, String subject, String content) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(content);
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("发送邮件失败, to={}", to, e);
            throw new BizException("邮件发送失败，请稍后重试");
        }
    }
}
