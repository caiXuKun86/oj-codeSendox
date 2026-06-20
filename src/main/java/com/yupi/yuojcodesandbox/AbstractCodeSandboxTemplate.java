package com.yupi.yuojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.dfa.WordTree;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.yupi.yuojcodesandbox.model.ExecuteCodeRequest;
import com.yupi.yuojcodesandbox.model.ExecuteCodeResponse;
import com.yupi.yuojcodesandbox.model.ExecuteMessage;
import com.yupi.yuojcodesandbox.model.OutputInfo;
import com.yupi.yuojcodesandbox.model.enums.JudgeResponseStatusEnum;
import com.yupi.yuojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
public abstract class AbstractCodeSandboxTemplate implements CodeSandbox {
    //获取违禁词字典树
    protected abstract WordTree getWordTree();

    // 获取用户上传code文件名字
    public abstract String getFileName();

    public abstract ExecuteMessage compileCode(File userCodeFile) throws IOException;

    public abstract String getImageName();

    @Resource
    private DockerClient dockerClient;

    //检查用户代码有无违禁词
    public String checkCodeBeforeCompile(String code) {
        WordTree wordTree = getWordTree();
        if (wordTree == null) {
            return null; // 如果不需要校验，子类可以返回 null
        }
        return wordTree.match(code);
    }

    //保存用户上传code到临时目录
    public Map<String, Object> saveFile(String code) {
        String globalPath = "/root/app/tmp/code";
        if (!FileUtil.exist(globalPath)) {
            FileUtil.mkdir(globalPath);
        }
        String userCodeParentPath = globalPath + File.separator + UUID.randomUUID();
        String userPath = userCodeParentPath + File.separator + getFileName();
        File userCodeFile = FileUtil.writeString(code, userPath, StandardCharsets.UTF_8);
        Map<String, Object> map = new HashMap<>();
        map.put("userCodeParentPath", userCodeParentPath);
        map.put("userCodeFile", userCodeFile);
        return map;
    }

    //执行代码
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        ExecuteCodeResponse response = new ExecuteCodeResponse();
        Long timeLimit = executeCodeRequest.getTimeLimit();
        Long memoryLimit = executeCodeRequest.getMemoryLimit();
        String inputParentPath = executeCodeRequest.getInputParentPath();
        String userOutputParentPath = executeCodeRequest.getUserOutputParentPath();
        Integer count = executeCodeRequest.getCount();
        String code = executeCodeRequest.getCode();
        // timeLimit和memoryLimit以cpp为基准,py和java代码自行加时间
        String language = executeCodeRequest.getLanguage();
        response.setLanguage(language);
        if (language.equals("java")) {
            timeLimit <<= 1;
            memoryLimit <<= 1;
        } else if (language.equals("python")) {
            timeLimit <<= 2;
            memoryLimit <<= 2;
        }
        String containerId = null;
        String userCodeParentPath = null;
        try {
            //1.保存代码文件
            Map<String, Object> map = saveFile(code);
            File userCodeFile = (File) map.get("userCodeFile");
            userCodeParentPath = map.get("userCodeParentPath").toString();
            //2.检测代码是否含有违禁词
            String foundWord = checkCodeBeforeCompile(code);
            if (foundWord != null) {
                log.error("代码中包含违禁操作或危险类库：" + foundWord);
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
                log.error("编译过程出错{}", e.getMessage());
                return getErrorResponse(e, response);
            }
            if (compileExecuteMessage.getExitValue() != 0) {
                log.error("编译失败{}", compileExecuteMessage.getMessage());
                response.setStatus(JudgeResponseStatusEnum.COMPILE_ERROR.getValue());
                response.setMessage(compileExecuteMessage.getMessage());
                return response;
            }
            //4.代码上传的虚拟机
            HostConfig hostConfig = new HostConfig();
            //最大内存
            hostConfig.withMemory(memoryLimit);
            //CPU核心数
            hostConfig.withCpuCount(1L);
            //禁止内存交换
            hostConfig.withMemorySwap(memoryLimit);
            hostConfig.withTmpFs(Collections.singletonMap("/tmp", "rw,noexec,nosuid,size=65536k"));
            hostConfig.setBinds(
                    //挂载用户code
                    new Bind(userCodeParentPath, new Volume("/app/code"), AccessMode.ro),
                    // 挂载输入用例
                    new Bind(inputParentPath, new Volume("/app/input"), AccessMode.ro),
                    // 挂载用户代码输出
                    new Bind(userOutputParentPath, new Volume("/app/output"), AccessMode.rw)
            );
            //最多50个线程
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
            dockerClient.startContainerCmd(containerId).exec();
            //5.执行代码
            List<OutputInfo> outputInfoList = new ArrayList<>(count);
            for (int i = 1; i <= count; i++) {
                // 构造当前测试点的输入文件对象
                String containerInputPath = "/app/input/" + i + ".in";
                ExecuteMessage executeMessage = null;
                try {
                    executeMessage = ProcessUtils.executeCommandInContainer(dockerClient, containerId, containerInputPath, userOutputParentPath, timeLimit, language, i);
                } catch (Exception e) {
                    log.error("代码执行发生非预期崩溃：" + e.getMessage());
                    e.printStackTrace();
                    return getErrorResponse(e, response);
                }
                OutputInfo outputInfo = new OutputInfo();
                outputInfo.setMessage(executeMessage.getMessage());
                outputInfo.setTime(executeMessage.getTime());
                outputInfo.setMemory(executeMessage.getMemory());
                outputInfoList.add(outputInfo);
            }
            response.setStatus(JudgeResponseStatusEnum.OK.getValue());
            response.setOutputInfoList(outputInfoList);
            return response;
        } finally {
            //6.释放资源
            //删除容器
            if (containerId != null) {
                try {
                    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                } catch (Exception e) {
                    log.error("强制删除 Docker 容器失败，containerId: {}, 原因: {}", containerId, e.getMessage());
                }
            }
            //删除code文件
            if (userCodeParentPath != null) {
                boolean del = FileUtil.del(userCodeParentPath);
                if (!del) {
                    log.error("清理本地用户代码目录失败，可能存在权限问题或残留占用，路径: {}", userCodeParentPath);
                } else {
                    log.info("成功清理本地用户代码目录: {}", userCodeParentPath);
                }
            }
            System.out.println(response);
        }
    }

    private ExecuteCodeResponse getErrorResponse(Throwable e, ExecuteCodeResponse response) {
        response.setMessage(e.getMessage());
        response.setStatus(JudgeResponseStatusEnum.SYSTEM_ERROR.getValue());
        return response;
    }
}
