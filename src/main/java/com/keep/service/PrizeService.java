package com.keep.service;

import com.keep.entity.Prize;
import com.keep.exception.BizException;
import com.keep.repository.PrizeRepository;
import com.keep.repository.RedeemOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PrizeService {

    private final PrizeRepository prizeRepository;
    private final RedeemOrderRepository orderRepository;
    private final QiniuService qiniuService;

    public PrizeService(PrizeRepository prizeRepository, RedeemOrderRepository orderRepository,
                        QiniuService qiniuService) {
        this.prizeRepository = prizeRepository;
        this.orderRepository = orderRepository;
        this.qiniuService = qiniuService;
    }

    @Transactional
    public Prize createPrize(Long managerId, String name, int requiredPoints,
                             int totalStock, String imageKey) {
        Prize prize = new Prize();
        prize.setManagerId(managerId);
        prize.setName(name);
        prize.setRequiredPoints(requiredPoints);
        prize.setTotalStock(totalStock);
        prize.setImageKey(imageKey);
        return prizeRepository.save(prize);
    }

    @Transactional
    public Prize updatePrize(Long managerId, Long prizeId, String name,
                             Integer requiredPoints, Integer totalStock, String imageKey) {
        Prize prize = prizeRepository.findById(prizeId)
                .orElseThrow(() -> new BizException("奖品不存在"));

        if (!prize.getManagerId().equals(managerId)) {
            throw new BizException(403, "无权操作此奖品");
        }

        if (name != null) prize.setName(name);
        if (requiredPoints != null) prize.setRequiredPoints(requiredPoints);

        if (totalStock != null) {
            // 库存不能低于已兑换且未取消的订单数
            int activeOrders = orderRepository.countByPrizeIdAndStatusNot(prizeId, "CANCELLED");
            if (totalStock < activeOrders) {
                throw new BizException("库存不能低于已兑换数量(" + activeOrders + ")");
            }
            prize.setTotalStock(totalStock);
        }

        if (imageKey != null && !imageKey.equals(prize.getImageKey())) {
            String oldKey = prize.getImageKey();
            prize.setImageKey(imageKey);
            // 异步删除旧图片 (旧图片无展示入口)
            if (oldKey != null && !oldKey.isBlank()) {
                qiniuService.deleteFileAsync(oldKey);
            }
        }

        return prizeRepository.save(prize);
    }

    /**
     * 下架奖品 (软删除，不删除七牛云图片 - 历史订单仍需展示)
     */
    @Transactional
    public void archivePrize(Long managerId, Long prizeId) {
        Prize prize = prizeRepository.findById(prizeId)
                .orElseThrow(() -> new BizException("奖品不存在"));
        if (!prize.getManagerId().equals(managerId)) {
            throw new BizException(403, "无权操作此奖品");
        }
        prize.setArchived(true);
        prizeRepository.save(prize);
    }

    /** 打卡者看到的可用奖品 (未下架) */
    public List<Prize> getAvailablePrizes(Long managerId) {
        return prizeRepository.findByManagerIdAndArchivedFalseOrderByCreatedAtDesc(managerId);
    }

    /** 管理者看到的全部奖品 (含下架) */
    public List<Prize> getAllPrizes(Long managerId) {
        return prizeRepository.findByManagerIdOrderByCreatedAtDesc(managerId);
    }
}
