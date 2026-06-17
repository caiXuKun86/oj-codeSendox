package com.yupi.yuojcodesandbox.impl;

import cn.hutool.core.io.FileUtil;
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

@Component("javaDockerCodeSandbox")
public class JavaDockerCodeSandbox extends AbstractCodeSandboxTemplate {
    // Java 沙箱专属的静态字典树
    private static final WordTree JAVA_WORD_TREE;

    static {
        JAVA_WORD_TREE = new WordTree();
        List<String> blackList = Arrays.asList(
                "Files", "File", "FileInputStream", "FileOutputStream",
                "RandomAccessFile", "FileReader", "FileWriter",
                "Runtime", "ProcessBuilder", "exec",
                "Socket", "ServerSocket", "URL", "HttpURLConnection",
                "java.lang.reflect", "Method", "invoke", "ClassLoader",
                "System.exit", "System.setProperty"
        );
        for (String word : blackList) {
            JAVA_WORD_TREE.addWord(word);
        }
    }

    @Override
    protected WordTree getWordTree() {
        return JAVA_WORD_TREE;
    }

    @Override
    public String getFileName() {
        return "Main.java";
    }

    @Override
    public ExecuteMessage compileCode(File userCodeFile) throws IOException {
        String javaHome = System.getProperty("java.home");
        String javacPath = javaHome + File.separator + "bin" + File.separator + "javac";
        // 如果 java.home 指向的是 jre 目录，则需要往上走一层到 JDK 根目录
        if (!FileUtil.exist(javacPath)) {
            javacPath = new File(javaHome).getParent() + File.separator + "bin" + File.separator + "javac";
        }

        // 使用动态获取到的绝对路径来执行编译
        String compileCmd = String.format("%s -encoding utf-8 %s", javacPath, userCodeFile.getAbsolutePath());

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
        return ProcessUtils.executeCommandInContainer(dockerClient, containerId, inputArgs, timeLimit,language);
    }

    @Override
    public String getImageName() {
        return "docker.m.daocloud.io/bellsoft/liberica-openjdk-alpine:8";
    }
}
