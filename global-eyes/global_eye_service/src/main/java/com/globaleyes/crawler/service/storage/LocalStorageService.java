package com.globaleyes.crawler.service.storage;

import com.globaleyes.crawler.config.StorageConfig;
import com.globaleyes.crawler.pojo.entity.NewsArticle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地文件存储服务实现
 * 将新闻文章存储为本地txt文件
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Service("localStorageService")
public class LocalStorageService implements NewsStorageService {

    private final StorageConfig.LocalConfig config;
    private final AtomicLong sequence;
    private final DateTimeFormatter timestampFormatter;
    private final DateTimeFormatter dateFormatter;

    /**
     * 构造函数注入配置
     *
     * @param storageConfig 存储配置
     */
    public LocalStorageService(StorageConfig storageConfig) {
        this.config = storageConfig.getLocal();
        this.sequence = new AtomicLong(0);
        this.timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSS");
        this.dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        initStorageDirectory();
    }

    /**
     * 初始化存储目录
     */
    private void initStorageDirectory() {
        Path path = Paths.get(config.getPath());
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("Created storage directory: {}", path.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to create storage directory: {}", e.getMessage());
        }
    }

    @Override
    public boolean save(NewsArticle article) {
        if (article == null) {
            return false;
        }

        try {
            String fileName = generateFileName(article);
            String content = buildFileContent(article);

            Path dateDir = createDateDirectory();
            Path filePath = dateDir.resolve(fileName);

            Files.writeString(filePath, content, StandardCharsets.UTF_8);

            log.debug("Saved article to file: {}", filePath.toAbsolutePath());
            return true;
        } catch (Exception e) {
            log.error("Failed to save article to local file: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 生成文件名
     * 格式: 序号_yyyyMMddHHmmssSSSS_新闻标题_序号.txt
     *
     * @param article 新闻文章
     * @return 文件名
     */
    private String generateFileName(NewsArticle article) {
        long seq = sequence.incrementAndGet();
        String timestamp = LocalDateTime.now().format(timestampFormatter);
        String title = sanitizeFileName(article.getTitle());
        String index = String.valueOf(seq);

        return String.format("%d_%s_%s_%s.txt", seq, timestamp, title, index);
    }

    /**
     * 清理文件名中的非法字符
     *
     * @param fileName 原始文件名
     * @return 清理后的文件名
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "untitled";
        }
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .substring(0, Math.min(fileName.length(), 50));
    }

    /**
     * 创建日期目录
     *
     * @return 日期目录路径
     */
    private Path createDateDirectory() throws IOException {
        String dateStr = LocalDateTime.now().format(dateFormatter);
        Path dateDir = Paths.get(config.getPath(), dateStr);

        if (!Files.exists(dateDir)) {
            Files.createDirectories(dateDir);
        }

        return dateDir;
    }

    /**
     * 构建文件内容
     *
     * @param article 新闻文章
     * @return 文件内容
     */
    private String buildFileContent(NewsArticle article) {
        StringBuilder sb = new StringBuilder();
        sb.append("标题：").append(article.getTitle()).append("\n");
        sb.append("来源：").append(article.getSource()).append("\n");
        sb.append("发布时间：").append(formatDateTime(article.getPublishedAt())).append("\n");
        sb.append("采集时间：").append(formatDateTime(article.getFetchedAt())).append("\n");
        sb.append("可信等级：").append(article.getAuthorityLevel() != null ? article.getAuthorityLevel() : "未知").append("\n");
        sb.append("详细内容：\n").append(article.getOriginalContent() != null ? article.getOriginalContent() : "");

        return sb.toString();
    }

    /**
     * 格式化日期时间
     *
     * @param dateTime 日期时间
     * @return 格式化后的字符串
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "未知";
        }
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Override
    public String getStorageType() {
        return "local";
    }

    @Override
    public boolean isAvailable() {
        Path path = Paths.get(config.getPath());
        return Files.isDirectory(path) && Files.isWritable(path);
    }
}
