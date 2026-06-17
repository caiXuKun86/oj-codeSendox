package com.yupi.yuojcodesandbox;

import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.Map;

@Component
public class CodeSandboxFactory {

    @Resource
    private Map<String, CodeSandbox> sandboxMap;

    /**
     * 根据语言和模式获取对应的沙箱 Bean
     */
    public CodeSandbox getSandbox(String language) {

        // 2. 直接从 Spring 的 Map 里取出现成的 Bean
        CodeSandbox sandbox = sandboxMap.get(language+"DockerCodeSandbox");

        if (sandbox == null) {
            return sandboxMap.get("javaDockerCodeSandbox");
        }

        return sandbox;
    }
}