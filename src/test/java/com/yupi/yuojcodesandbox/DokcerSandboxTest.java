package com.yupi.yuojcodesandbox;

import com.yupi.yuojcodesandbox.model.ExecuteCodeRequest;
import com.yupi.yuojcodesandbox.model.ExecuteCodeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.io.File;

@SpringBootTest
class DokcerSandboxTest {

    // Java 极速版排序代码 (使用 StreamTokenizer 优化 IO)
    public static final String javaCode = "import java.io.*;\n" +
            "import java.util.*;\n" +
            "\n" +
            "public class Main {\n" +
            "    public static void main(String[] args) throws IOException {\n" +
            "        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));\n" +
            "        StreamTokenizer st = new StreamTokenizer(br);\n" +
            "        if (st.nextToken() == StreamTokenizer.TT_EOF) return;\n" +
            "        int n = (int) st.nval;\n" +
            "        int[] arr = new int[n];\n" +
            "        for (int i = 0; i < n; i++) {\n" +
            "            st.nextToken();\n" +
            "            arr[i] = (int) st.nval;\n" +
            "        }\n" +
            "        Arrays.sort(arr);\n" +
            "        StringBuilder sb = new StringBuilder();\n" +
            "        for (int i = 0; i < n; i++) {\n" +
            "            sb.append(arr[i]);\n" +
            "            if (i < n - 1) sb.append(\" \");\n" +
            "        }\n" +
            "        System.out.println(sb.toString());\n" +
            "    }\n" +
            "}";

    // C++ 极速版排序代码
    public static final String cppCode = "#include <iostream>\n" +
            "#include <vector>\n" +
            "#include <algorithm>\n" +
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
            "        vector<int> data(n);\n" +
            "        for (int i = 0; i < n; i++) {\n" +
            "            cin >> data[i];\n" +
            "        }\n" +
            "        sort(data.begin(), data.end());\n" +
            "        for (int i = 0; i < n; i++) {\n" +
            "            cout << data[i];\n" +
            "            if (i < n - 1) cout << \" \";\n" +
            "        }\n" +
            "        cout << \"\\n\";\n" +
            "    }\n" +
            "    return 0;\n" +
            "}";

    // Python 排序代码
    public static final String pythonCode = "import sys\n" +
            "\n" +
            "def main():\n" +
            "    # 使用 read().split() 可以完美兼容多行输入和单行超长输入\n" +
            "    input_data = sys.stdin.read().split()\n" +
            "    if not input_data:\n" +
            "        return\n" +
            "    \n" +
            "    n = int(input_data[0])\n" +
            "    # 截取后面的 n 个元素并转换为 int\n" +
            "    arr = [int(x) for x in input_data[1:n+1]]\n" +
            "    arr.sort()\n" +
            "    \n" +
            "    # 拼接输出\n" +
            "    print(\" \".join(map(str, arr)))\n" +
            "\n" +
            "if __name__ == '__main__':\n" +
            "    main()";

    @Resource
    private CodeSandboxFactory codeSandboxFactory;

    @Test
    public void testMegaDataVolume() {
        // 1. 指定你要测试的语言: "java" | "cpp" | "python"
        String targetLanguage = "cpp";
        CodeSandbox codeSandbox = codeSandboxFactory.getSandbox(targetLanguage);

        String targetCode = "cpp";
        if ("java".equals(targetLanguage)) {
            targetCode = javaCode;
        } else if ("cpp".equals(targetLanguage)) {
            targetCode = cppCode;
        } else if ("python".equals(targetLanguage)) {
            targetCode = pythonCode;
        }

        String inputParentPath = "/root/app/tmp/in/2068152766221721602";
        String userOutputParentPath = "/root/app/tmp/userOut";



        File outDir = new File(userOutputParentPath);
        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        // 3. 构造请求参数
        ExecuteCodeRequest request = ExecuteCodeRequest.builder()
                .code(targetCode)
                .language(targetLanguage)
                .inputParentPath(inputParentPath)
                .userOutputParentPath(userOutputParentPath)
                .count(5)
                .timeLimit(1000L)
                .memoryLimit(256 * 1024 * 1024L)
                .build();

        // 4. 发起沙箱评测
        System.out.println("====== 开始发起 " + targetLanguage + " 排序压测 ======");
        ExecuteCodeResponse response = codeSandbox.executeCode(request);
        System.out.println("====== 压测执行完毕 ======");
        System.out.println("状态码: " + response.getStatus());
        System.out.println("错误/监控日志: " + response.getMessage());


    }


}