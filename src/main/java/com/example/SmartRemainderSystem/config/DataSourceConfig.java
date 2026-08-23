package com.example.SmartRemainderSystem.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Connection;

@Configuration
@Slf4j
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String mysqlUrl;

    @Value("${spring.datasource.username}")
    private String mysqlUsername;

    @Value("${spring.datasource.password}")
    private String mysqlPassword;

    @Value("${spring.datasource.driver-class-name}")
    private String mysqlDriver;

    @Bean
    @Primary
    public DataSource dataSource() {
        log.info("Attempting to connect to MySQL database at: {}", mysqlUrl);

        HikariConfig mysqlConfig = new HikariConfig();
        mysqlConfig.setJdbcUrl(mysqlUrl);
        mysqlConfig.setUsername(mysqlUsername);
        mysqlConfig.setPassword(mysqlPassword);
        mysqlConfig.setDriverClassName(mysqlDriver);
        
        // Fast fail and timeout configuration
        mysqlConfig.setInitializationFailTimeout(2000);
        mysqlConfig.setConnectionTimeout(2000);
        mysqlConfig.setMaximumPoolSize(5);

        try {
            HikariDataSource mysqlDataSource = new HikariDataSource(mysqlConfig);
            // Verify connection immediately
            try (Connection conn = mysqlDataSource.getConnection()) {
                log.info("✅ Successfully connected to MySQL database! Primary data store active.");
                return mysqlDataSource;
            }
        } catch (Exception e) {
            log.warn("⚠️ MySQL connection failed: {}. Falling back to in-memory H2 database.", e.getMessage());
            
            HikariConfig h2Config = new HikariConfig();
            h2Config.setJdbcUrl("jdbc:h2:mem:remaindersys;DB_CLOSE_DELAY=-1;MODE=MySQL");
            h2Config.setUsername("sa");
            h2Config.setPassword("");
            h2Config.setDriverClassName("org.h2.Driver");
            h2Config.setConnectionTimeout(2000);
            h2Config.setMaximumPoolSize(5);

            log.info("ℹ️ Bootstrapped transient in-memory H2 database (jdbc:h2:mem:remaindersys).");
            return new HikariDataSource(h2Config);
        }
    }
}
