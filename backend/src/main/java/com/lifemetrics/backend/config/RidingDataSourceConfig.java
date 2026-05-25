package com.lifemetrics.backend.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
import java.util.Properties;

@Configuration
@EnableJpaRepositories(basePackages = "com.lifemetrics.backend", excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.lifemetrics\\.backend\\.lotto\\..*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.lifemetrics\\.backend\\.persona\\..*")
}, entityManagerFactoryRef = "ridingEntityManagerFactory", transactionManagerRef = "ridingTransactionManager")
public class RidingDataSourceConfig {

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.riding")
    public DataSourceProperties ridingDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean
    public DataSource ridingDataSource(@Qualifier("ridingDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean
    public LocalContainerEntityManagerFactoryBean ridingEntityManagerFactory(@Qualifier("ridingDataSource") DataSource dataSource) {
        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("com.lifemetrics.backend");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        var properties = new Properties();
        properties.setProperty(
                "hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"
        );
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
        emf.setJpaProperties(properties);

        return emf;
    }

    @Primary
    @Bean
    public JpaTransactionManager ridingTransactionManager(@Qualifier("ridingEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
