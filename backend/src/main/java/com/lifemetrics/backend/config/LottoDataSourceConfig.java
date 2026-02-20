package com.lifemetrics.backend.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(
    basePackages = "com.lifemetrics.backend.lotto",
    entityManagerFactoryRef = "lottoEntityManagerFactory",
    transactionManagerRef = "lottoTransactionManager"
)
public class LottoDataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.lotto")
    public DataSource lottoDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean lottoEntityManagerFactory(
            @Qualifier("lottoDataSource") DataSource dataSource
    ) {
        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("com.lifemetrics.backend.lotto");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        return emf;
    }

    @Bean
    public JpaTransactionManager lottoTransactionManager(
            @Qualifier("lottoEntityManagerFactory") EntityManagerFactory emf
    ) {
        return new JpaTransactionManager(emf);
    }
}
