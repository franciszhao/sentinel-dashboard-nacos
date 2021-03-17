package com.alibaba.csp.sentinel.dashboard.rule.nacos;

import com.alibaba.csp.sentinel.concurrent.NamedThreadFactory;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.RuleEntity;
import com.alibaba.csp.sentinel.dashboard.util.JSONUtils;
import com.alibaba.csp.sentinel.slots.block.Rule;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @wiki https://github.com/eacdy/Sentinel-Dashboard-Nacos
 * add by tam
 */
@Component
public class NacosConfigUtil {
    @Autowired
    private NacosConfig.NacosProperties nacosProperties;

    public static final String FLOW_DATA_ID_POSTFIX = "-flow-rules";
    public static final String DEGRADE_DATA_ID_POSTFIX = "-degrade-rules";
    public static final String SYSTEM_DATA_ID_POSTFIX = "-system-rules";
    public static final String PARAM_FLOW_DATA_ID_POSTFIX = "-param-flow-rules";
    public static final String AUTHORITY_DATA_ID_POSTFIX = "-authority-rules";
    public static final String GATEWAY_FLOW_DATA_ID_POSTFIX = "-gateway-flow-rules";
    public static final String GATEWAY_API_DATA_ID_POSTFIX = "-gateway-api-rules";
    public static final String DASHBOARD_POSTFIX = "-dashboard";
    public static final String CLUSTER_MAP_DATA_ID_POSTFIX = "-cluster-map";

    /**
     * cc for `cluster-client`
     */
    public static final String CLIENT_CONFIG_DATA_ID_POSTFIX = "-cc-config";
    /**
     * cs for `cluster-server`
     */
    public static final String SERVER_TRANSPORT_CONFIG_DATA_ID_POSTFIX = "-cs-transport-config";
    public static final String SERVER_FLOW_CONFIG_DATA_ID_POSTFIX = "-cs-flow-config";
    public static final String SERVER_NAMESPACE_SET_DATA_ID_POSTFIX = "-cs-namespace-set";

    private final ExecutorService pool = new ThreadPoolExecutor(1, 1, 0L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue(1), new NamedThreadFactory("sentinel-dashboard-nacos-ds-update"), new ThreadPoolExecutor.DiscardOldestPolicy());

    /**
     * 将规则序列化成JSON文本，存储到Nacos server中
     *
     * @param configService nacos config service
     * @param app           应用名称
     * @param postfix       规则后缀 eg.NacosConfigUtil.FLOW_DATA_ID_POSTFIX
     * @param rules         规则对象
     * @throws NacosException 异常
     */
    public <T> void setRuleStringToNacos(ConfigService configService, String app, String postfix, List<T> rules) throws NacosException {
        AssertUtil.notEmpty(app, "app name cannot be empty");
        if (rules == null) {
            return;
        }

        List<Rule> ruleForApp = rules.stream()
                .map(rule -> {
                    RuleEntity rule1 = (RuleEntity) rule;
                    //System.out.println(rule1.getClass());
                    Rule rule2 = rule1.toRule();
                    //System.out.println(rule2.getClass());
                    return rule2;
                })
                .collect(Collectors.toList());

        String dataId = genDataId(app, postfix);
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Listener listener = new Listener() {
            @Override
            public Executor getExecutor() {
                return NacosConfigUtil.this.pool;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                countDownLatch.countDown();
            }
        };
        configService.addListener(dataId + DASHBOARD_POSTFIX, nacosProperties.getGroupId(), listener);
//        // 存储，给微服务使用
//        configService.publishConfig(
//                dataId,
//                nacosProperties.getGroupId(),
////                JSON.toJSONString(ruleForApp)
//                printPrettyJSON(ruleForApp)
//        );

        // 存储，给控制台使用
        configService.publishConfig(
                dataId + DASHBOARD_POSTFIX,
                nacosProperties.getGroupId(),
//                JSONUtils.toJSONString(rules)
                printPrettyJSON(rules)
        );

        try {
            countDownLatch.await(2000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        configService.removeListener(dataId + DASHBOARD_POSTFIX, nacosProperties.getGroupId(), listener);

    }

    /**
     * 从Nacos server中查询响应规则，并将其反序列化成对应Rule实体
     *
     * @param configService nacos config service
     * @param appName       应用名称
     * @param postfix       规则后缀 eg.NacosConfigUtil.FLOW_DATA_ID_POSTFIX
     * @param clazz         类
     * @param <T>           泛型
     * @return 规则对象列表
     * @throws NacosException 异常
     */
    public <T> List<T> getRuleEntitiesFromNacos(ConfigService configService, String appName, String postfix, Class<T> clazz) throws NacosException {
        String rules = configService.getConfig(
                genDataId(appName, postfix) + DASHBOARD_POSTFIX,
                //genDataId(appName, postfix),
                nacosProperties.getGroupId(),
                3000
        );
        if (StringUtil.isEmpty(rules)) {
            return new ArrayList<>();
        }
        return JSONUtils.parseObject(clazz, rules);
    }

    private static String genDataId(String appName, String postfix) {
        return appName + postfix;
    }

    private static String printPrettyJSON(Object obj) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return JSON.toJSONString(obj);
        }
    }
}
