package com.neuralbase.service.impl;

import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.extension.repository.AbstractRepository;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.neuralbase.entity.Kb;
import com.neuralbase.mapper.KbMapper;
import com.neuralbase.service.KbService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class KbServiceImpl extends AbstractRepository<KbMapper, Kb> implements KbService {

    @Autowired
    private KbMapper baseMapper;

    @Override
    public KbMapper getBaseMapper() {
        return baseMapper;
    }

    @Override
    public boolean saveBatch(Collection<Kb> entityList, int batchSize) {
        return executeBatch(entityList, batchSize, (sqlSession, entity) ->
            sqlSession.insert(SqlHelper.getSqlStatement(Kb.class, SqlMethod.INSERT_ONE), entity));
    }

    @Override
    public boolean updateBatchById(Collection<Kb> entityList, int batchSize) {
        return executeBatch(entityList, batchSize, (sqlSession, entity) ->
            sqlSession.update(SqlHelper.getSqlStatement(Kb.class, SqlMethod.UPDATE_BY_ID), entity));
    }

    @Override
    public boolean saveOrUpdateBatch(Collection<Kb> entityList, int batchSize) {
        return SqlHelper.saveOrUpdateBatch(getSqlSessionFactory(), Kb.class, log, entityList, batchSize,
            (sqlSession, entity) -> entity.getId() != null && getBaseMapper().selectById(entity.getId()) != null,
            (sqlSession, entity) -> {
                sqlSession.update(SqlHelper.getSqlStatement(Kb.class, SqlMethod.UPDATE_BY_ID), entity);
                return 0;
            });
    }
}
