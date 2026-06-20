package com.yupi.yuojcodesandbox.impl;

import cn.hutool.dfa.WordTree;
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

        String[] compileCmdArray = new String[]{
                javacPath,                      //编译器的绝对路径
                "-encoding", "utf-8",           //指定源文件的字符编码为 UTF-8
                userCodeFile.getAbsolutePath()  //用户 Java 源代码文件的绝对路径
        };
        Process compileProcess = Runtime.getRuntime().exec(compileCmdArray);
        return ProcessUtils.runProcessAndGetMessage(compileProcess);

    }


    @Override
    public String getImageName() {
        return "docker.m.daocloud.io/bellsoft/liberica-openjdk-alpine:8";
    }
}
