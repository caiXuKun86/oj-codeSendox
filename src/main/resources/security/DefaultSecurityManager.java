import java.security.Permission;

// 加上这个注解，告诉编译器：“我知道它过时了，别给我报警告了”
@SuppressWarnings("removal")
public class DefaultSecurityManager extends SecurityManager {

    @Override
    public void checkPermission(Permission perm) {
        // 默认放行所有权限
        // super.checkPermission(perm);
    }

    @Override
    public void checkExec(String cmd) {
        throw new SecurityException("沙箱保护：禁止执行系统命令！");
    }

    @Override
    public void checkWrite(String file) {
        throw new SecurityException("沙箱保护：禁止写文件！");
    }

    @Override
    public void checkConnect(String host, int port) {
        throw new SecurityException("沙箱保护：禁止建立网络连接！");
    }
}