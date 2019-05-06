package com.czj.service;

import com.czj.error.BusinessException;
import com.czj.service.model.OrderModel;

public interface OrderService {

    OrderModel createOrder(Integer userId,Integer itemId,Integer promoId,Integer amount) throws BusinessException;
}
