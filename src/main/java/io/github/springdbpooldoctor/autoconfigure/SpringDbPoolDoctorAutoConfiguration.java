package io.github.springdbpooldoctor.autoconfigure;

import com.zaxxer.hikari.HikariDataSource;
import io.github.springdbpooldoctor.core.PoolAnalyzer;
import io.github.springdbpooldoctor.core.PoolMonitorScheduler;
import io.github.springdbpooldoctor.hikari.HikariPoolMonitor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(HikariDataSource.class)
@ConditionalOnProperty(prefix = "spring.db-pool-doctor", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SpringDbPoolDoctorProperties.class)
public class SpringDbPoolDoctorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    PoolAnalyzer springDbPoolDoctorAnalyzer(SpringDbPoolDoctorProperties properties) {
        return new PoolAnalyzer(properties);
    }

    @Bean
    @ConditionalOnSingleCandidate(HikariDataSource.class)
    HikariPoolMonitor springDbPoolDoctorHikariMonitor(HikariDataSource dataSource, PoolAnalyzer analyzer) {
        return new HikariPoolMonitor(dataSource, analyzer);
    }

    @Bean(destroyMethod = "close")
    PoolMonitorScheduler springDbPoolDoctorScheduler(HikariPoolMonitor monitor, SpringDbPoolDoctorProperties properties) {
        PoolMonitorScheduler scheduler = new PoolMonitorScheduler(monitor::scan, properties.getScanIntervalMs());
        scheduler.start();
        return scheduler;
    }
}
