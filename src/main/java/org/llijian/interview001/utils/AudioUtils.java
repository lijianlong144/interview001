package com.example.lijian.utils;

import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

@Slf4j
public class AudioUtils {

    /**
     * 将音频文件转换为PCM格式
     * @param audioData 原始音频数据
     * @return PCM格式的音频数据
     * @throws IOException 转换失败时抛出
     */
    public static byte[] convertToPCM(byte[] audioData) throws IOException {
        try {
            // 从字节数组创建音频流
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(
                    new ByteArrayInputStream(audioData));

            // 获取音频格式
            AudioFormat sourceFormat = audioInputStream.getFormat();

            // 定义目标PCM格式 (16kHz, 16bit, 单声道)
            AudioFormat targetFormat = new AudioFormat(
                    16000, // 采样率
                    16,     // 采样位数
                    1,      // 声道数
                    true,   // 有符号
                    false   // 大端序
            );

            // 转换格式
            AudioInputStream convertedStream = AudioSystem.getAudioInputStream(
                    targetFormat, audioInputStream);

            // 读取转换后的音频数据
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;

            while ((read = convertedStream.read(buffer, 0, buffer.length)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            convertedStream.close();
            audioInputStream.close();

            return outputStream.toByteArray();

        } catch (UnsupportedAudioFileException e) {
            log.error("不支持的音频格式: {}", e.getMessage(), e);
            throw new IOException("不支持的音频格式", e);
        }
    }

    /**
     * 将音频文件转换为PCM格式
     * @param audioFile 音频文件
     * @return PCM格式的音频数据
     * @throws IOException 转换失败时抛出
     */
    public static byte[] convertFileToPCM(File audioFile) throws IOException {
        try {
            // 从文件创建音频流
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(audioFile);

            // 获取音频格式
            AudioFormat sourceFormat = audioInputStream.getFormat();
            log.info("原始音频格式: {}", sourceFormat);

            // 定义目标PCM格式 (16kHz, 16bit, 单声道)
            AudioFormat targetFormat = new AudioFormat(
                    16000, // 采样率
                    16,     // 采样位数
                    1,      // 声道数
                    true,   // 有符号
                    false   // 大端序
            );

            // 转换格式
            AudioInputStream convertedStream = AudioSystem.getAudioInputStream(
                    targetFormat, audioInputStream);

            // 读取转换后的音频数据
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;

            while ((read = convertedStream.read(buffer, 0, buffer.length)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            convertedStream.close();
            audioInputStream.close();

            return outputStream.toByteArray();

        } catch (UnsupportedAudioFileException e) {
            log.error("不支持的音频格式: {}", e.getMessage(), e);
            throw new IOException("不支持的音频格式", e);
        }
    }

    /**
     * 检查音频文件是否为支持的格式
     * @param file 音频文件
     * @return 是否支持
     */
    public static boolean isSupportedAudioFormat(File file) {
        try {
            AudioSystem.getAudioInputStream(file);
            return true;
        } catch (UnsupportedAudioFileException | IOException e) {
            return false;
        }
    }
}