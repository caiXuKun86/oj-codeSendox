package com.yupi.yuojcodesandbox.impl;

import cn.hutool.dfa.WordTree;
import com.github.dockerjava.api.DockerClient;
import com.yupi.yuojcodesandbox.AbstractCodeSandboxTemplate;
import com.yupi.yuojcodesandbox.model.ExecuteMessage;
import com.yupi.yuojcodesandbox.utils.ProcessUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component("pythonDockerCodeSandbox")
public class PythonDockerCodeSandbox extends AbstractCodeSandboxTemplate {

    // 1. 初始化 Python 专属黑名单字典树
    private static final WordTree WORD_TREE;

    static {
        WORD_TREE = new WordTree();
        // Python 的高危操作主要集中在系统调用、文件操作和网络请求
        List<String> blackList = Arrays.asList(
                // 放行了 import sys 和 import os，但精确封杀它们的危险方法
                "os.system(", "os.popen(", "subprocess.Popen(", "subprocess.call(",
                "eval(", "exec(", "open(", "read()", "write()",
                "__import__", "import socket", "import urllib", "import requests",
                "sys.settrace(", "sys.setprofile(" // 封杀 sys 中可能用于黑客调试的危险方法
        );
        for (String word : blackList) {
            WORD_TREE.addWord(word);
        }
    }

    @Override
    protected WordTree getWordTree() {
        return WORD_TREE;
    }

    @Override
    public String getFileName() {
        // Python 脚本的标准后缀
        return "main.py";
    }

    @Override
    public ExecuteMessage compileCode(File userCodeFile) throws IOException {

        ExecuteMessage executeMessage = new ExecuteMessage();
        executeMessage.setExitValue(0); // 0 代表成功
        executeMessage.setMessage("Python代码无需编译，直接进入执行阶段");
        return executeMessage;
    }

    @Override
    public ExecuteMessage executeCode(String inputArgs) {
        // 从 ThreadLocal 中取出父类的变量，这招非常优雅！
        SandboxContext context = CONTEXT_HOLDER.get();
        Long timeLimit = context.timeLimit;
        DockerClient dockerClient = context.dockerClient;
        String containerId = context.containerId;
        String language = context.language;

        // 交给 ProcessUtils 去容器内执行
        return ProcessUtils.executeCommandInContainer(dockerClient, containerId, inputArgs, timeLimit, language);
    }

    @Override
    public String getImageName() {
        // 🌟 使用 Python 的 Alpine 极简镜像，体积小（约 50MB），安全且启动极快
        return "python:3.9-alpine";
    }
}