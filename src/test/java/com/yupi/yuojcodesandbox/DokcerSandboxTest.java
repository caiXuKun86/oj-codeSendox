package com.yupi.yuojcodesandbox;

import com.yupi.yuojcodesandbox.model.ExecuteCodeRequest;
import com.yupi.yuojcodesandbox.model.ExecuteCodeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
class DokcerSandboxTest {

    public static final String javaCode = "import java.io.BufferedReader;\n" +
            "import java.io.InputStreamReader;\n" +
            "import java.io.IOException;\n" +
            "import java.util.StringTokenizer;\n" +
            "\n" +
            "public class Main {\n" +
            "    public static void main(String[] args) throws IOException {\n" +
            "        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));\n" +
            "        String line = br.readLine();\n" +
            "        if (line == null) return;\n" +
            "        StringTokenizer st = new StringTokenizer(line);\n" +
            "        if (!st.hasMoreTokens()) return;\n" +
            "        \n" +
            "        int n = Integer.parseInt(st.nextToken());\n" +
            "        long sum = 0;\n" +
            "        for (int i = 0; i < n; i++) {\n" +
            "            if (st.hasMoreTokens()) {\n" +
            "                int x = Integer.parseInt(st.nextToken());\n" +
            "                sum += x / 2;\n" +
            "            }\n" +
            "        }\n" +
            "        System.out.println(sum);\n" +
            "    }\n" +
            "}";

    public static final String cppCode = "#include <iostream>\n" +
            "\n" +
            "using namespace std;\n" +
            "\n" +
            "int main() {\n" +
            "    // 极速输入输出优化\n" +
            "    ios::sync_with_stdio(false);\n" +
            "    cin.tie(0);\n" +
            "    \n" +
            "    int n;\n" +
            "    if (cin >> n) {\n" +
            "        long long sum = 0;\n" +
            "        for (int i = 0; i < n; i++) {\n" +
            "            int x;\n" +
            "            cin >> x;\n" +
            "            sum += x / 2;\n" +
            "        }\n" +
            "        cout << sum << \"\\n\";\n" +
            "    }\n" +
            "    return 0;\n" +
            "}";
    public static final String pythonCode = "import sys\n" +
            "\n" +
            "def main():\n" +
            "    # 🌟 关键修改：用 readline() 替代 read()，遇到回车直接放行，不再死等 EOF！\n" +
            "    input_data = sys.stdin.readline().split()\n" +
            "    if not input_data:\n" +
            "        return\n" +
            "    \n" +
            "    n = int(input_data[0])\n" +
            "    total_sum = 0\n" +
            "    \n" +
            "    limit = min(n + 1, len(input_data))\n" +
            "    for i in range(1, limit):\n" +
            "        total_sum += int(input_data[i]) // 2\n" +
            "        \n" +
            "    print(total_sum)\n" +
            "\n" +
            "if __name__ == '__main__':\n" +
            "    main()";
    @Resource
    private CodeSandboxFactory codeSandboxFactory;

    @Test
    public void testMegaDataVolume() {
        // 1. 指定你要测试的语言: "java" | "cpp" | "python"
        String targetLanguage = "python";
        CodeSandbox codeSandbox = codeSandboxFactory.getSandbox(targetLanguage);

        ExecuteCodeRequest request = new ExecuteCodeRequest();
        request.setLanguage(targetLanguage);

        // 2. 根据语言路由注入对应的优化源码
        if ("java".equals(targetLanguage)) {
            request.setCode(javaCode);
        } else if ("cpp".equals(targetLanguage)) {
            request.setCode(cppCode);
        } else if ("python".equals(targetLanguage)) {
            request.setCode(pythonCode);
        }

        // 百万级别数据，给足安全边界
        request.setTimeLimit(1000L); // 15秒超时限制
        request.setMemoryLimit(256 * 1024 * 1024L); // 256MB 内存限制

        // 3. 🌟 动态构建 10^6 级别的超大测试用例
        System.out.println("正在内存中生成 10^6 级别超大数据流...");
        int n = 10000000; // 100万
        StringBuilder sb = new StringBuilder();

        // 首位是 n
        sb.append(n);

        // 循环追加 100 万个数字 "4"
        for (int i = 0; i < n; i++) {
            sb.append(" ").append(4);
        }
        sb.append("\n"); // 喂入换行符触发 readline/流结束

        List<String> inputList = new ArrayList<>();
        inputList.add(sb.toString()); // 塞入大用例
        request.setInputList(inputList);

        // 释放 StringBuilder 内存，防止影响宿主机
        sb.setLength(0);

        // 4. 发起沙箱评测
        System.out.println("====== 开始发起 " + targetLanguage + " 百万数据压测 ======");
        ExecuteCodeResponse response = codeSandbox.executeCode(request);
        System.out.println("====== 压测执行完毕 ======");
        System.out.println("状态码: " + response.getStatus());
        System.out.println("输出结果: " + response.getOutputList());
        System.out.println("错误/监控日志: " + response.getMessage());

    }




}


