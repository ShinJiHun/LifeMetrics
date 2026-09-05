package com.lifemetrics.backend.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

// RidingDataSourceConfig / JournalDataSourceConfig 와 동일한 DataSourceProperties 기반 패턴.
// (DataSourceBuilder.create().build() 직접 바인딩 방식은 부트스트랩 시 Hikari 커넥션이
//  완전히 초기화되기 전에 Hibernate가 메타데이터 조회를 시도하면서 경고가 나던 문제가 있어 통일함.)
@ConditionalOnProperty(name = "lotto.datasource.enabled", havingValue = "true")
@EnableJpaRepositories(
        basePackages = "com.lifemetrics.backend.lotto",
        entityManagerFactoryRef = "lottoEntityManagerFactory",
        transactionManagerRef = "lottoTransactionManager"
)
@Configuration
public class LottoDataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.lotto")
    public DataSourceProperties lottoDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource lottoDataSource(@Qualifier("lottoDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean lottoEntityManagerFactory(
            @Qualifier("lottoDataSource") DataSource dataSource
    ) {
        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("com.lifemetrics.backend.lotto");
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
    public JpaTransactionManager lottoTransactionManager(
            @Qualifier("lottoEntityManagerFactory") EntityManagerFactory emf
    ) {
        return new JpaTransactionManager(emf);
    }
}
