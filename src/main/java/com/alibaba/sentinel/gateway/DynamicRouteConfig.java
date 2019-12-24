package com.alibaba.sentinel.gateway;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 动态路由配置
 *
 * @author zlt
 * @date 2019/10/7
 * <p>
 * Blog: https://blog.csdn.net/zlt2000
 * Github: https://github.com/zlt2000
 */
@Configuration
@ConditionalOnProperty(prefix = "zlt.gateway.dynamicRoute", name = "enabled", havingValue = "true")
public class DynamicRouteConfig {
    @Autowired
    private ApplicationEventPublisher publisher;

    /**
     * Nacos实现方式
     */
    @Configuration
    @ConditionalOnProperty(prefix = "zlt.gateway.dynamicRoute", name = "dataType", havingValue = "nacos", matchIfMissing = true)
    public class NacosDynRoute {
        @Autowired
        private NacosConfigProperties nacosConfigProperties;

        @Bean
        public NacosRouteDefinitionRepository nacosRouteDefinitionRepository() {

            RouteNacosProperties routeNacosProperties  = routeNacosProperties();

            return new NacosRouteDefinitionRepository(routeNacosProperties, publisher, nacosConfigProperties);
        }

        @Bean
        @ConfigurationProperties(prefix = "zlt.gateway.nacos.rule")
        public RouteNacosProperties routeNacosProperties() {
            return new RouteNacosProperties();
        }
    }

    @Getter
    @Setter
    public static class RouteNacosProperties {
        private String dataId;
        private String groupId;
        private String namespace;
    }
}

