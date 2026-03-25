package com.keep.service;

import com.keep.entity.PointsBatch;
import com.keep.entity.PointsTransaction;
import com.keep.exception.BizException;
import com.keep.repository.PointsBatchRepository;
import com.keep.repository.PointsTransactionRepository;
import com.keep.util.DateUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PointsService {

    private final PointsBatchRepository batchRepository;
    private final PointsTransactionRepository txRepository;

    public PointsService(PointsBatchRepository batchRepository,
                         PointsTransactionRepository txRepository) {
        this.batchRepository = batchRepository;
        this.txRepository = txRepository;
    }

    /**
     * 发放积分 (周期奖励/额外奖励)
     */
    @Transactional
    public void earnPoints(Long checkerId, Long managerId, int amount, String source, int validityDays) {
        LocalDate today = currentDate();
        PointsBatch batch = new PointsBatch();
        batch.setCheckerId(checkerId);
        batch.setManagerId(managerId);
        batch.setOriginalAmount(amount);
        batch.setRemainingAmount(amount);
        batch.setSource(source);
        batch.setExpireDate(today.plusDays(validityDays));
        batchRepository.save(batch);

        int balance = batchRepository.sumAvailablePoints(checkerId);
        recordTransaction(checkerId, "EARN", amount, balance, source + " +" + amount, batch.getId());
    }

    /**
     * 消费积分 (FIFO: 优先消费最快过期的)
     * @return 实际消费的积分批次明细 (用于取消时还原)
     */
    @Transactional
    public List<ConsumedDetail> consumePoints(Long checkerId, int amount) {
        List<PointsBatch> batches = batchRepository.findAvailableBatches(checkerId);
        int totalAvailable = batches.stream().mapToInt(PointsBatch::getRemainingAmount).sum();

        if (totalAvailable < amount) {
            throw new BizException("积分不足");
        }

        java.util.List<ConsumedDetail> details = new java.util.ArrayList<>();
        int remaining = amount;

        for (PointsBatch batch : batches) {
            if (remaining <= 0) break;
            int consume = Math.min(batch.getRemainingAmount(), remaining);
            batch.setRemainingAmount(batch.getRemainingAmount() - consume);
            batchRepository.save(batch);
            details.add(new ConsumedDetail(batch.getId(), consume, batch.getExpireDate()));
            remaining -= consume;
        }

        int balance = batchRepository.sumAvailablePoints(checkerId);
        recordTransaction(checkerId, "CONSUME", -amount, balance, "兑换消耗 -" + amount, null);

        return details;
    }

    /**
     * 还原积分 (取消兑换时)
     * @return 实际过期无法还原的积分数
     */
    @Transactional
    public int refundPoints(Long checkerId, List<ConsumedDetail> details) {
        int expiredAmount = 0;
        int refundedAmount = 0;

        for (ConsumedDetail detail : details) {
            PointsBatch batch = batchRepository.findById(detail.batchId()).orElse(null);
            if (batch == null) continue;

            // 已过期的积分无法还原
            if (batch.getExpireDate().isBefore(currentDate())) {
                expiredAmount += detail.amount();
            } else {
                batch.setRemainingAmount(batch.getRemainingAmount() + detail.amount());
                batch.setExpired(false);
                batchRepository.save(batch);
                refundedAmount += detail.amount();
            }
        }

        if (refundedAmount > 0) {
            int balance = batchRepository.sumAvailablePoints(checkerId);
            recordTransaction(checkerId, "REFUND", refundedAmount, balance,
                    "取消兑换还原 +" + refundedAmount, null);
        }

        if (expiredAmount > 0) {
            recordTransaction(checkerId, "EXPIRE", 0,
                    batchRepository.sumAvailablePoints(checkerId),
                    "取消兑换但积分已过期 " + expiredAmount + " 分", null);
        }

        return expiredAmount;
    }

    /**
     * 惩罚扣分 (积分最低为0)
     */
    @Transactional
    public void penaltyDeduct(Long checkerId, int amount) {
        List<PointsBatch> batches = batchRepository.findAvailableBatches(checkerId);
        int totalAvailable = batches.stream().mapToInt(PointsBatch::getRemainingAmount).sum();

        int actualDeduct = Math.min(totalAvailable, amount);
        if (actualDeduct <= 0) return;

        int remaining = actualDeduct;
        for (PointsBatch batch : batches) {
            if (remaining <= 0) break;
            int consume = Math.min(batch.getRemainingAmount(), remaining);
            batch.setRemainingAmount(batch.getRemainingAmount() - consume);
            batchRepository.save(batch);
            remaining -= consume;
        }

        int balance = batchRepository.sumAvailablePoints(checkerId);
        recordTransaction(checkerId, "PENALTY", -actualDeduct, balance,
                "连续未打卡惩罚 -" + actualDeduct, null);
    }

    /**
     * 过期积分处理
     */
    @Transactional
    public void expireBatches() {
        List<PointsBatch> expired = batchRepository.findExpiredBatches(currentDate());
        for (PointsBatch batch : expired) {
            int expiredAmount = batch.getRemainingAmount();
            batch.setRemainingAmount(0);
            batch.setExpired(true);
            batchRepository.save(batch);

            int balance = batchRepository.sumAvailablePoints(batch.getCheckerId());
            recordTransaction(batch.getCheckerId(), "EXPIRE", -expiredAmount, balance,
                    "积分过期 -" + expiredAmount, batch.getId());
        }
    }

    public int getAvailablePoints(Long checkerId) {
        return batchRepository.sumAvailablePoints(checkerId);
    }

    public int getExpiringPointsIn30Days(Long checkerId) {
        return batchRepository.sumExpiringPoints(checkerId, currentDate().plusDays(30));
    }

    public List<PointsTransaction> getTransactions(Long checkerId) {
        return txRepository.findByCheckerIdOrderByCreatedAtDesc(checkerId);
    }

    private void recordTransaction(Long checkerId, String type, int amount, int balance,
                                   String remark, Long batchId) {
        PointsTransaction tx = new PointsTransaction();
        tx.setCheckerId(checkerId);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setBalanceAfter(balance);
        tx.setRemark(remark);
        tx.setBatchId(batchId);
        txRepository.save(tx);
    }

    private LocalDate currentDate() {
        return DateUtil.nowUtc8().toLocalDate();
    }

    public record ConsumedDetail(Long batchId, int amount, LocalDate expireDate) {}
}
