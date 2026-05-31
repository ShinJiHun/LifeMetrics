package com.lifemetrics.backend.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.lifemetrics.backend.persona",
        entityManagerFactoryRef = "journalEntityManagerFactory",
        transactionManagerRef = "journalTransactionManager"
)
public class JournalDataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.journal")
    public DataSourceProperties journalDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource journalDataSource(@Qualifier("journalDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean journalEntityManagerFactory(@Qualifier("journalDataSource") DataSource dataSource) {
        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("com.lifemetrics.backend.persona");
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

    @Bean
    public JpaTransactionManager journalTransactionManager(@Qualifier("journalEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}