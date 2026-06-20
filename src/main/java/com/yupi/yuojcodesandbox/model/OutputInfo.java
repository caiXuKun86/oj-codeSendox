package com.yupi.yuojcodesandbox.model;

import lombok.Data;

@Data
public class OutputInfo {

    /**
     * 如内存溢出,时间溢出,程序报错,output为空时才有该字段
     */
    private String message;
    /**
     * 单次运行消耗时间
     */
    private Long time;
    /**
     * 单次运行消耗内容
     */
    private Long memory;
}
