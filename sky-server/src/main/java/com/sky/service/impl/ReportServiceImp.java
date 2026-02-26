package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImp implements ReportService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 获取营业额报表
     * @param startDate
     * @param endDate
     * @return
     */
    @Override
    @Transactional
    public TurnoverReportVO getTurnoverReport(LocalDate startDate, LocalDate endDate) {
        //TurnoverReportVO居然用两个String来存数据
        //dateList
        List<LocalDate> dateList = new ArrayList<>();
        List<Double> turnoverList = new ArrayList<>();

        dateList.add(startDate);
        while(!startDate.equals(endDate)){
            startDate = startDate.plusDays(1);
            dateList.add(startDate);
        }
        for (LocalDate date : dateList) {
            LocalDateTime startOfDay = LocalDateTime.of(date,LocalTime.MIN);
            LocalDateTime endOfDay = LocalDateTime.of(date,LocalTime.MAX);
            Map map = new HashMap<>();
            map.put("begin",startOfDay);
            map.put("end",endOfDay);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.countAmountByMap(map);
            turnoverList.add(turnover == null ? 0.0 : turnover);
        }
        TurnoverReportVO turnoverReportVO = TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();
        return turnoverReportVO;
    }

    /**
     * 获取用户统计报表
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        List<Integer> totalUserList = new ArrayList<>();
        List<Integer> newUserList = new ArrayList<>();

        dateList.add(begin);
        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        for (LocalDate date : dateList) {
            LocalDateTime startOfDay = LocalDateTime.of(date,LocalTime.MIN);
            LocalDateTime endOfDay = LocalDateTime.of(date,LocalTime.MAX);
            Map map = new HashMap<>();
            map.put("end",endOfDay);
            Integer totalUser = userMapper.countByMap(map);
            totalUserList.add(totalUser == null ? 0 : totalUser);
            map.put("begin",startOfDay);
            Integer newUser = userMapper.countByMap(map);
            newUserList.add(newUser == null ? 0 : newUser);
        }

        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .build();
    }

    @Override
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        List<Integer> orderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();

        dateList.add(begin);
        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        for (LocalDate date : dateList) {
            LocalDateTime startOfDay = LocalDateTime.of(date,LocalTime.MIN);
            LocalDateTime endOfDay = LocalDateTime.of(date,LocalTime.MAX);
            Integer orderCount = getOrderCount(startOfDay, endOfDay, null);
            Integer validOrderCount = getOrderCount(startOfDay, endOfDay, Orders.COMPLETED);
            orderCountList.add(orderCount == null ? 0 : orderCount);
            validOrderCountList.add(validOrderCount == null ? 0 : validOrderCount);
        }

        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();
        Integer newOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();

        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(newOrderCount)
                .orderCompletionRate(totalOrderCount == 0 ? 0.0 : (newOrderCount.doubleValue() / totalOrderCount))
                .build();
    }

    /**
     * 获取热销菜品TOP10报表
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime startOfDay = LocalDateTime.of(begin,LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(end,LocalTime.MAX);
        List<GoodsSalesDTO> salesTop10 = orderMapper.selectSalesTop10(startOfDay, endOfDay);
        List<String> names = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String nameList = StringUtils.join(names, ",");
        List<Integer> numbers = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String numberList = StringUtils.join(numbers, ",");

        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    /**
     * 获取订单数量
     * @param startOfDay
     * @param endOfDay
     * @param status
     * @return
     */
    private Integer getOrderCount(LocalDateTime startOfDay, LocalDateTime endOfDay, Integer status) {
        Map map = new HashMap<>();
        map.put("end",endOfDay);
        map.put("begin",startOfDay);
        map.put("status", status);
        Integer count = orderMapper.countByMap(map);
        return count == null ? 0 : count;
    }

    /**
     * 导出营业数据
     * @param response
     */
    @Override
    public void exportBusinessData(HttpServletResponse response) {
        //查询营业数据（最近30天）
        LocalDate beginDate = LocalDate.now().minusDays(30);
        LocalDate endDate = LocalDate.now().minusDays(1);
        BusinessDataVO businessDataVO = workspaceService.getBusinessData(LocalDateTime.of(beginDate, LocalTime.MIN), LocalDateTime.of(endDate, LocalTime.MAX));
        //通过POI创建excel文件
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        //写入到excel
        try {
            //创建excel
            XSSFWorkbook excel = new XSSFWorkbook(in);
            //填充数据
            XSSFSheet sheet = excel.getSheet("sheet1");
            sheet.createRow(1).createCell(1).setCellValue("时间" + beginDate + "至" + endDate);

            XSSFRow row = sheet.createRow(3);
            row.createCell(2).setCellValue(businessDataVO.getTurnover());
            row.createCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
            row.createCell(6).setCellValue(businessDataVO.getNewUsers());

            row = sheet.createRow(4);
            row.createCell(2).setCellValue(businessDataVO.getValidOrderCount());
            row.createCell(4).setCellValue(businessDataVO.getUnitPrice());

            //填充明细数据
            for (int i = 0; i < 30; i++) {
                LocalDate date = beginDate.plusDays(i);
                //查询某一天的营业数据
                BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));

                //获得某一行
                row = sheet.getRow(7 + i);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessData.getTurnover());
                row.getCell(3).setCellValue(businessData.getValidOrderCount());
                row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessData.getUnitPrice());
                row.getCell(6).setCellValue(businessData.getNewUsers());
            }

            //输出流把excel文件下载到客户端浏览器
            ServletOutputStream out = response.getOutputStream();
            excel.write(out);
            //关闭资源
            out.close();
            excel.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
