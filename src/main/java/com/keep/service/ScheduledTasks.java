package com.keep.service;

import com.keep.entity.CheckinRecord;
import com.keep.entity.ManagerConfig;
import com.keep.entity.User;
import com.keep.repository.CheckinRecordRepository;
import com.keep.repository.ManagerConfigRepository;
import com.keep.repository.UserRepository;
import com.keep.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 定时任务: 积分过期 + 惩罚扣分
 * 每小时执行一次，内部判断是否到达各管理者的 resetHour
 */
@Service
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);
    private static final String PENALTY_TIMES_KEY_PREFIX = "penalty:times:";

    private final PointsService pointsService;
    private final UserRepository userRepository;
    private final ManagerConfigRepository configRepository;
    private final CheckinRecordRepository checkinRepository;
    private final StringRedisTemplate redisTemplate;

    public ScheduledTasks(PointsService pointsService, UserRepository userRepository,
                          ManagerConfigRepository configRepository,
                          CheckinRecordRepository checkinRepository,
                          StringRedisTemplate redisTemplate) {
        this.pointsService = pointsService;
        this.userRepository = userRepository;
        this.configRepository = configRepository;
        this.checkinRepository = checkinRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 每小时执行 - 积分过期检查
     */
    @Scheduled(cron = "0 0 * * * *")
    public void checkPointsExpiration() {
        log.info("定时任务: 积分过期检查开始");
        pointsService.expireBatches();
        log.info("定时任务: 积分过期检查完成");
    }

    /**
     * 每小时执行 - 惩罚计算
     * 检查每个打卡者是否触发惩罚条件
     */
    @Scheduled(cron = "0 5 * * * *")
    public void checkPenalties() {
        log.info("定时任务: 惩罚检查开始");

        List<User> checkers = userRepository.findAll().stream()
                .filter(u -> "CHECKER".equals(u.getRole()) && u.getManagerId() != null)
                .toList();

        for (User checker : checkers) {
            try {
                processCheckerPenalty(checker);
            } catch (Exception e) {
                log.error("惩罚计算异常, checkerId={}", checker.getId(), e);
            }
        }

        log.info("定时任务: 惩罚检查完成");
    }

    private void processCheckerPenalty(User checker) {
        Optional<ManagerConfig> configOpt = configRepository.findByManagerId(checker.getManagerId());
        if (configOpt.isEmpty()) return;

        ManagerConfig config = configOpt.get();
        LocalDate today = DateUtil.getCurrentCheckinDate(config.getResetHour());

        // 获取最后一次打卡日期
        Optional<CheckinRecord> lastCheckin = checkinRepository
                .findTopByCheckerIdOrderByCheckinDateDesc(checker.getId());

        LocalDate lastCheckinDate = lastCheckin.map(CheckinRecord::getCheckinDate)
                .orElse(checker.getCreatedAt().toLocalDate());

        long missedDays = ChronoUnit.DAYS.between(lastCheckinDate, today);
        String penaltyTimesKey = PENALTY_TIMES_KEY_PREFIX + checker.getId();
        if (missedDays <= 0) {
            redisTemplate.delete(penaltyTimesKey);
            return;
        }

        int targetPenaltyTimes = (int) (missedDays / config.getPenaltyDays());
        if (targetPenaltyTimes <= 0) return;

        int alreadyAppliedTimes = parseInt(redisTemplate.opsForValue().get(penaltyTimesKey), 0);
        if (targetPenaltyTimes <= alreadyAppliedTimes) {
            return;
        }

        int deltaTimes = targetPenaltyTimes - alreadyAppliedTimes;
        int deductAmount = deltaTimes * config.getPenaltyPoints();
        pointsService.penaltyDeduct(checker.getId(), deductAmount);
        redisTemplate.opsForValue().set(penaltyTimesKey, String.valueOf(targetPenaltyTimes));

        log.info("惩罚执行 checkerId={}, missedDays={}, deltaTimes={}, deductAmount={}",
                checker.getId(), missedDays, deltaTimes, deductAmount);
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }
}
