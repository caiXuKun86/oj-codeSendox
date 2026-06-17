package com.yupi.yuojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.dfa.WordTree;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.yupi.yuojcodesandbox.model.ExecuteCodeRequest;
import com.yupi.yuojcodesandbox.model.ExecuteCodeResponse;
import com.yupi.yuojcodesandbox.model.ExecuteMessage;
import com.yupi.yuojcodesandbox.model.OutputInfo;
import com.yupi.yuojcodesandbox.model.enums.JudgeResponseStatusEnum;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class AbstractCodeSandboxTemplate implements CodeSandbox {


    // 定义上下文封装类
    public static class SandboxContext {
        public Long timeLimit;
        public DockerClient dockerClient;
        public String containerId;
        public String language;
    }

    // 声明 ThreadLocal
    protected static final ThreadLocal<SandboxContext> CONTEXT_HOLDER = new ThreadLocal<>();

    public abstract String getFileName();

    protected abstract WordTree getWordTree();

    @Resource
    private DockerClient dockerClient;
    public Map<String, Object> saveFile(String code) {
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + "tmpCode";
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + getFileName();
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        Map<String, Object> map = new HashMap<>();
        map.put("userCodeParentPath", userCodeParentPath);
        map.put("userCodeFile", userCodeFile);
        return map;
    }

    public String checkCodeBeforeCompile(String code) {
        WordTree wordTree = getWordTree();
        if (wordTree == null) {
            return null; // 如果不需要校验，子类可以返回 null
        }
        return wordTree.match(code);
    }

    public abstract ExecuteMessage compileCode(File userCodeFile) throws IOException;

    public abstract ExecuteMessage executeCode(String inputArgs);
    public abstract String getImageName();

    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        ExecuteCodeResponse response = new ExecuteCodeResponse();
        Long timeLimit = executeCodeRequest.getTimeLimit();
        Long memoryLimit = executeCodeRequest.getMemoryLimit();

        String language = executeCodeRequest.getLanguage();
        response.setLanguage(language);
        if (language.equals("java")) {
            timeLimit <<= 1;
            memoryLimit <<= 1;
        } else if (language.equals("python")) {
            timeLimit <<= 3;
            memoryLimit <<= 3;
        }
        String containerId = null;
        SandboxContext context = new SandboxContext();
        context.timeLimit = timeLimit;
        context.dockerClient = dockerClient;
        context.language = language;

        CONTEXT_HOLDER.set(context);
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();


        //1.保存代码文件
        Map<String, Object> map = saveFile(code);
        File userCodeFile = (File) map.get("userCodeFile");
        String userCodeParentPath = map.get("userCodeParentPath").toString();

        try {
            //2.检测代码是否含有违禁词
            String foundWord = checkCodeBeforeCompile(code);
            if (foundWord != null) {
                // 假设你有一个代表“包含违禁代码”的枚举
                response.setStatus(JudgeResponseStatusEnum.DANGEROUS_CODE.getValue());
                response.setMessage("代码中包含违禁操作或危险类库：" + foundWord);
                return response;
            }
            // 3.编译代码
            ExecuteMessage compileExecuteMessage = null;
            try {
                compileExecuteMessage = compileCode(userCodeFile);
            } catch (IOException e) {
                e.printStackTrace();
                return getErrorResponse(e, response);
            }
            if (compileExecuteMessage.getExitValue() != 0) {
                response.setStatus(JudgeResponseStatusEnum.COMPILE_ERROR.getValue());
                response.setMessage(compileExecuteMessage.getMessage());
                return response;
            }
            //4.代码上传的虚拟机
            //创建并启动容器,并把用户文件挂载到容器中
            HostConfig hostConfig = new HostConfig();
            hostConfig.withMemory(memoryLimit);
            hostConfig.withCpuCount(1L);
            hostConfig.withMemorySwap(0L);
            hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
            hostConfig.withPidsLimit(50L);
            hostConfig.withSecurityOpts(Arrays.asList("no-new-privileges"));
            CreateContainerResponse container = dockerClient.createContainerCmd(getImageName())
                    .withNetworkDisabled(true)
                    .withReadonlyRootfs(true)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withCmd("/bin/sh", "-c", "tail -f /dev/null")
                    .withHostConfig(hostConfig)
                    .exec();

            containerId = container.getId();
            context.containerId = containerId;

            dockerClient.startContainerCmd(containerId).exec();

            //4.执行代码
            List<OutputInfo> outputInfoList = new ArrayList<>(inputList.size());

            for (String inputArgs : inputList) {
                ExecuteMessage executeMessage = null;
                try {
                    executeMessage = executeCode(inputArgs);
                } catch (Exception e) {
                    e.printStackTrace();
                    return getErrorResponse(e, response);
                }
                OutputInfo outputInfo = new OutputInfo();
                outputInfo.setOutput(executeMessage.getOutput());
                outputInfo.setMessage(executeMessage.getMessage());
                outputInfo.setTime(executeMessage.getTime());
                outputInfo.setMemory(executeMessage.getMemory());
                outputInfoList.add(outputInfo);
            }
            response.setOutputList(outputInfoList);
            response.setStatus(JudgeResponseStatusEnum.OK.getValue());

            return response;
        } finally {
            if (userCodeFile.getParentFile() != null) {
                boolean del = FileUtil.del(userCodeParentPath);
            }
            CONTEXT_HOLDER.remove();
            if (containerId != null) {
                dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            }
        }
    }


    private ExecuteCodeResponse getErrorResponse(Throwable e, ExecuteCodeResponse response) {
        response.setMessage(e.getMessage());
        response.setStatus(JudgeResponseStatusEnum.SYSTEM_ERROR.getValue());
        return response;
    }



}
