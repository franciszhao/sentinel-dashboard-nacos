package com.alibaba.csp.sentinel.dashboard.controller;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.RuleEntity;
import com.alibaba.csp.sentinel.dashboard.domain.Result;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRuleProvider;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRulePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.security.InvalidParameterException;
import java.util.*;

public abstract class BaseSyncDataController<T extends RuleEntity> {
    private final Logger logger = LoggerFactory.getLogger(BaseSyncDataController.class);

    protected abstract void format(T entity, String app);
    protected abstract void merge(T entity, T oldEntity);

    protected T findById(DynamicRuleProvider<List<T>> ruleProvider, String app, Long id) {
        try {
            List<T> rules = ruleProvider.getRules(app);
            if (rules != null && !rules.isEmpty()) {
                for (T entity : rules) {
                    if (entity.getId().equals(id)) {
                        this.format(entity, app);
                        return entity;
                    }
    //
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    protected List<T> list(DynamicRuleProvider<List<T>> ruleProvider, String app) throws Exception {
        List<T> rules = ruleProvider.getRules(app);
        if (rules != null && !rules.isEmpty()) {
            for (T entity : rules) {
                this.format(entity, app);
//                entity.setApp(app);
//                if (entity.getClusterConfig() != null && entity.getClusterConfig().getFlowId() != null) {
//                    entity.setId(entity.getClusterConfig().getFlowId());
//                }
            }

            Collections.sort(rules, new Comparator<T>() {
                @Override
                public int compare(T o1, T o2) {
                    return (int) (o1.getId() - o2.getId());
                }
            });
        } else {
            rules = new ArrayList<>();
        }
        return rules;
    }

    protected void save(DynamicRulePublisher<List<T>> rulePublisher, DynamicRuleProvider<List<T>> ruleProvider, T entity) throws Exception {
        if (null == entity || StringUtils.isEmpty(entity.getApp())) {
            throw new InvalidParameterException("app is required");
        }
        if (null != entity.getId()) {
            throw new InvalidParameterException("id must be null");
        }
        List<T> rules = this.list(ruleProvider, entity.getApp());

        long nextId = 1;
        if (rules.size() > 0) {
            nextId = rules.get(rules.size() - 1).getId() + 1;
        }
        entity.setId(nextId);
        rules.add(entity);
        rulePublisher.publish(entity.getApp(), rules);
    }

    protected Result update(DynamicRulePublisher<List<T>> rulePublisher, DynamicRuleProvider<List<T>> ruleProvider, T entity) throws Exception {
        if (null == entity || null == entity.getId() || StringUtils.isEmpty(entity.getApp())) {
            throw new InvalidParameterException("id is required");
        }
        List<T> rules = this.list(ruleProvider, entity.getApp());
        if (null == rules || rules.isEmpty()) {
            return null;
        }

        for (int i = 0; i < rules.size(); i++) {
            T oldEntity = rules.get(i);
            if (oldEntity.getId().equals(entity.getId())) {
                this.merge(entity, oldEntity);

                rules.set(i, entity);
                break;
            }
        }

        rulePublisher.publish(entity.getApp(), rules);
        return null;
    }

    protected void delete(DynamicRulePublisher<List<T>> rulePublisher, DynamicRuleProvider<List<T>> ruleProvider, long id, String app) throws Exception {
        List<T> rules = this.list(ruleProvider, app);
        if (null == rules || rules.isEmpty()) {
            return;
        }

        Iterator<T> ruleIterator = rules.iterator();
        while (ruleIterator.hasNext()) {
            T flowRuleEntity = ruleIterator.next();
            if (flowRuleEntity.getId().equals(id)) {
                ruleIterator.remove();
            }
        }
        rulePublisher.publish(app, rules);
    }
}
