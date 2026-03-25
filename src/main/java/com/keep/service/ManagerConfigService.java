package com.keep.service;

import com.keep.entity.ManagerConfig;
import com.keep.exception.BizException;
import com.keep.repository.ManagerConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManagerConfigService {

    private final ManagerConfigRepository configRepository;

    public ManagerConfigService(ManagerConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public ManagerConfig getConfig(Long managerId) {
        return configRepository.findByManagerId(managerId)
                .orElseGet(() -> {
                    ManagerConfig config = new ManagerConfig();
                    config.setManagerId(managerId);
                    return configRepository.save(config);
                });
    }

    /**
     * 获取打卡者所属管理者的配置
     */
    public ManagerConfig getConfigForChecker(Long managerId) {
        return configRepository.findByManagerId(managerId)
                .orElseThrow(() -> new BizException("管理者配置不存在"));
    }

    @Transactional
    public ManagerConfig saveConfig(Long managerId, ManagerConfig update) {
        ManagerConfig config = getConfig(managerId);

        if (update.getStreakDays() > 0) config.setStreakDays(update.getStreakDays());
        if (update.getPointsPerCycle() > 0) config.setPointsPerCycle(update.getPointsPerCycle());
        if (update.getBonusFullCount() > 0) config.setBonusFullCount(update.getBonusFullCount());
        if (update.getBonusPoints() > 0) config.setBonusPoints(update.getBonusPoints());
        if (update.getPointsValidity() > 0) config.setPointsValidity(update.getPointsValidity());
        if (update.getPenaltyDays() > 0) config.setPenaltyDays(update.getPenaltyDays());
        if (update.getPenaltyPoints() > 0) config.setPenaltyPoints(update.getPenaltyPoints());
        if (update.getResetHour() >= 0 && update.getResetHour() <= 23) {
            config.setResetHour(update.getResetHour());
        }

        config.setInitialized(true);
        return configRepository.save(config);
    }
}
