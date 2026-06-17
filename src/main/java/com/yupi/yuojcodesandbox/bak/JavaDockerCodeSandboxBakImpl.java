package com.yupi.yuojcodesandbox.bak;

import cn.hutool.core.io.FileUtil;
import cn.hutool.dfa.WordTree;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.yupi.yuojcodesandbox.CodeSandbox;
import com.yupi.yuojcodesandbox.model.ExecuteCodeRequest;
import com.yupi.yuojcodesandbox.model.ExecuteCodeResponse;
import com.yupi.yuojcodesandbox.model.ExecuteMessage;
import com.yupi.yuojcodesandbox.model.OutputInfo;
import com.yupi.yuojcodesandbox.model.enums.JudgeResponseStatusEnum;
import com.yupi.yuojcodesandbox.utils.ProcessUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
@Deprecated
public class JavaDockerCodeSandboxBakImpl implements CodeSandbox {
    private final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    // 1. 初始化黑名单字典树 (建议作为类的静态成员，只加载一次)
    private static final WordTree WORD_TREE;


    static {
        WORD_TREE = new WordTree();
        List<String> blackList = Arrays.asList(
                "Files", "File", "FileInputStream", "FileOutputStream",
                "RandomAccessFile", "FileReader", "FileWriter",
                "Runtime", "ProcessBuilder", "exec",
                "Socket", "ServerSocket", "URL", "HttpURLConnection",
                "java.lang.reflect", "Method", "invoke", "ClassLoader",
                "System.exit", "System.setProperty"
        );
        for (String word : blackList) {
            WORD_TREE.addWord(word);
        }
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        ExecuteCodeResponse response = new ExecuteCodeResponse();
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();

        String language = executeCodeRequest.getLanguage();
        response.setLanguage(language);

        //得到用户输入文件路径
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        DockerClient dockerClient = getClient();
        String containerId = null;

        try {
            //判断用户上传是否具有敏感词
            String foundWord = WORD_TREE.match(code);
            if (foundWord != null) {
                // 假设你有一个代表“包含违禁代码”的枚举
                response.setStatus(JudgeResponseStatusEnum.DANGEROUS_CODE.getValue());
                response.setMessage("代码中包含违禁操作或危险类库：" + foundWord);
                return response;
            }
            ExecuteMessage compileExecuteMessage = null;
            //获取Javac绝对路径
            String javaHome = System.getProperty("java.home");
            String javacPath = javaHome + File.separator + "bin" + File.separator + "javac";
            // 如果 java.home 指向的是 jre 目录，则需要往上走一层到 JDK 根目录
            if (!FileUtil.exist(javacPath)) {
                javacPath = new File(javaHome).getParent() + File.separator + "bin" + File.separator + "javac";
            }

            // 使用动态获取到的绝对路径来执行编译
            String compileCmd = String.format("%s -encoding utf-8 %s", javacPath, userCodeFile.getAbsolutePath());
            Process compileProcess = null;
            try {
                compileProcess = Runtime.getRuntime().exec(compileCmd);
                compileExecuteMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");

            } catch (IOException e) {
                e.printStackTrace();
                return getErrorResponse(e, response);
            }

            if (compileExecuteMessage.getExitValue() != 0) {
                response.setStatus(JudgeResponseStatusEnum.COMPILE_ERROR.getValue());
                response.setMessage(compileExecuteMessage.getMessage());
                return response;
            }

//            //拉取镜像
//            if (!FIRST_INIT) {
//                try {
//                    dockerClient.pullImageCmd("docker.m.daocloud.io/bellsoft/liberica-openjdk-alpine:8")
//                            .start()
//                            .awaitCompletion(); // 阻塞等待拉取完成
//                    System.out.println("镜像拉取完成！");
//                    FIRST_INIT = true;
//                } catch (InterruptedException e) {
//                    System.out.println("拉取镜像失败");
//                    throw new RuntimeException(e);
//                }
//            }
            //创建并启动容器,并把用户文件挂载到容器中
            HostConfig hostConfig = new HostConfig();
            hostConfig.withMemory(128 * 1024 * 1024L);
            hostConfig.withCpuCount(1L);
            hostConfig.withMemorySwap(0L);
            hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
            hostConfig.withPidsLimit(50L);
            hostConfig.withSecurityOpts(Arrays.asList("no-new-privileges"));
            CreateContainerResponse container = dockerClient.createContainerCmd("docker.m.daocloud.io/bellsoft/liberica-openjdk-alpine:8")
                    .withNetworkDisabled(true)
                    .withReadonlyRootfs(true)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withCmd("/bin/sh", "-c", "tail -f /dev/null")
                    .withHostConfig(hostConfig)
                    .exec();

            containerId = container.getId();
            dockerClient.startContainerCmd(containerId).exec();

            //执行代码
            List<OutputInfo> outputInfoList = new ArrayList<>(inputList.size());

            for (String inputArgs : inputList) {
                OutputInfo outputInfo = new OutputInfo();
                try {
                    ExecuteMessage executeMessage = ProcessUtils.executeCommandInContainer(dockerClient, containerId, inputArgs,2000L,language);
                    outputInfo.setOutput(executeMessage.getOutput());
                    outputInfo.setMessage(executeMessage.getMessage());
                    outputInfo.setTime(executeMessage.getTime());
                    outputInfo.setMemory(executeMessage.getMemory());
                    outputInfoList.add(outputInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                    return getErrorResponse(e, response);
                }
            }
            response.setOutputList(outputInfoList);
            response.setStatus(JudgeResponseStatusEnum.OK.getValue());
            return response;
        } finally {
            if (userCodeFile.getParentFile() != null) {
                boolean del = FileUtil.del(userCodeParentPath);
                System.out.println("删除" + (del ? "成功" : "失败"));
            }
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            System.out.println(response.getStatus());
            System.out.println(response.getLanguage());
            System.out.println(response.getMessage());
            for (OutputInfo outputInfo : response.getOutputList()) {
                System.out.println(outputInfo);
            }
        }
    }

    private ExecuteCodeResponse getErrorResponse(Throwable e, ExecuteCodeResponse response) {
        response.setMessage(e.getMessage());
        response.setStatus(JudgeResponseStatusEnum.SYSTEM_ERROR.getValue());
        return response;
    }

    public DockerClient getClient() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
        return DockerClientImpl.getInstance(config, httpClient);

    }


}
