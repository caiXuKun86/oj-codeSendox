package com.yupi.yuojcodesandbox.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.yupi.yuojcodesandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * 进程工具类
 */
@Slf4j
public class ProcessUtils {

    /**
     * 执行进程并获取信息
     *
     * @param runProcess
     * @return
     */
    public static ExecuteMessage runProcessAndGetMessage(Process runProcess) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            // 等待程序执行，获取错误码
            int exitValue = runProcess.waitFor();
            executeMessage.setExitValue(exitValue);
            // 正常退出
            if (exitValue == 0) {
                // 分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                // 逐行读取
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine);
                }
                executeMessage.setOutput(compileOutputStringBuilder.toString());
            } else {
                // 分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                // 逐行读取
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine);
                }
                executeMessage.setMessage(compileOutputStringBuilder.toString());

                // 分批获取进程的错误输出
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()));
                StringBuilder errorCompileOutputStringBuilder = new StringBuilder();

                // 逐行读取
                String errorCompileOutputLine;
                while ((errorCompileOutputLine = errorBufferedReader.readLine()) != null) {
                    errorCompileOutputStringBuilder.append(errorCompileOutputLine);
                }
                executeMessage.setMessage(errorCompileOutputStringBuilder.toString());
            }
        } catch (Exception e) {
            log.error("编译过程异常{}", e.getMessage());
        }
        return executeMessage;
    }


    public static ExecuteMessage executeCommandInContainer(DockerClient dockerClient, String containerId,
                                                           String inputFilePath, String userOutputParentPath,
                                                           Long timeLimit, String language, Integer index) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        String[] cmd = getCmdByLanguage(language, inputFilePath, index);

        // 1.必须开启 Attach 流，否则 awaitCompletion 会瞬间返回，导致后台进程被中途强杀！
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withCmd(cmd)
                .exec();

        String execId = execCreateCmdResponse.getId();
        if (execId == null) {
            throw new RuntimeException("创建 Docker 执行命令失败");
        }

        try {
            // 2. 启动执行命令
            ExecStartCmd execStartCmd = dockerClient.execStartCmd(execId);
            boolean isFinished = execStartCmd.exec(new ExecStartResultCallback()).awaitCompletion(timeLimit, TimeUnit.MILLISECONDS);
            // 3. 处理超时
            if (!isFinished) {
                executeMessage.setExitValue(-1);
                executeMessage.setMemory(0L);
                executeMessage.setMessage("Time Limit Exceeded");
                return executeMessage;
            }
            // 4. 捞取真正的 exitCode
            Long exitCode = dockerClient.inspectExecCmd(execId).exec().getExitCodeLong();
            executeMessage.setExitValue(exitCode != null ? exitCode.intValue() : -1);
            // 读取错误输出
            File errorFile = new File(userOutputParentPath, "error_" + index + ".txt");
            if (errorFile.exists()) {
                executeMessage.setMessage(FileUtil.readUtf8String(errorFile).trim());
            }
            // 读取底层资源监控统计
            File statsFile = new File(userOutputParentPath, "stats_" + index + ".txt");
            if (statsFile.exists()) {
                String statsStr = FileUtil.readUtf8String(statsFile).trim();
                // 精确解析 time 命令写入的时间和内存
                if (statsStr.contains("time=")) {
                    // 1. 截取 time= 和 &memory= 之间的时间字符串 (例如 "0.02")
                    String timeStr = StrUtil.subBetween(statsStr, "time=", "&memory=");
                    executeMessage.setTime((long) (Double.parseDouble(timeStr) * 1000));

                    // 2. 截取 memory= 之后的所有内容作为内存字符串 (例如 "14524")
                    String memStr = StrUtil.subAfter(statsStr, "memory=", false).trim();

                    // 3. 健壮性处理：防止 time 命令在尾部输出多余的换行或空白字符
                    memStr = memStr.split("\\s+")[0];
                    executeMessage.setMemory(Long.parseLong(memStr));
                }
            }
        } catch (Exception e) {
            log.error("代码执行异常{}", e.getMessage());
            executeMessage.setExitValue(-1);
            executeMessage.setMessage("执行异常: " + e.getMessage());
        }
        return executeMessage;
    }

    private static String[] getCmdByLanguage(String language, String inputFilePath, Integer index) {
        String timeFormat = "time=%e&memory=%M";
        // 约定容器内的输出重定向路径（对应我们挂载的可写目录）
        String out = "/app/output/output_" + index + ".txt";
        String err = "/app/output/error_" + index + ".txt";
        String stats = "/app/output/stats_" + index + ".txt";

        if (language.equals("java")) {
            // -o %s: 将 time 命令的统计结果写到 stats.txt
            // > %s 2> %s: 将程序的正常输出写到 output.txt，报错写到 error.txt
            // 注意：-cp 路径改为了 /app/code
            String cmd = String.format("/usr/bin/time -o %s -f %s java -Dfile.encoding=UTF-8 -cp /app/code Main < %s > %s 2> %s",
                    stats, timeFormat, inputFilePath, out, err);
            return new String[]{"sh", "-c", cmd};

        } else if (language.equals("cpp")) {
            // 注意：C++ 编译后的可执行文件路径改为 /app/code/main
            String cmd = String.format("/usr/bin/time -o %s -f %s /app/code/main < %s > %s 2> %s",
                    stats, timeFormat, inputFilePath, out, err);
            return new String[]{"sh", "-c", cmd};

        } else if (language.equals("python")) {
            // 注意：Python 脚本路径改为 /app/code/main.py
            String cmd = String.format("/usr/bin/time -o %s -f %s python3 /app/code/main.py < %s > %s 2> %s",
                    stats, timeFormat, inputFilePath, out, err);
            return new String[]{"sh", "-c", cmd};
        }

        return new String[]{}; // 兜底处理
    }
}


