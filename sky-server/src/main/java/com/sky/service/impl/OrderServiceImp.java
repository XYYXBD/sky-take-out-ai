package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.service.UserProfileService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OrderServiceImp implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;
    @Autowired
    private UserProfileService userProfileService;

    /**
     * 提交订单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        Long userId = BaseContext.getCurrentId();
        //业务异常处理（地址簿/购物车为空等）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook == null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        ShoppingCart shoppingcart = new ShoppingCart();
        shoppingcart.setUserId(userId);
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(shoppingcart);
        if (shoppingCarts == null || shoppingCarts.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        //orders表添加一条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setAddress(addressBook.getDetail());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);
        orderMapper.insert(orders);
        //order_detail表添加多条数据
        List<OrderDetail> orderDetails = new ArrayList<>();
        for (ShoppingCart cart : shoppingCarts) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetails.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetails);
        //shopping_cart表删除对应用户的购物车数据
        shoppingCartMapper.cleanByUserId(userId);

        try {
            userProfileService.updateProfileAfterOrder(userId, orders);
        } catch (Exception e) {
            // 画像更新失败不影响订单主流程，避免业务回滚
            log.error("user profile update after submit failed, userId={}, orderId={}, error={}",
                    userId, orders.getId(), e.getMessage(), e);
        }
        //返回订单提交结果
        OrderSubmitVO orderSubmitVO = new OrderSubmitVO().builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderTime(orders.getOrderTime())
                .orderAmount(orders.getAmount())
                .build();
        return orderSubmitVO;
    }

    /**
     * 历史订单分页查询
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult historyOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageResult pageResult = new PageResult();

        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        PageHelper.startPage(ordersPageQueryDTO.getPage(),
                ordersPageQueryDTO.getPageSize());
        //目前只能查询到List<Orders>
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        //需要将List<Order>构造List<OrderVO>，其中OrderVO包含了Oders和List<OrderDetail>
        //现在把获得List<OrderVO>的方法封装了
        List<OrderVO> orderVOList = getOrderVOList(page);
        pageResult.setRecords(orderVOList);
        pageResult.setTotal(page.getTotal());
        return pageResult;
    }

    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    @Override
    public OrderVO getOrderDetail(Long id) {
        Orders order = orderMapper.selectById(id);
        List<OrderDetail> orderDetails = orderDetailMapper.listByOrderId(order.getId());
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(order, orderVO);
        orderVO.setOrderDetailList(orderDetails);
        return orderVO;
    }

    /**
     * 取消订单
     *
     * @param id
     */
    @Override
    @Transactional
    public void cancelOrder(Long id) throws Exception {
        //先查这个订单
        Orders order = orderMapper.selectById(id);
        // 校验订单是否存在
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Integer status = order.getStatus();
        //已接单和派送中，需要电话沟通，交给商家手动取消
        if(status >= Orders.CONFIRMED){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //待接单，还需要退款
        if (status.equals(Orders.TO_BE_CONFIRMED)) {
//            weChatPayUtil.refund(
//                    order.getNumber(), //商户订单号
//                    order.getNumber(), //商户退款单号
//                    new BigDecimal(0.01),//退款金额，单位 元
//                    new BigDecimal(0.01));//原订单金额
            log.info("申请退款：模拟退款成功");
            //支付状态修改为 退款
            order.setPayStatus(Orders.REFUND);
        }
        //待付款，直接取消
        order.setStatus(Orders.CANCELLED);
        order.setCancelReason("用户取消");
        order.setCancelTime(LocalDateTime.now());
        orderMapper.update(order);
    }
    /**
     * 商家取消订单
     *
     * @param ordersCancelDTO
     */
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.selectById(ordersCancelDTO.getId());

        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == 1) {
            //用户已支付，需要退款
//            String refund = weChatPayUtil.refund(
//                    ordersDB.getNumber(),
//                    ordersDB.getNumber(),
//                    new BigDecimal(0.01),
//                    new BigDecimal(0.01));
//            log.info("申请退款：{}", refund);
            log.info("申请退款：模拟退款成功");
        }

        // 管理端取消订单需要退款，根据订单id更新订单状态、取消原因、取消时间
        Orders orders = new Orders();
        orders.setId(ordersCancelDTO.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }


    /**
     * 再来一单
     *
     * @param id
     */
    @Override
    public void repetitionOrder(Long id) {
        Orders order = orderMapper.selectById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //再来一单就是将原订单中的商品重新加入到购物车中
        List<OrderDetail> orderDetails = orderDetailMapper.listByOrderId(id);
        for (OrderDetail orderDetail : orderDetails) {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail, shoppingCart);
            shoppingCart.setUserId(BaseContext.getCurrentId());
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        }

    }

    /**
     * 顾客催单
      * @param id
     */
    public void reminder(Long id){
        Orders ordersDB = orderMapper.selectById(id);
        if(ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Map map = new HashMap();
        map.put("type", 2);
        map.put("orderId", id);
        map.put("content","订单号" + ordersDB.getNumber());
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    /**
     * 条件分页查询订单
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageResult pageResult = new PageResult();

        PageHelper.startPage(ordersPageQueryDTO.getPage(),
                ordersPageQueryDTO.getPageSize());
        //目前只能查询到List<Orders>
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        if(page == null || page.getTotal() == 0){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        List<OrderVO> orderVOList = getOrderVOList(page);
        pageResult.setRecords(orderVOList);
        pageResult.setTotal(page.getTotal());
        return pageResult;
    }

    /**
     * 将Page<Orders>构造为List<OrderVO>
     * @param page
     * @return
     */
    private List<OrderVO> getOrderVOList(Page<Orders> page){
        List<OrderVO> orderVOList = new ArrayList<>();
        if (page != null && page.getTotal() > 0) {
            for (Orders order : page) {
                //获取OrderDetail列表
                List<OrderDetail> orderDetails = orderDetailMapper.listByOrderId(order.getId());
                //构造OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(order, orderVO);
                orderVO.setOrderDetailList(orderDetails);
                //OrderVO里其实还有个量:orderDishes
                orderVO.setOrderDishes(getOrderDishes(orderDetails));
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    /**
     * 构造订单菜品详情字符串
     * @param orderDetails
     * @return
     */
    private String getOrderDishes(List<OrderDetail> orderDetails){
        String orderDishes = "";
        for (OrderDetail orderDetail : orderDetails) {
            String dishName = orderDetail.getName();
            String dishNum = orderDetail.getNumber().toString();
            orderDishes = orderDishes + dishName + "*" + dishNum + ";";
        }
        return orderDishes;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = new Orders();
        orders.setId(ordersConfirmDTO.getId());
        orders.setStatus(Orders.CONFIRMED);

        orderMapper.update(orders);
    }

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.selectById(ordersRejectionDTO.getId());

        // 订单只有存在且状态为2（待接单）才可以拒单
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == Orders.PAID) {
//            String refund = weChatPayUtil.refund(
//                    ordersDB.getNumber(),
//                    ordersDB.getNumber(),
//                    new BigDecimal(0.01),
//                    new BigDecimal(0.01));
//            log.info("申请退款：{}", refund);
            log.info("申请退款：模拟退款成功");
        }
        // 拒单需要退款，根据订单id更新订单状态、拒单原因、取消时间
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.selectById(userId);
//        //调用微信支付接口，生成预支付交易单
//        JSONObject jsonObject = weChatPayUtil.pay(
//                ordersPaymentDTO.getOrderNumber(), //商户订单号
//                new BigDecimal(0.01), //支付金额，单位 元
//                "苍穹外卖订单", //商品描述
//                user.getOpenid() //微信用户的openid
//        );
//
//        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
//            throw new OrderBusinessException("该订单已支付");
//        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code","ORDERPAID");
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));
        //替代微信支付成功后的数据库订单状态更新
        Integer OrderPaidStatus = Orders.PAID; //模拟支付成功状态
        Integer OrderStatus = Orders.TO_BE_CONFIRMED; //模拟支付成功后订单状态
        LocalDateTime checkoutTime = LocalDateTime.now(); //模拟支付成功后结账时间
        String orderNumber = ordersPaymentDTO.getOrderNumber();
        orderMapper.updateStatus(OrderStatus, OrderPaidStatus, checkoutTime, orderNumber);
        paySuccess(orderNumber);
        log.info("模拟订单支付成功，更新订单状态，订单号：{}" , orderNumber);

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.selectByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);

        Map map = new HashMap<>();
        map.put("type",1);
        map.put("orderId",ordersDB.getId());
        map.put("content","订单号" + outTradeNo);
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }

    /**
     * 派送订单
     *
     * @param id
     */
    public void delivery(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.selectById(id);

        // 校验订单是否存在，并且状态为3
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为派送中
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(orders);
    }

    /**
     * 完成订单
     *
     * @param id
     */
    public void complete(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.selectById(id);

        // 校验订单是否存在，并且状态为4
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为完成
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(orders);
    }

}
