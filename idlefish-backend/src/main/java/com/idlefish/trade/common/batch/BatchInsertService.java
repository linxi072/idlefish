package com.idlefish.trade.common.batch;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 批量插入设施：在 BATCH 执行器会话中循环调用 {@code BaseMapper.insert}，将 N 条 insert 折叠为
 * 批量 JDBC 预编译语句，消除循环内逐条 insert 的写放大（技术债务 D2/D3/D4）。
 *
 * <p>复用 {@link BaseMapper#insert} 既有链路，因此 {@code IdType.ASSIGN_ID} 雪花主键与
 * {@code @TableField(fill=INSERT/INSERT_UPDATE)} 时间戳填充等行为与原逐条 insert 完全一致，
 * 不会因引入批量机制而丢失主键/审计字段。</p>
 *
 * <p>事务边界：若存在 Spring 活动事务，则通过 {@link SqlHelper#sqlSessionBatch(Class)} 加入该事务
 * （由 Spring 统一提交/回滚，本类不再显式 commit/rollback）；否则开启独立的 BATCH 会话并自行
 * commit，保证非事务调用场景下数据可见。异常时仅在非事务路径回滚，避免误伤外层事务。</p>
 */
@Component
public class BatchInsertService {

    private static final int DEFAULT_BATCH_SIZE = 500;

    private final SqlSessionFactory sqlSessionFactory;

    public BatchInsertService(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    public <T> int batchInsert(Class<T> entityClass,
                              Class<? extends BaseMapper<T>> mapperClass,
                              List<T> entities) {
        return batchInsert(entityClass, mapperClass, entities, DEFAULT_BATCH_SIZE);
    }

    public <T> int batchInsert(Class<T> entityClass,
                              Class<? extends BaseMapper<T>> mapperClass,
                              List<T> entities,
                              int batchSize) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        if (batchSize <= 0) {
            batchSize = DEFAULT_BATCH_SIZE;
        }
        boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();
        SqlSession session = txActive
                ? SqlHelper.sqlSessionBatch(entityClass)
                : sqlSessionFactory.openSession(ExecutorType.BATCH, false);
        try {
            BaseMapper<T> mapper = session.getMapper(mapperClass);
            int count = 0;
            for (T entity : entities) {
                mapper.insert(entity);
                if (++count % batchSize == 0) {
                    session.flushStatements();
                }
            }
            session.flushStatements();
            if (!txActive) {
                session.commit();
            }
            return count;
        } catch (RuntimeException ex) {
            if (!txActive) {
                session.rollback();
            }
            throw ex;
        } finally {
            session.close();
        }
    }
}
