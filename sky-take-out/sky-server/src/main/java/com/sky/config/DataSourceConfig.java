package com.sky.config;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 主业务库配置。
 *
 * 项目存在两个数据源：
 * 1. dataSource：PostgreSQL smart_logistics，给 MyBatis 和 @Transactional 使用。
 * 2. timescaleDataSource：TimescaleDB gps，只给 GPS 时序数据 JdbcTemplate 使用。
 */
@Configuration
@Slf4j
public class DataSourceConfig {

    @Bean(name = "dataSource")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.druid")
    public DataSource dataSource() {
        log.info("初始化主业务数据源 smart_logistics...");
        return DruidDataSourceBuilder.create().build();
    }

    @Bean(name = "businessJdbcTemplate")
    @Primary
    public JdbcTemplate businessJdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
