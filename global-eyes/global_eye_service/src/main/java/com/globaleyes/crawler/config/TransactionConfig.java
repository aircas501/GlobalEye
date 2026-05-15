package com.globaleyes.crawler.config;

import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

/**
 * 多数据源事务配置类
 * 配置 JPA (MySQL) 和 Neo4j 的事务管理器
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Configuration
public class TransactionConfig {

    /**
     * JPA 事务管理器 (MySQL)
     * 设置为 Primary，作为默认事务管理器
     *
     * @param entityManagerFactory JPA 实体管理器工厂
     * @return JPA 事务管理器
     */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager jpaTransactionManager = new JpaTransactionManager();
        jpaTransactionManager.setEntityManagerFactory(entityManagerFactory);
        return jpaTransactionManager;
    }

    /**
     * Neo4j 事务管理器
     *
     * @param driver Neo4j 驱动
     * @return Neo4j 事务管理器
     */
    @Bean(name = "neo4jTransactionManager")
    public PlatformTransactionManager neo4jTransactionManager(Driver driver) {
        return new Neo4jTransactionManager(driver);
    }
}
