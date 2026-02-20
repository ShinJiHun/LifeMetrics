package com.lifemetrics.backend.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(
    basePackages = "com.lifemetrics.backend",
    excludeFilters = @ComponentScan.Filter(  // ✅ 변경
        type = FilterType.REGEX,
        pattern = "com\\.lifemetrics\\.backend\\.lotto\\..*"
    ),
    entityManagerFactoryRef = "ridingEntityManagerFactory",
    transactionManagerRef = "ridingTransactionManager"
)
public class RidingDataSourceConfig {

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.riding")
    public DataSource ridingDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Primary
    @Bean
    public LocalContainerEntityManagerFactoryBean ridingEntityManagerFactory(
            @Qualifier("ridingDataSource") DataSource dataSource
    ) {
        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("com.lifemetrics.backend");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        return emf;
    }

    @Primary
    @Bean
    public JpaTransactionManager ridingTransactionManager(
            @Qualifier("ridingEntityManagerFactory") EntityManagerFactory emf
    ) {
        return new JpaTransactionManager(emf);
    }
}
