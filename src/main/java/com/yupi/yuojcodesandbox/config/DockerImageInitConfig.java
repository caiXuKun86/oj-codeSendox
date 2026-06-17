package com.yupi.yuojcodesandbox.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class DockerImageInitConfig implements ApplicationRunner {

    // 如果你的 DockerClient 已经交给 Spring 管理，可以直接注入
    // 如果没有，你可以直接 new 或者调用你的 getClient() 方法
    @Resource
    private DockerClient dockerClient;

    public static final String JAVA_IMAGE = "docker.m.daocloud.io/bellsoft/liberica-openjdk-alpine:8";
    public static final String CPP_IMAGE = "n0madic/alpine-gcc:9.2.0";
    public static final String PYTHON_IMAGE = "python:3.9-alpine";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("====== 开始检查判题机 Docker 镜像 ======");
        
        try {
            dockerClient.inspectImageCmd(JAVA_IMAGE).exec();
            System.out.println("检查通过：Java沙箱镜像已存在");
        } catch (NotFoundException e) {
            System.out.println("未找到 Java 沙箱镜像，正在从远端拉取（这可能需要几分钟）...");
            try {
                dockerClient.pullImageCmd(JAVA_IMAGE)
                        .start()
                        .awaitCompletion(); // 阻塞等待直到拉取成功
                System.out.println("Java 沙箱镜像拉取成功！");
            } catch (InterruptedException ex) {
                System.err.println("拉取镜像被中断");
                throw new RuntimeException(ex);
            }
        }
        try {
            dockerClient.inspectImageCmd(CPP_IMAGE).exec();
            System.out.println("检查通过：Gcc沙箱镜像已存在");
        } catch (NotFoundException e) {
            System.out.println("未找到 Gcc 沙箱镜像，正在从远端拉取（这可能需要几分钟）...");
            try {
                dockerClient.pullImageCmd(CPP_IMAGE)
                        .start()
                        .awaitCompletion(); // 阻塞等待直到拉取成功
                System.out.println("Gcc 沙箱镜像拉取成功！");
            } catch (InterruptedException ex) {
                System.err.println("拉取镜像被中断");
                throw new RuntimeException(ex);
            }
        }
        try {
            dockerClient.inspectImageCmd(PYTHON_IMAGE).exec();
            System.out.println("检查通过：Python 沙箱镜像已存在");
        } catch (NotFoundException e) {
            System.out.println("未找到 Python 沙箱镜像，正在从远端拉取（这可能需要几分钟）...");
            try {
                dockerClient.pullImageCmd(CPP_IMAGE)
                        .start()
                        .awaitCompletion(); // 阻塞等待直到拉取成功
                System.out.println("Python 沙箱镜像拉取成功！");
            } catch (InterruptedException ex) {
                System.err.println("拉取镜像被中断");
                throw new RuntimeException(ex);
            }
        }
        
        System.out.println("====== Docker 镜像检查完毕 ======");
    }
}