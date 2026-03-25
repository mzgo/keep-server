package com.keep.controller;

import com.keep.config.JwtFilter;
import com.keep.dto.ApiResponse;
import com.keep.entity.CheckinRecord;
import com.keep.exception.BizException;
import com.keep.service.CheckinService;
import com.keep.service.StreakEngine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/checkin")
public class CheckinController {

    private final CheckinService checkinService;
    private final StreakEngine streakEngine;

    public CheckinController(CheckinService checkinService, StreakEngine streakEngine) {
        this.checkinService = checkinService;
        this.streakEngine = streakEngine;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> checkin(HttpServletRequest request,
                                                     @RequestBody Map<String, String> body) {
        Long userId = requireAuth(request);
        String photoKey = body.get("photoKey");
        String note = body.get("note");
        if (photoKey == null || photoKey.isBlank()) {
            throw new BizException("请上传打卡照片");
        }

        CheckinService.CheckinResult checkinResult = checkinService.checkinAndProcess(userId, photoKey, note);
        CheckinRecord record = checkinResult.record();
        StreakEngine.StreakResult result = checkinResult.streakResult();

        return ApiResponse.ok(Map.of(
                "record", record,
                "streak", result.totalStreak(),
                "earnedPoints", result.earnedPoints(),
                "bonusEarned", result.bonusEarned()
        ));
    }

    /** 首页仪表盘数据 */
    @GetMapping("/dashboard")
    public ApiResponse<StreakEngine.DashboardStatus> dashboard(HttpServletRequest request) {
        Long userId = requireAuth(request);
        return ApiResponse.ok(streakEngine.getDashboardStatus(userId));
    }

    /** 兼容旧前端接口命名 */
    @GetMapping("/today")
    public ApiResponse<Map<String, Object>> today(HttpServletRequest request) {
        Long userId = requireAuth(request);
        StreakEngine.DashboardStatus status = streakEngine.getDashboardStatus(userId);
        return ApiResponse.ok(Map.of(
                "checkedInToday", status.checkedInToday(),
                "currentStreak", status.currentStreak(),
                "cycleDay", status.cycleDay(),
                "streakDays", status.streakDays()
        ));
    }

    @GetMapping("/history")
    public ApiResponse<List<CheckinRecord>> history(HttpServletRequest request) {
        Long userId = requireAuth(request);
        return ApiResponse.ok(checkinService.getHistory(userId));
    }

    @GetMapping("/month")
    public ApiResponse<List<CheckinRecord>> monthRecords(HttpServletRequest request,
                                                          @RequestParam int year,
                                                          @RequestParam int month) {
        Long userId = requireAuth(request);
        return ApiResponse.ok(checkinService.getMonthRecords(userId, year, month));
    }

    private Long requireAuth(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtFilter.ATTR_USER_ID);
        if (userId == null) throw new BizException(401, "请先登录");
        return userId;
    }
}
