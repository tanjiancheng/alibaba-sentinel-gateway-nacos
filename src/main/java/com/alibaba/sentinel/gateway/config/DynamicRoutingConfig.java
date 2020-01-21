package com.alibaba.sentinel.gateway.config;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.sentinel.gateway.entity.FilterEntity;
import com.alibaba.sentinel.gateway.entity.PredicateEntity;
import com.alibaba.sentinel.gateway.entity.RouteEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;

@Component
public class DynamicRoutingConfig implements ApplicationEventPublisherAware {
    private final Logger logger = LoggerFactory.getLogger(DynamicRoutingConfig.class);

    @Value("${gateway.nacos.routes.data_id}")
    private String dataId = "gateway-routes";
    @Value("${gateway.nacos.routes.group_id}")
    private String groupId = "DEFAULT_GROUP";
    @Value("${spring.cloud.nacos.config.namespace}")
    private String namespace = "feed145f-a45e-4589-b84e-9d1dc1d105e9";
    @Value("${spring.cloud.nacos.server-addr}")
    private String serverAddr = "10.11.83.115:8848";

    @Autowired
    private RouteDefinitionWriter routeDefinitionWriter;

    private ApplicationEventPublisher applicationEventPublisher;

    private List<RouteEntity> oldRouteEntityList;

    @Bean
    public void refreshRouting() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, this.serverAddr);
        properties.put(PropertyKeyConst.NAMESPACE, this.namespace);
        ConfigService configService = NacosFactory.createConfigService(properties);
        String configInfo = configService.getConfig(this.dataId, this.groupId, 5000);
        if (StrUtil.isNotEmpty(configInfo)) {
            List<RouteEntity> list = JSON.parseArray(configInfo).toJavaList(RouteEntity.class);
            for (RouteEntity route : list) {
                update(assembleRouteDefinition(route));
            }
            oldRouteEntityList = list;
            logger.info("加载路由成功");
        }

        configService.addListener(this.dataId, this.groupId, new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                logger.info("接收到更新的路由配置:" + configInfo); //记录收到的路由
                List<RouteEntity> list = JSON.parseArray(configInfo).toJavaList(RouteEntity.class);
                for (RouteEntity route : list) {
                    try {
                        update(assembleRouteDefinition(route));
                    } catch (Exception e) {
                        //配置错误的路由后重新设置旧的路由回去
                        logger.info(route.getId() + " 路由配置错误");
                        for (RouteEntity oldRoute : oldRouteEntityList) {
                            if (route.getId().equals(oldRoute.getId())) {
                                logger.info(route.getId() + " 重新更新路由:" + JSON.toJSONString(oldRoute));
                                update(assembleRouteDefinition(oldRoute));
                            }
                        }
                    }
                }
            }
        });
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }


    /**
     * 路由更新
     *
     * @param routeDefinition
     * @return
     */
    public void update(RouteDefinition routeDefinition) {

        try {
            this.routeDefinitionWriter.delete(Mono.just(routeDefinition.getId()));
            logger.info("路由删除成功");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        Mono t = routeDefinitionWriter.save(Mono.just(routeDefinition));
        try {
            t.subscribe();
            ApplicationEvent event = new RefreshRoutesEvent(this);
            this.applicationEventPublisher.publishEvent(event);
            logger.info("路由更新成功");
        } catch (Exception e) {
            //logger.error(e.getMessage(), e);
            throw e;
            //System.out.println("------------------旧配置:"+oldConfigInfo);
            //logger.info("报错"+ e.getMessage());
            //logger.error(e.getMessage(), e);
        }
    }

    public RouteDefinition assembleRouteDefinition(RouteEntity routeEntity) {

        RouteDefinition definition = new RouteDefinition();

        // ID
        definition.setId(routeEntity.getId());

        // Predicates
        List<PredicateDefinition> pdList = new ArrayList<>();
        for (PredicateEntity predicateEntity : routeEntity.getPredicates()) {
            PredicateDefinition predicateDefinition = new PredicateDefinition();
            predicateDefinition.setArgs(predicateEntity.getArgs());
            predicateDefinition.setName(predicateEntity.getName());
            pdList.add(predicateDefinition);
        }
        definition.setPredicates(pdList);

        // Filters
        List<FilterDefinition> fdList = new ArrayList<>();
        for (FilterEntity filterEntity : routeEntity.getFilters()) {
            FilterDefinition filterDefinition = new FilterDefinition();
            filterDefinition.setArgs(filterEntity.getArgs());
            filterDefinition.setName(filterEntity.getName());
            fdList.add(filterDefinition);
        }
        definition.setFilters(fdList);

        // URI
        URI uri = UriComponentsBuilder.fromUriString(routeEntity.getUri()).build().toUri();
        definition.setUri(uri);

        return definition;
    }
}
