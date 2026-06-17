package com.yupi.yuojcodesandbox.utils;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.yupi.yuojcodesandbox.model.ExecuteMessage;

import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * 进程工具类
 */
public class ProcessUtils {

    /**
     * 执行进程并获取信息
     *
     * @param runProcess
     * @param opName
     * @return
     */
    public static ExecuteMessage runProcessAndGetMessage(Process runProcess, String opName) {
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
            e.printStackTrace();
        }
        return executeMessage;
    }

    public static ExecuteMessage runInteractProcessAndGetMessage(Process runProcess, String args) {
        ExecuteMessage executeMessage = new ExecuteMessage();

        try {
            StopWatch stopWatch = new StopWatch();
            // 向控制台输入程序
            OutputStream outputStream = runProcess.getOutputStream();
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);

            // 【修改点 1】加上换行符，模拟按下回车
            outputStreamWriter.write(args + "\n");
            outputStreamWriter.flush();

            // 【修改点 2】写入完成后，立即关闭流，告诉子进程输入结束 (EOF)
            outputStreamWriter.close();
            outputStream.close();
            stopWatch.start();

            // 判断是否超时
            long timeoutMillis = 2000L;
            boolean isFinished = runProcess.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            stopWatch.stop();
            if (!isFinished) {
                // 如果返回 false，说明超时了！
                runProcess.destroyForcibly();

                stopWatch.stop(); // 记得停止计时
                executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
                // 【设置状态】设定一个特殊的错误码或直接写死 Message
                executeMessage.setExitValue(-1);
                executeMessage.setMemory(1024L);
                executeMessage.setMessage("Time Limit Exceeded");

                return executeMessage;
            }

            int exitValue = runProcess.waitFor();
            if (exitValue == 0) {
                // 分批获取进程的正常输出
                InputStream inputStream = runProcess.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder compileOutputStringBuilder = new StringBuilder();

                // 逐行读取
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine);
                }
                executeMessage.setOutput(compileOutputStringBuilder.toString());
                inputStream.close();
            } else {
                // 分批获取进程的正常输出
                InputStream inputStream = runProcess.getInputStream();

                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                // 逐行读取
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine);
                }
                executeMessage.setOutput(compileOutputStringBuilder.toString());

                // 分批获取进程的错误输出
                InputStream errorStream = runProcess.getErrorStream();
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(errorStream));
                StringBuilder errorCompileOutputStringBuilder = new StringBuilder();

                // 逐行读取
                String errorCompileOutputLine;
                while ((errorCompileOutputLine = errorBufferedReader.readLine()) != null) {
                    errorCompileOutputStringBuilder.append(errorCompileOutputLine);
                }
                executeMessage.setMessage(errorCompileOutputStringBuilder.toString());
                inputStream.close();
                errorStream.close();
            }

            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
            executeMessage.setMemory(1024L);

            // 释放读取流资源
            runProcess.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeMessage;
    }

    /**
     * 在 Docker 容器中执行用户的 Java 程序
     *
     * @param containerId 启动的容器ID
     * @param inputArgs   测试用例输入（例如 "1 2"）
     * @return 统一的执行信息
     */
    public static ExecuteMessage executeCommandInContainer(DockerClient dockerClient, String containerId, String inputArgs, Long timeLimit, String language) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        String[] cmd = getCmdByLanguage(language);
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                .withCmd(cmd)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .exec();
        String execId = execCreateCmdResponse.getId();
        if (execId == null) {
            throw new RuntimeException("创建 Docker 执行命令失败");
        }
        ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrStream = new ByteArrayOutputStream();
        ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback(stdoutStream, stderrStream);
        try {

            dockerClient.statsCmd(containerId);
            ExecStartCmd execStartCmd = dockerClient.execStartCmd(execId);
            // 🌟 关键点：将你的测试用例输入作为 Stdin 喂给容器
            if (StrUtil.isNotBlank(inputArgs)) {
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream((inputArgs + "\n").getBytes());
                execStartCmd.withStdIn(byteArrayInputStream);
            }
            boolean isFinished = execStartCmd.exec(execStartResultCallback).awaitCompletion(timeLimit, TimeUnit.MILLISECONDS);
            if (!isFinished) {
                executeMessage.setExitValue(-1);
                executeMessage.setMemory(0L);
                executeMessage.setMessage("Time Limit Exceeded");
                return executeMessage;
            }
            // 6. 捞取执行状态码 (通过 inspectExecCmd 拿到真正的 exitCode)
            InspectExecResponse inspectExecResponse = dockerClient.inspectExecCmd(execId).exec();
            Long exitCode = inspectExecResponse.getExitCodeLong();
            executeMessage.setExitValue(exitCode != null ? exitCode.intValue() : -1);

            String stdoutStr = stdoutStream.toString("UTF-8").trim();
            String stderrStr = stderrStream.toString("UTF-8").trim();
            executeMessage.setOutput(stdoutStr);
            String timeTag = "YU_OJ_STATS_TIME:";
            String memTag = "_MEM:";
            if (stderrStr.contains(timeTag)) {
                try {
                    String timeStr = StrUtil.subBetween(stderrStr, timeTag, memTag);
                    executeMessage.setTime((long) (Double.parseDouble(timeStr) * 1000));

                    String memStr = StrUtil.subAfter(stderrStr, memTag, true).trim();
                    memStr = memStr.split("\\s+")[0];
                    executeMessage.setMemory(Long.parseLong(memStr));

                    // 3.清洗日志：把这些底层监控标识从错误流中剔除，避免污染前端展示
                    executeMessage.setMessage(StrUtil.subBefore(stderrStr, timeTag, true).trim()) ;

                } catch (Exception e) {
                    // 如果用户的代码输出了极度畸形的内容干扰了解析，默默捕获异常，走上方的兜底值
                    System.err.println("底层精准统计解析失败，已回退至默认统计: " + e.getMessage());
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
            executeMessage.setExitValue(-1);
            executeMessage.setMessage("Docker 线程中断异常: " + e.getMessage());
        }
        return executeMessage;
    }

    private static String[] getCmdByLanguage(String language) {
// 🌟 定义统一个格式化标签，必须和外面解析时保持绝对一致
        String timeFormat = "YU_OJ_STATS_TIME:%e_MEM:%M";

        if (language.equals("java")) {
            // Java 建议加上 -Xmx 和编码参数，防止 JVM 自身把容器撑爆
            return new String[]{
                    "/usr/bin/time", "-f", timeFormat,
                    "java", "-Xmx256m", "-Dfile.encoding=UTF-8", "-cp", "/app", "Main"
            };
        } else if (language.equals("cpp")) {
            return new String[]{
                    "/usr/bin/time", "-f", timeFormat,
                    "/app/main"
            };
        } else if (language.equals("python")) {
            return new String[]{
                    "/usr/bin/time", "-f", timeFormat,
                    "python3", "/app/main.py"
            };
        } else {
            // 遇到不支持的语言直接抛出异常，比返回一个错误的数组更安全
            throw new IllegalArgumentException("暂不支持该语言的执行: " + language);
        }

    }
}


