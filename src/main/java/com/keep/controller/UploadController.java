package com.keep.controller;

import com.keep.config.JwtFilter;
import com.keep.dto.ApiResponse;
import com.keep.entity.User;
import com.keep.exception.BizException;
import com.keep.repository.CheckinRecordRepository;
import com.keep.repository.PrizeRepository;
import com.keep.repository.UserRepository;
import com.keep.service.QiniuService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final QiniuService qiniuService;
    private final UserRepository userRepository;
    private final PrizeRepository prizeRepository;
    private final CheckinRecordRepository checkinRecordRepository;

    public UploadController(QiniuService qiniuService,
                            UserRepository userRepository,
                            PrizeRepository prizeRepository,
                            CheckinRecordRepository checkinRecordRepository) {
        this.qiniuService = qiniuService;
        this.userRepository = userRepository;
        this.prizeRepository = prizeRepository;
        this.checkinRecordRepository = checkinRecordRepository;
    }

    /**
     * 获取上传凭证 (前端直传七牛云)
     */
    @GetMapping("/token")
    public ApiResponse<Map<String, String>> getUploadToken(HttpServletRequest request,
                                                            @RequestParam(defaultValue = "checkin") String type) {
        User user = requireAuthUser(request);
        String prefix = resolveUploadPrefix(user, type);

        QiniuService.UploadCredential cred = qiniuService.generateUploadToken(prefix);
        return ApiResponse.ok(Map.of(
                "uploadToken", cred.token(),
                "fileKey", cred.key()
        ));
    }

    /**
     * 获取私有文件的签名访问URL
     */
    @GetMapping("/url")
    public ApiResponse<Map<String, String>> getSignedUrl(HttpServletRequest request,
                                                          @RequestParam String key) {
        User user = requireAuthUser(request);
        assertCanAccessExistingKey(user, key);

        String url = qiniuService.getSignedUrl(key);
        return ApiResponse.ok(Map.of("url", url != null ? url : ""));
    }

    /**
     * 上传完成后确认（兼容前端旧接口）
     * 这里允许访问“刚上传未落库”的 key，但仅按前缀和角色做最小授权。
     */
    @PostMapping("/confirm")
    public ApiResponse<Map<String, String>> confirmUpload(HttpServletRequest request,
                                                          @RequestBody Map<String, String> body) {
        User user = requireAuthUser(request);
        String key = body.getOrDefault("key", body.get("fileKey"));
        assertCanConfirmNewKey(user, key);
        String url = qiniuService.getSignedUrl(key);
        return ApiResponse.ok(Map.of("key", key, "url", url != null ? url : ""));
    }

    private User requireAuthUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtFilter.ATTR_USER_ID);
        if (userId == null) {
            throw new BizException(401, "请先登录");
        }
        return userRepository.findById(userId).orElseThrow(() -> new BizException(401, "用户不存在"));
    }

    private String resolveUploadPrefix(User user, String type) {
        return switch (type) {
            case "avatar" -> "avatars";
            case "prize" -> {
                if (!"MANAGER".equals(user.getRole())) {
                    throw new BizException(403, "仅管理者可上传奖品图片");
                }
                yield "prizes";
            }
            case "checkin" -> {
                if (!"CHECKER".equals(user.getRole())) {
                    throw new BizException(403, "仅打卡者可上传打卡图片");
                }
                yield "checkin";
            }
            default -> throw new BizException("不支持的上传类型");
        };
    }

    private void assertCanConfirmNewKey(User user, String key) {
        if (key == null || key.isBlank()) {
            throw new BizException("缺少文件key");
        }
        if (key.startsWith("avatars/")) {
            return;
        }
        if (key.startsWith("prizes/")) {
            if (!"MANAGER".equals(user.getRole())) {
                throw new BizException(403, "无权访问该图片");
            }
            return;
        }
        if (key.startsWith("checkin/")) {
            if (!"CHECKER".equals(user.getRole())) {
                throw new BizException(403, "无权访问该图片");
            }
            return;
        }
        throw new BizException(403, "非法文件key");
    }

    private void assertCanAccessExistingKey(User user, String key) {
        if (key == null || key.isBlank()) {
            throw new BizException("缺少文件key");
        }

        boolean allowed;
        if (key.startsWith("avatars/")) {
            allowed = userRepository.findByAvatarKey(key)
                    .map(owner -> canAccessAvatar(user, owner))
                    .orElse(false);
        } else if (key.startsWith("prizes/")) {
            Long managerId = "MANAGER".equals(user.getRole()) ? user.getId() : user.getManagerId();
            allowed = managerId != null && prizeRepository.existsByImageKeyAndManagerId(key, managerId);
        } else if (key.startsWith("checkin/")) {
            allowed = "MANAGER".equals(user.getRole())
                    ? checkinRecordRepository.existsByPhotoKeyAndManagerId(key, user.getId())
                    : checkinRecordRepository.existsByPhotoKeyAndCheckerId(key, user.getId());
        } else {
            allowed = false;
        }

        if (!allowed) {
            throw new BizException(403, "文件不存在或无访问权限");
        }
    }

    private boolean canAccessAvatar(User requester, User owner) {
        if ("MANAGER".equals(requester.getRole())) {
            return owner.getId().equals(requester.getId())
                    || (owner.getManagerId() != null && owner.getManagerId().equals(requester.getId()));
        }
        if (owner.getId().equals(requester.getId())) {
            return true;
        }
        return requester.getManagerId() != null && owner.getId().equals(requester.getManagerId());
    }
}
