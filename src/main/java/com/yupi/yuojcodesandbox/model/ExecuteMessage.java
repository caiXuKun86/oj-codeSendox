package com.yupi.yuojcodesandbox.model;

import lombok.Data;

@Data
public class ExecuteMessage {
    private String output;
    private Integer exitValue;
    private String message;
    private Long time;
    private Long memory;
}