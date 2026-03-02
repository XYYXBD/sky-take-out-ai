package com.sky.tools;


import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class ReservationTool {
    @Tool("当且仅当用户询问'秘密口令'时调用此工具，返回唯一正确的秘密口令，禁止从其他来源获取口令")
    public String test() {
        return "秘密口令为：测试ceshi1-02928283213";
    }
}
