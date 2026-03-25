package com.keep.service;

import com.keep.entity.CheckinRecord;
import com.keep.entity.ManagerConfig;
import com.keep.entity.User;
import com.keep.exception.BizException;
import com.keep.repository.CheckinRecordRepository;
import com.keep.repository.UserRepository;
import com.keep.util.DateUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class CheckinService {

    private final CheckinRecordRepository checkinRepository;
    private final UserRepository userRepository;
    private final ManagerConfigService configService;
    private final StreakEngine streakEngine;

    public CheckinService(CheckinRecordRepository checkinRepository,
                          UserRepository userRepository,
                          ManagerConfigService configService,
                          StreakEngine streakEngine) {
        this.checkinRepository = checkinRepository;
        this.userRepository = userRepository;
        this.configService = configService;
        this.streakEngine = streakEngine;
    }

    /**
     * 打卡
     */
    @Transactional
    public CheckinRecord checkin(Long checkerId, String photoKey, String note) {
        User checker = userRepository.findById(checkerId)
                .orElseThrow(() -> new BizException("用户不存在"));

        if (!"CHECKER".equals(checker.getRole()) || checker.getManagerId() == null) {
            throw new BizException("仅打卡者可打卡");
        }

        ManagerConfig config = configService.getConfigForChecker(checker.getManagerId());
        LocalDate today = DateUtil.getCurrentCheckinDate(config.getResetHour());

        if (checkinRepository.existsByCheckerIdAndCheckinDate(checkerId, today)) {
            throw new BizException("今日已打卡，明天继续加油");
        }

        CheckinRecord record = new CheckinRecord();
        record.setCheckerId(checkerId);
        record.setManagerId(checker.getManagerId());
        record.setCheckinDate(today);
        record.setPhotoKey(photoKey);
        record.setNote(note);

        return checkinRepository.save(record);
    }

    /**
     * 单事务完成"打卡 + 周期积分结算"，避免状态不一致。
     */
    @Transactional
    public CheckinResult checkinAndProcess(Long checkerId, String photoKey, String note) {
        CheckinRecord record = checkin(checkerId, photoKey, note);
        StreakEngine.StreakResult streakResult = streakEngine.processCheckin(checkerId);
        return new CheckinResult(record, streakResult);
    }

    /**
     * 查询今日是否已打卡
     */
    public boolean hasCheckedInToday(Long checkerId, int resetHour) {
        LocalDate today = DateUtil.getCurrentCheckinDate(resetHour);
        return checkinRepository.existsByCheckerIdAndCheckinDate(checkerId, today);
    }

    /**
     * 获取指定月份的打卡记录 (日历视图)
     */
    public List<CheckinRecord> getMonthRecords(Long checkerId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        return checkinRepository.findByCheckerIdAndDateRange(checkerId, start, end);
    }

    /**
     * 获取打卡历史列表
     */
    public List<CheckinRecord> getHistory(Long checkerId) {
        return checkinRepository.findByCheckerIdOrderByCheckinDateDesc(checkerId);
    }

    /**
     * 计算当前连续打卡天数
     */
    public int getCurrentStreak(Long checkerId, int resetHour) {
        LocalDate today = DateUtil.getCurrentCheckinDate(resetHour);
        List<CheckinRecord> records = checkinRepository.findRecentRecords(checkerId, today);

        int streak = 0;
        LocalDate expected = today;
        for (CheckinRecord r : records) {
            if (r.getCheckinDate().equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }

    public record CheckinResult(CheckinRecord record, StreakEngine.StreakResult streakResult) {}
}
