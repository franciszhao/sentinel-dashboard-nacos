/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.dashboard.controller.v2;

import java.security.InvalidParameterException;
import java.util.Date;
import java.util.List;

import com.alibaba.csp.sentinel.dashboard.auth.AuthAction;
import com.alibaba.csp.sentinel.dashboard.auth.AuthService;
import com.alibaba.csp.sentinel.dashboard.auth.AuthService.PrivilegeType;
import com.alibaba.csp.sentinel.dashboard.controller.BaseSyncDataController;
import com.alibaba.csp.sentinel.util.StringUtil;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.FlowRuleEntity;
import com.alibaba.csp.sentinel.dashboard.repository.rule.InMemoryRuleRepositoryAdapter;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRuleProvider;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRulePublisher;
import com.alibaba.csp.sentinel.dashboard.domain.Result;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Flow rule controller (v2).
 *
 * @author Eric Zhao
 * @since 1.4.0
 */
@RestController
@RequestMapping(value = "/v2/flow")
public class FlowControllerV2 extends BaseSyncDataController<FlowRuleEntity> {

    private final Logger logger = LoggerFactory.getLogger(FlowControllerV2.class);

//    @Autowired
//    private InMemoryRuleRepositoryAdapter<FlowRuleEntity> repository;

    @Autowired
    @Qualifier("flowRuleNacosProvider")
    private DynamicRuleProvider<List<FlowRuleEntity>> ruleProvider;
    @Autowired
    @Qualifier("flowRuleNacosPublisher")
    private DynamicRulePublisher<List<FlowRuleEntity>> rulePublisher;

    @GetMapping("/rules")
    @AuthAction(PrivilegeType.READ_RULE)
    public Result<List<FlowRuleEntity>> apiQueryMachineRules(@RequestParam String app) {

        if (StringUtil.isEmpty(app)) {
            return Result.ofFail(-1, "app can't be null or empty");
        }
        try {
//            List<FlowRuleEntity> rules = this.getAll(app);
//            if (rules != null && !rules.isEmpty()) {
//                for (FlowRuleEntity entity : rules) {
//                    entity.setApp(app);
//                    if (entity.getClusterConfig() != null && entity.getClusterConfig().getFlowId() != null) {
//                        entity.setId(entity.getClusterConfig().getFlowId());
//                    }
//                }
//            }
//            rules = repository.saveAll(rules);
            List<FlowRuleEntity> rules = this.list(ruleProvider, app);
            return Result.ofSuccess(rules);
        } catch (Throwable throwable) {
            logger.error("Error when querying flow rules", throwable);
            return Result.ofThrowable(-1, throwable);
        }
    }

    private <R> Result<R> checkEntityInternal(FlowRuleEntity entity) {
        if (entity == null) {
            return Result.ofFail(-1, "invalid body");
        }
        if (StringUtil.isBlank(entity.getApp())) {
            return Result.ofFail(-1, "app can't be null or empty");
        }
        if (StringUtil.isBlank(entity.getLimitApp())) {
            return Result.ofFail(-1, "limitApp can't be null or empty");
        }
        if (StringUtil.isBlank(entity.getResource())) {
            return Result.ofFail(-1, "resource can't be null or empty");
        }
        if (entity.getGrade() == null) {
            return Result.ofFail(-1, "grade can't be null");
        }
        if (entity.getGrade() != 0 && entity.getGrade() != 1) {
            return Result.ofFail(-1, "grade must be 0 or 1, but " + entity.getGrade() + " got");
        }
        if (entity.getCount() == null || entity.getCount() < 0) {
            return Result.ofFail(-1, "count should be at lease zero");
        }
        if (entity.getStrategy() == null) {
            return Result.ofFail(-1, "strategy can't be null");
        }
        if (entity.getStrategy() != 0 && StringUtil.isBlank(entity.getRefResource())) {
            return Result.ofFail(-1, "refResource can't be null or empty when strategy!=0");
        }
        if (entity.getControlBehavior() == null) {
            return Result.ofFail(-1, "controlBehavior can't be null");
        }
        int controlBehavior = entity.getControlBehavior();
        if (controlBehavior == 1 && entity.getWarmUpPeriodSec() == null) {
            return Result.ofFail(-1, "warmUpPeriodSec can't be null when controlBehavior==1");
        }
        if (controlBehavior == 2 && entity.getMaxQueueingTimeMs() == null) {
            return Result.ofFail(-1, "maxQueueingTimeMs can't be null when controlBehavior==2");
        }
        if (entity.isClusterMode() && entity.getClusterConfig() == null) {
            return Result.ofFail(-1, "cluster config should be valid");
        }
        return null;
    }

    @PostMapping("/rule")
    @AuthAction(value = AuthService.PrivilegeType.WRITE_RULE)
    public Result<FlowRuleEntity> apiAddFlowRule(@RequestBody FlowRuleEntity entity) {

        Result<FlowRuleEntity> checkResult = checkEntityInternal(entity);
        if (checkResult != null) {
            return checkResult;
        }

//        entity.setId(this.getNextId(entity.getApp()));
        Date date = new Date();
        entity.setGmtCreate(date);
        entity.setGmtModified(date);
//        entity.setLimitApp(entity.getLimitApp().trim());
//        entity.setResource(entity.getResource().trim());
        try {
            this.save(rulePublisher, ruleProvider, entity);
//            entity = repository.save(entity);
//            publishRules(entity.getApp());
        } catch (Throwable throwable) {
            logger.error("Failed to add flow rule", throwable);
            return Result.ofThrowable(-1, throwable);
        }
        return Result.ofSuccess(entity);
    }

    @PutMapping("/rule/{id}")
    @AuthAction(AuthService.PrivilegeType.WRITE_RULE)

    public Result<FlowRuleEntity> apiUpdateFlowRule(@PathVariable("id") Long id,
                                                    @RequestBody FlowRuleEntity entity) {
        if (id == null || id <= 0) {
            return Result.ofFail(-1, "Invalid id");
        }
        FlowRuleEntity oldEntity = this.findById(ruleProvider, entity.getApp(), id);
        if (oldEntity == null) {
            return Result.ofFail(-1, "id " + id + " does not exist");
        }
        if (entity == null) {
            return Result.ofFail(-1, "invalid body");
        }

//        entity.setApp(oldEntity.getApp());
//        entity.setIp(oldEntity.getIp());
//        entity.setPort(oldEntity.getPort());
//        Result<FlowRuleEntity> checkResult = checkEntityInternal(entity);
//        if (checkResult != null) {
//            return checkResult;
//        }
//
        entity.setId(id);
//        Date date = new Date();
//        entity.setGmtCreate(null);
//        entity.setGmtModified(date);
        try {
            this.update(rulePublisher, ruleProvider, entity);
//            entity = repository.save(entity);
//            if (entity == null) {
//                return Result.ofFail(-1, "save entity fail");
//            }
//            publishRules(oldEntity.getApp());
        } catch (Throwable throwable) {
            logger.error("Failed to update flow rule", throwable);
            return Result.ofThrowable(-1, throwable);
        }
        return Result.ofSuccess(entity);
    }

    @DeleteMapping("/rule/{id}")
    @AuthAction(PrivilegeType.DELETE_RULE)
    public Result<Long> apiDeleteRule(@PathVariable("id") Long id, @RequestParam("app") String app) {
        if (id == null || id <= 0) {
            return Result.ofFail(-1, "Invalid id");
        }
        if (StringUtils.isEmpty(app)) {
            return Result.ofFail(-1, "Invalid app");
        }
//        FlowRuleEntity oldEntity = repository.findById(id);
//        if (oldEntity == null) {
//            return Result.ofSuccess(null);
//        }

        try {
//            repository.delete(id);
//            publishRules(oldEntity.getApp());
            this.delete(rulePublisher, ruleProvider, id, app);
        } catch (Exception e) {
            return Result.ofFail(-1, e.getMessage());
        }
        return Result.ofSuccess(id);
    }

//    private void publishRules(/*@NonNull*/ String app) throws Exception {
//        List<FlowRuleEntity> rules = repository.findAllByApp(app);
//        rulePublisher.publish(app, rules);
//    }

//    private List<FlowRuleEntity> list(String app) throws Exception {
//        List<FlowRuleEntity> rules = ruleProvider.getRules(app);
//        if (rules != null && !rules.isEmpty()) {
//            for (FlowRuleEntity entity : rules) {
//                entity.setApp(app);
//                if (entity.getClusterConfig() != null && entity.getClusterConfig().getFlowId() != null) {
//                    entity.setId(entity.getClusterConfig().getFlowId());
//                }
//            }
//
//            Collections.sort(rules, new Comparator<FlowRuleEntity>() {
//                @Override
//                public int compare(FlowRuleEntity o1, FlowRuleEntity o2) {
//                    return (int) (o1.getId() - o2.getId());
//                }
//            });
//        } else {
//            rules = Collections.EMPTY_LIST;
//        }
//        return rules;
//    }
//
//    private void save(DynamicRulePublisher<List<FlowRuleEntity>> rulePublisher, FlowRuleEntity newFlowRule, String app) throws Exception {
//        if (null != newFlowRule.getId()) {
//            throw new InvalidParameterException("id must be null");
//        }
//        List<FlowRuleEntity> rules = this.list(app);
//
//        long nextId = 1;
//        if (rules.size() > 0) {
//            nextId = rules.get(rules.size() - 1).getId() + 1;
//        }
//        newFlowRule.setId(nextId);
//        rules.add(newFlowRule);
//        rulePublisher.publish(app, rules);
//    }
//
//    private Result update(DynamicRulePublisher<List<FlowRuleEntity>> rulePublisher, DynamicRuleProvider<List<FlowRuleEntity>> ruleProvider, FlowRuleEntity entity, String app) throws Exception {
//        if (null == entity || null == entity.getId()) {
//            throw new InvalidParameterException("id is required");
//        }
//        List<FlowRuleEntity> rules = ruleProvider.getRules(app);
//        if (null == rules || rules.isEmpty()) {
//            return null;
//        }
//
//        for (int i = 0; i < rules.size(); i++) {
//            FlowRuleEntity oldEntity = rules.get(i);
//            if (oldEntity.getId().equals(entity.getId())) {
//                entity.setApp(oldEntity.getApp());
//                entity.setIp(oldEntity.getIp());
//                entity.setPort(oldEntity.getPort());
//                Result<FlowRuleEntity> checkResult = checkEntityInternal(entity);
//                if (checkResult != null) {
//                    return checkResult;
//                }
//
////                entity.setId(id);
//                Date date = new Date();
//                entity.setGmtCreate(oldEntity.getGmtCreate());
//                entity.setGmtModified(date);
//
//                rules.set(i, entity);
//                break;
//            }
//        }
//
//        rulePublisher.publish(app, rules);
//        return null;
//    }
//
//    private void delete(DynamicRulePublisher<List<FlowRuleEntity>> rulePublisher, DynamicRuleProvider<List<FlowRuleEntity>> ruleProvider, long id, String app) throws Exception {
//        List<FlowRuleEntity> rules = ruleProvider.getRules(app);
//        if (null == rules || rules.isEmpty()) {
//            return;
//        }
//
//        Iterator<FlowRuleEntity> ruleIterator = rules.iterator();
//        while (ruleIterator.hasNext()) {
//            FlowRuleEntity flowRuleEntity = ruleIterator.next();
//            if (flowRuleEntity.getId().equals(id)) {
//                ruleIterator.remove();
//            }
//        }
//        rulePublisher.publish(app, rules);
//    }


    @Override
    protected void format(FlowRuleEntity entity, String app) {
        entity.setApp(app);
        if (entity.getClusterConfig() != null && entity.getClusterConfig().getFlowId() != null) {
            entity.setId(entity.getClusterConfig().getFlowId());
        }
    }

    @Override
    protected void merge(FlowRuleEntity target, FlowRuleEntity oldEntity) {
        target.setApp(oldEntity.getApp());
        target.setIp(oldEntity.getIp());
        target.setPort(oldEntity.getPort());
        Result<FlowRuleEntity> checkResult = checkEntityInternal(target);
        if (checkResult != null) {
            throw new InvalidParameterException(checkResult.getMsg());
        }

//                entity.setId(id);
        Date date = new Date();
        target.setGmtCreate(oldEntity.getGmtCreate());
        target.setGmtModified(date);
    }
}
