package com.keep.service;

import com.keep.entity.CheckinRecord;
import com.keep.entity.ManagerConfig;
import com.keep.entity.User;
import com.keep.repository.CheckinRecordRepository;
import com.keep.repository.UserRepository;
import com.keep.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 连续打卡周期引擎 - 打卡后调用此服务计算积分发放
 *
 * 核心逻辑:
 * 1. 计算当前连续打卡天数
 * 2. 每完成 STREAK_DAYS 天 = 1个周期 -> 发放 POINTS_PER_CYCLE 积分
 * 3. 每累积 BONUS_FULL_COUNT 个周期 -> 发放 BONUS_POINTS 额外积分
 * 4. 连续中断 -> 周期进度和全勤累积归零
 */
@Service
public class StreakEngine {

    private static final Logger log = LoggerFactory.getLogger(StreakEngine.class);

    private final CheckinRecordRepository checkinRepository;
    private final UserRepository userRepository;
    private final ManagerConfigService configService;
    private final PointsService pointsService;

    public StreakEngine(CheckinRecordRepository checkinRepository,
                        UserRepository userRepository,
                        ManagerConfigService configService,
                        PointsService pointsService) {
        this.checkinRepository = checkinRepository;
        this.userRepository = userRepository;
        this.configService = configService;
        this.pointsService = pointsService;
    }

    /**
     * 打卡后触发: 检查是否完成周期/全勤，发放积分
     */
    @Transactional
    public StreakResult processCheckin(Long checkerId) {
        User checker = userRepository.findById(checkerId).orElse(null);
        if (checker == null || checker.getManagerId() == null) {
            return new StreakResult(0, 0, 0, 0);
        }

        ManagerConfig config = configService.getConfigForChecker(checker.getManagerId());
        int streak = calculateCurrentStreak(checkerId, config.getResetHour());

        int completedCycles = streak / config.getStreakDays();
        int cycleDay = streak % config.getStreakDays();
        int earnedPoints = 0;
        int bonusEarned = 0;

        // 检查今天的打卡是否刚好完成了一个周期
        // 条件: 连续天数 > 0 且刚好是 STREAK_DAYS 的整数倍
        if (streak > 0 && cycleDay == 0) {
            // 刚完成一个周期
            pointsService.earnPoints(checkerId, checker.getManagerId(),
                    config.getPointsPerCycle(), "CYCLE_REWARD", config.getPointsValidity());
            earnedPoints = config.getPointsPerCycle();
            log.info("打卡者{}完成第{}个周期，发放{}积分", checkerId, completedCycles, earnedPoints);

            // 检查全勤额外奖励
            if (completedCycles > 0 && completedCycles % config.getBonusFullCount() == 0) {
                pointsService.earnPoints(checkerId, checker.getManagerId(),
                        config.getBonusPoints(), "BONUS_REWARD", config.getPointsValidity());
                bonusEarned = config.getBonusPoints();
                log.info("打卡者{}达成第{}次全勤额外奖励，发放{}积分",
                        checkerId, completedCycles / config.getBonusFullCount(), bonusEarned);
            }
        }

        return new StreakResult(streak, cycleDay == 0 ? config.getStreakDays() : cycleDay,
                earnedPoints, bonusEarned);
    }

    /**
     * 计算当前连续打卡天数 (从今天往回数连续的天数)
     */
    public int calculateCurrentStreak(Long checkerId, int resetHour) {
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

    /**
     * 获取仪表盘状态数据
     */
    public DashboardStatus getDashboardStatus(Long checkerId) {
        User checker = userRepository.findById(checkerId).orElse(null);
        if (checker == null || checker.getManagerId() == null) {
            return DashboardStatus.empty();
        }

        ManagerConfig config = configService.getConfigForChecker(checker.getManagerId());
        int streak = calculateCurrentStreak(checkerId, config.getResetHour());
        int completedCycles = streak / config.getStreakDays();
        int cycleDay = streak % config.getStreakDays();
        int bonusProgress = completedCycles % config.getBonusFullCount();
        int availablePoints = pointsService.getAvailablePoints(checkerId);
        int expiringPoints = pointsService.getExpiringPointsIn30Days(checkerId);
        boolean checkedIn = checkinRepository.existsByCheckerIdAndCheckinDate(
                checkerId, DateUtil.getCurrentCheckinDate(config.getResetHour()));

        return new DashboardStatus(
                streak, cycleDay, config.getStreakDays(), config.getPointsPerCycle(),
                completedCycles, bonusProgress, config.getBonusFullCount(), config.getBonusPoints(),
                availablePoints, expiringPoints, checkedIn
        );
    }

    public record StreakResult(int totalStreak, int cycleDay, int earnedPoints, int bonusEarned) {}

    public record DashboardStatus(
            int currentStreak, int cycleDay, int streakDays, int pointsPerCycle,
            int completedCycles, int bonusProgress, int bonusFullCount, int bonusPoints,
            int availablePoints, int expiringPoints, boolean checkedInToday
    ) {
        static DashboardStatus empty() {
            return new DashboardStatus(0, 0, 5, 1, 0, 0, 3, 1, 0, 0, false);
        }
    }
}
