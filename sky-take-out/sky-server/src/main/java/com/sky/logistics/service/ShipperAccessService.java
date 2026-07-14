package com.sky.logistics.service;

import com.sky.logistics.common.LogisticsForbiddenException;
import com.sky.logistics.dto.CargoCreateDTO;
import com.sky.logistics.dto.CargoQueryDTO;
import com.sky.logistics.entity.CargoRecord;
import com.sky.logistics.mapper.LogisticsCargoMapper;
import com.sky.logistics.vo.LogisticsUserContextVO;
import org.springframework.stereotype.Service;

/** 货主数据域校验，确保 SHIPPER 只能访问本人订单。 */
@Service
public class ShipperAccessService {

    private final LogisticsSecurityService securityService;
    private final LogisticsCargoMapper cargoMapper;

    public ShipperAccessService(LogisticsSecurityService securityService, LogisticsCargoMapper cargoMapper) {
        this.securityService = securityService;
        this.cargoMapper = cargoMapper;
    }

    public LogisticsUserContextVO current(String authorization) {
        return securityService.currentOrDefault(authorization, "USR-001", "SHIPPER");
    }

    public void applyOwnerScope(CargoQueryDTO query, String authorization) {
        LogisticsUserContextVO user = current(authorization);
        if (isShipper(user)) {
            query.setOwnerId(user.getUserId());
        }
    }

    public void assignOwner(CargoCreateDTO request, String authorization) {
        LogisticsUserContextVO user = current(authorization);
        if (isShipper(user) || request.getOwnerId() == null) {
            request.setOwnerId(user.getUserId());
        }
    }

    public void requireOwnedIfShipper(String cargoId, String authorization) {
        LogisticsUserContextVO user = current(authorization);
        if (isShipper(user) && cargoMapper.countOwnedBy(cargoId, user.getUserId()) == 0) {
            throw new LogisticsForbiddenException("只能查询和操作本人提交的宠物运输订单");
        }
    }

    public void requireShipperOrAdmin(String authorization) {
        securityService.requireAnyRole(authorization, "SHIPPER", "ADMIN");
    }

    /** 货主仅可在订单尚未受理时修正需求；受理后改址必须走改址申请。 */
    public void requireEditableIfShipper(String cargoId, String authorization) {
        LogisticsUserContextVO user = current(authorization);
        if (!isShipper(user)) {
            return;
        }
        requireOwnedIfShipper(cargoId, authorization);
        CargoRecord cargo = cargoMapper.findByCargoId(cargoId);
        if (cargo == null || !"CREATED".equals(cargo.getStatus())) {
            throw new LogisticsForbiddenException("订单受理后不能直接修改，目的地变化请提交改址申请");
        }
    }

    public void requireCancelableIfShipper(String cargoId, String authorization) {
        LogisticsUserContextVO user = current(authorization);
        if (!isShipper(user)) {
            return;
        }
        requireOwnedIfShipper(cargoId, authorization);
        CargoRecord cargo = cargoMapper.findByCargoId(cargoId);
        if (cargo == null || !"CREATED".equals(cargo.getStatus())) {
            throw new LogisticsForbiddenException("订单受理后不能由货主直接取消，请联系调度人员处理");
        }
    }

    public void rejectShipper(String authorization, String message) {
        if (isShipper(current(authorization))) {
            throw new LogisticsForbiddenException(message);
        }
    }

    public void requireSelfIfShipper(String userId, String authorization) {
        LogisticsUserContextVO user = current(authorization);
        if (isShipper(user) && !user.getUserId().equals(userId)) {
            throw new LogisticsForbiddenException("货主只能查询和维护自己的账户资料");
        }
    }

    private boolean isShipper(LogisticsUserContextVO user) {
        return user != null && "SHIPPER".equals(user.getRole());
    }
}
