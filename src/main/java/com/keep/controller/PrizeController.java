package com.keep.controller;

import com.keep.config.JwtFilter;
import com.keep.dto.ApiResponse;
import com.keep.entity.Prize;
import com.keep.entity.User;
import com.keep.exception.BizException;
import com.keep.repository.UserRepository;
import com.keep.service.PrizeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PrizeController {

    private final PrizeService prizeService;
    private final UserRepository userRepository;

    public PrizeController(PrizeService prizeService, UserRepository userRepository) {
        this.prizeService = prizeService;
        this.userRepository = userRepository;
    }

    /** 管理者: 新增奖品 */
    @PostMapping("/manager/prizes")
    public ApiResponse<Prize> createPrize(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long managerId = requireManager(request);
        String name = (String) body.get("name");
        int requiredPoints = (int) body.get("requiredPoints");
        int totalStock = (int) body.get("totalStock");
        String imageKey = (String) body.get("imageKey");
        return ApiResponse.ok(prizeService.createPrize(managerId, name, requiredPoints, totalStock, imageKey));
    }

    /** 管理者: 编辑奖品 */
    @PutMapping("/manager/prizes/{id}")
    public ApiResponse<Prize> updatePrize(HttpServletRequest request, @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        Long managerId = requireManager(request);
        String name = (String) body.get("name");
        Integer requiredPoints = body.containsKey("requiredPoints") ? (Integer) body.get("requiredPoints") : null;
        Integer totalStock = body.containsKey("totalStock") ? (Integer) body.get("totalStock") : null;
        String imageKey = (String) body.get("imageKey");
        return ApiResponse.ok(prizeService.updatePrize(managerId, id, name, requiredPoints, totalStock, imageKey));
    }

    /** 管理者: 下架奖品 */
    @DeleteMapping("/manager/prizes/{id}")
    public ApiResponse<Void> archivePrize(HttpServletRequest request, @PathVariable Long id) {
        Long managerId = requireManager(request);
        prizeService.archivePrize(managerId, id);
        return ApiResponse.ok();
    }

    /** 管理者: 全部奖品 (含下架) */
    @GetMapping("/manager/prizes")
    public ApiResponse<List<Prize>> getAllPrizes(HttpServletRequest request) {
        Long managerId = requireManager(request);
        return ApiResponse.ok(prizeService.getAllPrizes(managerId));
    }

    /** 打卡者: 可用奖品列表 */
    @GetMapping("/prizes")
    public ApiResponse<List<Prize>> getAvailablePrizes(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtFilter.ATTR_USER_ID);
        if (userId == null) throw new BizException(401, "请先登录");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(401, "用户不存在"));
        Long managerId = "MANAGER".equals(user.getRole()) ? user.getId() : user.getManagerId();
        if (managerId == null) {
            throw new BizException("未绑定管理者，暂无可兑换奖品");
        }

        return ApiResponse.ok(prizeService.getAvailablePrizes(managerId));
    }

    private Long requireManager(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtFilter.ATTR_USER_ID);
        String role = (String) request.getAttribute(JwtFilter.ATTR_ROLE);
        if (userId == null) throw new BizException(401, "请先登录");
        if (!"MANAGER".equals(role)) throw new BizException(403, "仅管理者可访问");
        return userId;
    }
}
