package com.yupi.yuojcodesandbox.model;

import lombok.Data;

@Data
public class ExecuteMessage {
    private Integer exitValue;
    private String output;
    private String message;
    private Long time;
    private Long memory;
}
