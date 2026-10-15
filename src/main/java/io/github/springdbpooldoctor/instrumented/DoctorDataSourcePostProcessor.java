package io.github.springdbpooldoctor.instrumented;

import io.github.springdbpooldoctor.core.InstrumentedStats;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Wraps DataSource beans with InstrumentedDataSource when
 * spring.db-pool-doctor.instrumented=true. Registered as a static bean
 * definition so it post-processes DataSource beans created later.
 */
public class DoctorDataSourcePostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DoctorDataSourcePostProcessor.class);

    private final boolean instrumented;
    private final double stackSampleRate;

    public DoctorDataSourcePostProcessor(boolean instrumented, double stackSampleRate) {
        this.instrumented = instrumented;
        this.stackSampleRate = stackSampleRate;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!instrumented) return bean;
        if (!(bean instanceof DataSource) || bean instanceof InstrumentedDataSource) return bean;
        try {
            InstrumentedDataSource wrapped = new InstrumentedDataSource(
                    (DataSource) bean, new InstrumentedStats(stackSampleRate));
            log.info("spring-db-pool-doctor: instrumented DataSource bean '{}'", beanName);
            return wrapped;
        } catch (RuntimeException e) {
            log.warn("spring-db-pool-doctor: failed to instrument DataSource '{}'; using original", beanName, e);
            return bean;
        }
    }
}
