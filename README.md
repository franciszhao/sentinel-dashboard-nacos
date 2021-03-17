# sentinel-dashboard-nacos
Use nacos as sentinel dashboard's datasource.

基于[Sentinel](https://github.com/alibaba/Sentinel) Dashboard 1.8.0版本修改而来。

### 问题
Sentinel Dashboard官方版本中支持创建DynamicRuleProvider和DynamicRulePublisher来和外部数据源通信，但是仅仅增加这两个类的实现并不够，使用下来发现逻辑上有一些问题：
1. 新建RuleEntity的时候，ID是从代码中的AtomicLong变量获取的，每次这个变量都是从0开始计数，也就意味着每次重启之后ID都重新计数，
   
   这在使用内存存储rule的时候没有问题，但是一旦有外部数据源，这地方逻辑就不对了。
2. 新建RuleEntity之后，会将当前所有的RuleEntity发布到外部数据源
   
   如果是从资源列表页直接创建规则，那么这时候还没从外部数据源加载已存在的rule，当前rule创建完成之后发布到外部数据源的时候，只会把刚创建的这个发布出去，导致之前存在的rule被覆盖掉。
3. 在各个Controller的list方法中，在从外部加载rule之后，会调用repository的saveAll方法（就是InMemoryRuleRepositoryAdapter的saveAll方法），在该方法中会清除所有的rule
   
   这相当于内存中同时只能有一个app的rule存在。

### 改动点
1. 不再使用InMemoryRuleRepositoryAdapter的各个实现类作为repository，仅使用外部数据源。
2. 增加NacosConfig和NacosConfigUtil，作为和Nacos通信的基础类，感谢[tanjiancheng/alibaba-sentinel-dashboard-nacos](https://github.com/tanjiancheng/alibaba-sentinel-dashboard-nacos)。
3. 增加以下类，用于各类Rule和外部数据源交互。
    - GatewayApiRuleNacosProvider
    - GatewayFlowRuleNacosProvider
    - FlowRuleNacosProvider
    - AuthorityRuleNacosProvider
    - DegradeRuleNacosProvider
    - ParamFlowRuleNacosProvider
    - SystemRuleNacosProvider
    - GatewayApiRuleNacosPublisher
    - GatewayFlowRuleNacosPublisher
    - FlowRuleNacosPublisher
    - AuthorityRuleNacosPublisher
    - DegradeRuleNacosPublisher
    - ParamFlowRuleNacosPublisher
    - SystemRuleNacosPublisher. 
4. 增加了BaseSyncDataController类，该类提供了findById\list\save\update\delete方法用于和外部数据源交互，提供了format方法，用于格式化从外部数据源获取到的数据，提供了merge方法用于在update时做数据整合。
5. 修改以下类，这些类继承BaseSyncDataController类，不再使用原有的repository，同时修改注入的DynamicRuleProvider和DynamicRulePublisher实现。
    - GatewayApiController
    - GatewayFlowRuleController
    - FlowControllerV2
    - AuthorityRuleController
    - DegradeController
    - ParamFlowRuleController
    - SystemController

### 新增参数

Nacos相关
spring.cloud.sentinel.datasource.nacos.server-addr=http://localhost:8848
spring.cloud.sentinel.datasource.nacos.groupId=sentinel
spring.cloud.sentinel.datasource.nacos.namespace=sentinel

server.port=${server.listen.port:8088}


sentinel.api.io.thread.count=4
sentinel.metric.fetch.io.thread.count=6



### 优化
1. 调整了SentinelApiClient和MetricFetcher的线程数，使用默认配置的时候在我本地MBP风扇声音挺大的。
2. 提供给client端使用的规则格式，和dashboard使用的规则格式略有不同，在tanjiancheng/alibaba-sentinel-dashboard-nacos的实现中这两端的数据分别存储到不同的dataId，但是考虑到数据的一致性，我这里将数据存储成了一份，client在使用的时候，需要自定义解析器。

详见代码NacosConfigUtil：

```
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
```

client端解析器代码如下：

```

import com.alibaba.cloud.sentinel.datasource.converter.SentinelConverter;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;

/**
 * 自定义SentinelRule反序列化逻辑
 * @param <T>
 */
public class CustomizeSentinelConverter<T> extends SentinelConverter {
    public static final Logger log = LoggerFactory.getLogger(CustomizeSentinelConverter.class);
    private ObjectMapper objectMapper = new ObjectMapper();
    private Class<T> ruleClass;


    public CustomizeSentinelConverter(ObjectMapper objectMapper, Class<T> ruleClass) {
        super(objectMapper, ruleClass);
        this.ruleClass = ruleClass;

        if (null != objectMapper) {
            this.objectMapper = objectMapper;
        }
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
    }

    @Override
    public Collection<Object> convert(String source) {
        Collection<Object> ruleCollection;

        // hard code
        if (ruleClass == FlowRule.class || ruleClass == DegradeRule.class
                || ruleClass == SystemRule.class || ruleClass == AuthorityRule.class
                || ruleClass == ParamFlowRule.class) {
            ruleCollection = new ArrayList<>();
        } else {
            ruleCollection = new HashSet<>();
        }

        if (StringUtils.isEmpty(source)) {
            log.warn("converter can not convert rules because source is empty");
            return ruleCollection;
        }

        try {
            List<HashMap> sourceArray = objectMapper.readValue(source,
                    new TypeReference<List<HashMap>>() {
                    });

            for (HashMap obj : sourceArray) {
                String item = null;
                try {
//                    item = objectMapper.writeValueAsString(obj);
                    item = this.getValue(obj);
                    Optional.ofNullable(convertRule(item))
                            .ifPresent(convertRule -> ruleCollection.add(convertRule));
                }
                catch (IOException e) {
                    log.error("sentinel rule convert error: " + e.getMessage(), e);
                    throw new IllegalArgumentException(
                            "sentinel rule convert error: " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            else {
                throw new RuntimeException("convert error: " + e.getMessage(), e);
            }
        }
        return ruleCollection;
    }

    private String getValue(HashMap obj) throws IOException {
        if (ruleClass == AuthorityRule.class || ruleClass == ParamFlowRule.class) {
            return objectMapper.writeValueAsString(obj.get("rule"));
        } else {
            return objectMapper.writeValueAsString(obj);
        }
    }

    private Object convertRule(String ruleStr) throws IOException {
        return objectMapper.readValue(ruleStr, ruleClass);
    }
}
```
