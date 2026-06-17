package com.yupi.yuojcodesandbox.model;

import lombok.Data;

import java.util.List;

@Data
public class ExecuteCodeResponse {
    /**
     * 输出集合
     */
    private List<OutputInfo> outputList;
    /**
     * 运行前异常,如编译错误,outputList为空时有该字段
     */
    private String message;
    /**
     *  运行状态
     */
    private Integer status;
    private String language;



}
