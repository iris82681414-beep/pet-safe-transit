package com.sky.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * TimescaleDB 时序库数据源配置（第二数据源）
 *
 * 主数据源（smart_logistics 业务库）由 DataSourceConfig + Druid 装配，且带有 @Primary。
 * 这里仅配置 TimescaleDB（gps 库），通过 @Qualifier("timescaleDataSource") 区分。
 */
@Configuration
@Slf4j
public class TimescaleDBConfig {

    @Value("${timescaledb.datasource.jdbc-url}")
    private String jdbcUrl;

    @Value("${timescaledb.datasource.username}")
    private String username;

    @Value("${timescaledb.datasource.password}")
    private String password;

    @Value("${timescaledb.datasource.driver-class-name}")
    private String driverClassName;

    /**
     * TimescaleDB 数据源
     */
    @Bean(name = "timescaleDataSource")
    public DataSource timescaleDataSource() {
        log.info("初始化 TimescaleDB 数据源: {}", jdbcUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(300000);

        return new HikariDataSource(config);
    }

    /**
     * TimescaleDB 专用 JdbcTemplate
     */
    @Bean(name = "timescaleJdbcTemplate")
    public JdbcTemplate timescaleJdbcTemplate(
            @Qualifier("timescaleDataSource") DataSource dataSource) {
        log.info("初始化 TimescaleDB JdbcTemplate...");
        return new JdbcTemplate(dataSource);
    }
}
