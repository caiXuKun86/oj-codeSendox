package com.yupi.yuojcodesandbox.controller;

import com.yupi.yuojcodesandbox.CodeSandbox;
import com.yupi.yuojcodesandbox.CodeSandboxFactory;
import com.yupi.yuojcodesandbox.model.ExecuteCodeRequest;
import com.yupi.yuojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController("/")
public class MainController {

    @GetMapping("/health")
    public String healthCheck() {
        return "ok";
    }

    @Resource
    private CodeSandboxFactory codeSandboxFactory;
    @PostMapping("/exec")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest){
        String language = executeCodeRequest.getLanguage();
        CodeSandbox codeSandbox = codeSandboxFactory.getSandbox(language);
        return codeSandbox.executeCode(executeCodeRequest);
    }
}
