package com.yupi.yuojcodesandbox.impl;

import cn.hutool.dfa.WordTree;
import com.github.dockerjava.api.DockerClient;
import com.yupi.yuojcodesandbox.AbstractCodeSandboxTemplate;
import com.yupi.yuojcodesandbox.model.ExecuteMessage;
import com.yupi.yuojcodesandbox.utils.ProcessUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component("cppDockerCodeSandbox")
public class CppDockerCodeSandbox extends AbstractCodeSandboxTemplate {

    @Override
    public String getFileName() {
        return "main.cpp";
    }

    @Override
    protected WordTree getWordTree() {
        return null;
    }

    @Override
    public ExecuteMessage compileCode(File userCodeFile) throws IOException {
        String compileCmd = String.format("g++ -fmax-errors=3 -O2 -std=c++14 -static %s -o %s -w",
                userCodeFile.getAbsolutePath(),
                userCodeFile.getParentFile().getAbsolutePath() + File.separator + "main");

        Process compileProcess = Runtime.getRuntime().exec(compileCmd);
        return ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
    }

    @Override
    public ExecuteMessage executeCode(String inputArgs) {
        // 从 ThreadLocal 中取出父类的变量
        SandboxContext context = CONTEXT_HOLDER.get();
        Long timeLimit = context.timeLimit;
        DockerClient dockerClient = context.dockerClient;
        String containerId = context.containerId;
        String language = context.language;
        return ProcessUtils.executeCommandInContainer(dockerClient,containerId,inputArgs,timeLimit,language);
    }

    @Override
    public String getImageName() {
        return "n0madic/alpine-gcc:9.2.0";
    }
}
