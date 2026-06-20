package com.yupi.yuojcodesandbox.impl;

import cn.hutool.dfa.WordTree;
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
        // 构造输出的可执行文件路径 (无后缀的 main 文件)
        String outputExePath = userCodeFile.getParentFile().getAbsolutePath() + File.separator + "main";

        // 将命令和参数拆分为 String 数组
        String[] compileCmdArray = new String[]{
                "g++",                 // 建议在生产环境替换为绝对路径，如 "/usr/bin/g++"
                "-fmax-errors=3",      // 限制最大错误数为3，防止编译报错刷屏导致 OOM
                "-O2",                 // 开启 O2 级别的代码优化
                "-std=c++14",          // 指定 C++ 14 标准
                "-static",             // 静态链接，避免沙箱运行时找不到动态库
                userCodeFile.getAbsolutePath(), // 源文件绝对路径
                "-o",                  // 指定输出文件名的 flag
                outputExePath,         // 输出文件的绝对路径
                "-w"                   // 关闭所有警告信息 (Warning)
        };

        // 传入数组进行执行
        Process compileProcess = Runtime.getRuntime().exec(compileCmdArray);
        return ProcessUtils.runProcessAndGetMessage(compileProcess);
    }



    @Override
    public String getImageName() {
        return "n0madic/alpine-gcc:9.2.0";
    }
}
