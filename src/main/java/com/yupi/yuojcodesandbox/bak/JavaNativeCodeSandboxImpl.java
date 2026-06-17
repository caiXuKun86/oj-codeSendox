package com.yupi.yuojcodesandbox.bak;

import cn.hutool.core.io.FileUtil;
import cn.hutool.dfa.WordTree;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Deprecated
public class JavaNativeCodeSandboxImpl implements CodeSandbox {
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


        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        try {
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
            //执行代码
            List<OutputInfo> outputInfoList = new ArrayList<>(inputList.size());
            String securityManagerDir = System.getProperty("user.dir") + File.separator
                    + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "security";
            for (String inputArgs : inputList) {
                String runCmd = String.format(
                        "java -Xmx64m -Dfile.encoding=UTF-8 -Djava.security.manager=DefaultSecurityManager -cp \"%s%s%s\" Main",
                        userCodeParentPath, File.pathSeparator, securityManagerDir
                );
                OutputInfo outputInfo = new OutputInfo();
                try {
                    Process runProcess = Runtime.getRuntime().exec(runCmd);
                    ExecuteMessage executeMessage = ProcessUtils.runInteractProcessAndGetMessage(runProcess, inputArgs);
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
//                System.out.println("删除" + (del ? "成功" : "失败"));
            }
            System.out.println(response.getStatus());
            System.out.println(response.getLanguage());
            System.out.println(response.getMessage());
//            for (OutputInfo outputInfo : response.getOutputList()) {
//                System.out.println(outputInfo);
//            }

        }
    }

    private ExecuteCodeResponse getErrorResponse(Throwable e, ExecuteCodeResponse response) {
        response.setMessage(e.getMessage());
        response.setStatus(JudgeResponseStatusEnum.SYSTEM_ERROR.getValue());
        return response;
    }


}
