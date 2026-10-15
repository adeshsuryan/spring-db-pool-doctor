package io.github.springdbpooldoctor.autoconfigure;

import com.zaxxer.hikari.HikariDataSource;
import io.github.springdbpooldoctor.core.PoolAnalyzer;
import io.github.springdbpooldoctor.core.PoolMonitorScheduler;
import io.github.springdbpooldoctor.hikari.HikariPoolMonitor;
import io.github.springdbpooldoctor.instrumented.DoctorDataSourcePostProcessor;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnClass(HikariDataSource.class)
@ConditionalOnProperty(prefix = "spring.db-pool-doctor", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SpringDbPoolDoctorProperties.class)
public class SpringDbPoolDoctorAutoConfiguration {

    /**
     * Static so the post-processor is registered early enough to wrap
     * DataSource beans regardless of autoconfiguration ordering.
     */
    @Bean
    public static BeanPostProcessor springDbPoolDoctorDataSourcePostProcessor(Environment env) {
        boolean instrumented = env.getProperty("spring.db-pool-doctor.instrumented", Boolean.class, false);
        double sampleRate = env.getProperty("spring.db-pool-doctor.stack-sample-rate", Double.class, 0.1);
        return new DoctorDataSourcePostProcessor(instrumented, sampleRate);
    }

    @Bean
    @ConditionalOnMissingBean
    PoolAnalyzer springDbPoolDoctorAnalyzer(SpringDbPoolDoctorProperties properties) {
        return new PoolAnalyzer(properties);
    }

    @Bean
    @ConditionalOnSingleCandidate(DataSource.class)
    HikariPoolMonitor springDbPoolDoctorMonitor(DataSource dataSource, PoolAnalyzer analyzer,
                                              SpringDbPoolDoctorProperties properties) {
        return new HikariPoolMonitor(dataSource, analyzer, properties);
    }

    @Bean(destroyMethod = "close")
    PoolMonitorScheduler springDbPoolDoctorScheduler(HikariPoolMonitor monitor, SpringDbPoolDoctorProperties properties) {
        PoolMonitorScheduler scheduler = new PoolMonitorScheduler(monitor::scan, properties.getScanIntervalMs());
        scheduler.start();
        return scheduler;
    }
}
