package com.globaleyes.crawler.pojo.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * B站视频实体类
 * 对应数据库表 bilibili_video
 *
 * @author Crawler Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bilibili_video", indexes = {
    @Index(name = "uk_bvid", columnList = "bvid", unique = true)
})
public class BilibiliVideo {

    /**
     * 自增主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 内容类型，固定为video
     */
    @Column(length = 20)
    private String type;

    /**
     * UP主昵称
     */
    @Column(length = 100)
    private String author;

    /**
     * UP主唯一ID
     */
    private Long mid;

    /**
     * 视频分区ID
     */
    @Column(length = 10)
    private String typeid;

    /**
     * 视频分区名称
     */
    @Column(length = 50)
    private String typename;

    /**
     * 视频播放链接
     */
    @Column(length = 255)
    private String arcurl;

    /**
     * 视频AV号（全局唯一）
     */
    @Column(nullable = false)
    private Long aid;

    /**
     * 视频BV号（全局唯一，用于去重）
     */
    @Column(length = 50, nullable = false, unique = true)
    private String bvid;

    /**
     * 视频标题
     */
    @Column(length = 500)
    private String title;

    /**
     * 视频简介
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 视频封面图链接
     */
    @Column(length = 255)
    private String pic;

    /**
     * 播放量
     */
    private Integer play;

    /**
     * 视频评论数（冗余字段）
     */
    @Column(name = "video_review")
    private Integer videoReview;

    /**
     * 收藏数
     */
    private Integer favorites;

    /**
     * 视频标签
     */
    @Column(length = 500)
    private String tag;

    /**
     * 评论数
     */
    private Integer review;

    /**
     * 发布时间戳（秒）
     */
    private Long pubdate;

    /**
     * 提交发布时间戳
     */
    private Long senddate;

    /**
     * 视频时长 格式：分:秒
     */
    @Column(length = 20)
    private String duration;

    /**
     * UP主头像链接
     */
    @Column(length = 255)
    private String upic;

    /**
     * 数据入库时间
     */
    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;
}
