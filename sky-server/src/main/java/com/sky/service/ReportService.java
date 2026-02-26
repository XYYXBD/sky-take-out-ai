package com.sky.service;

import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import jakarta.servlet.http.HttpServletResponse;
//import javax.servlet.http.HttpServletResponse;

import java.time.LocalDate;

public interface ReportService {
    /**
     * 获取营业额报表
     *
     * @param startDate
     * @param endDate
     * @return
     */
    TurnoverReportVO getTurnoverReport(LocalDate startDate, LocalDate endDate);

    /**
     * 获取用户统计报表
     * @param begin
     * @param end
     * @return
     */
    UserReportVO getUserStatistics(LocalDate begin, LocalDate end);

    /**
     * 获取订单统计报表
     * @param begin
     * @param end
     * @return
     */
    OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end);

    /**
     * 获取热销菜品TOP10报表
     * @param begin
     * @param end
     * @return
     */
    SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end);

    /**
     * 导出报表
     * @param response
     */
    void exportBusinessData(HttpServletResponse response);
}
