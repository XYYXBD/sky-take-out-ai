package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理超时订单，每分钟触发一次
     */
    @Scheduled(cron = "0 * * * * *")
    public void processTimeoutOrder(){
        log.info("处理超时订单");
        //查找超时的订单(15分钟未支付)
        List<Orders> ordersList = orderMapper.selectByStatusAndOrderTime(Orders.PENDING_PAYMENT, LocalDateTime.now().minusMinutes(15));
        if(ordersList != null && !ordersList.isEmpty()){
            for (Orders order : ordersList) {
                //更新订单状态为已取消
                order.setStatus(Orders.CANCELLED);
                order.setCancelReason("订单超时未支付，系统自动取消");
                order.setCancelTime(LocalDateTime.now());
                orderMapper.update(order);
                log.info("订单超时未支付，已取消订单:{}", order.getId());
            }
        } else {
            log.info("无超时订单需要处理");
        }
    }

    /**
     *  处理配送中的订单，每天凌晨1点触发一次
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void processDeliveryOrders(){
        log.info("处理配送中的订单");
        //查找仍然在配送中的订单
        List<Orders> deliveryOrders = orderMapper.selectByStatusAndOrderTime(Orders.DELIVERY_IN_PROGRESS, LocalDateTime.now().minusHours(1));
        if(deliveryOrders != null && !deliveryOrders.isEmpty()){
            for (Orders order : deliveryOrders) {
                //更新订单状态为已完成
                order.setStatus(Orders.COMPLETED);
                orderMapper.update(order);
                log.info("配送时间过长，已自动完成订单:{}", order.getId());
            }
        } else {
            log.info("无配送中订单需要处理");
        }
    }
}
