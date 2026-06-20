package com.yupi.yuojcodesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecuteCodeRequest {
    private String inputParentPath;
    private String userOutputParentPath;
    private Integer count;
    private String code;
    private String language;
    private Long timeLimit;
    private Long memoryLimit;
}
